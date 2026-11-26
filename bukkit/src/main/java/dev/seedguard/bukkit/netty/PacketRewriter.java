package dev.seedguard.bukkit.netty;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongUnaryOperator;

/**
 * Rewrites packets by reflection without knowing their field names.
 *
 * <p>This one class handles Spigot-mapped 1.20–1.20.4 ({@code PacketPlayOutLogin}) and
 * Mojang-mapped 1.20.5+ ({@code ClientboundLoginPacket}), as well as records and classes.
 * Since 1.20.2 the seed sits inside a nested {@code CommonPlayerSpawnInfo} record, so the
 * rewriter recurses into nested Minecraft records and rebuilds immutable records through
 * their canonical constructor.
 */
public final class PacketRewriter {
    private static final Set<String> SPAWN_PACKETS = Set.of(
            "ClientboundLoginPacket", "ClientboundRespawnPacket",
            "PacketPlayOutLogin", "PacketPlayOutRespawn");
    private static final Set<String> SIGN_UPDATE_PACKETS = Set.of(
            "ServerboundSignUpdatePacket", "PacketPlayInUpdateSign");

    private static final Map<Class<?>, Boolean> SPAWN_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> SIGN_CACHE = new ConcurrentHashMap<>();

    /** First rewrite failure, surfaced in /seedguard status. */
    public static volatile String lastError;

    private PacketRewriter() {}

    public static boolean isSpawnPacket(Class<?> c) {
        return SPAWN_CACHE.computeIfAbsent(c, k -> SPAWN_PACKETS.contains(k.getSimpleName()));
    }

    public static boolean isSignUpdate(Class<?> c) {
        return SIGN_CACHE.computeIfAbsent(c, k -> SIGN_UPDATE_PACKETS.contains(k.getSimpleName()));
    }

    /** The four sign lines: a {@code String[]} field (≤26.2) or a {@code List<String>} field (26.3+). */
    public static String[] signLines(Object packet) {
        for (Class<?> c = packet.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType() != String[].class && !List.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(packet);
                    if (v instanceof String[] arr) return arr;
                    if (v instanceof List<?> list && list.stream().allMatch(e -> e instanceof String)) {
                        return list.toArray(new String[0]);
                    }
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Returns either {@code obj} itself (mutated in place) or a rebuilt copy. */
    public static Object rewriteLongs(Object obj, LongUnaryOperator op) {
        try {
            return rewrite(obj, op, 0);
        } catch (Throwable t) {
            if (lastError == null) lastError = obj.getClass().getName() + ": " + t;
            return obj; // never break the connection over this
        }
    }

    private static Object rewrite(Object obj, LongUnaryOperator op, int depth) throws Exception {
        if (obj == null || depth > 2) return obj;
        Class<?> c = obj.getClass();
        if (c.isRecord()) {
            // Spigot-mapped servers (≤1.20.4) rename record accessors, so getAccessor() can be
            // null. A record's instance fields are its components in declaration order, so read
            // them directly and rebuild through the canonical constructor (same type order).
            Field[] fields = Arrays.stream(c.getDeclaredFields())
                    .filter(f -> !Modifier.isStatic(f.getModifiers())).toArray(Field[]::new);
            Object[] values = new Object[fields.length];
            Class<?>[] types = new Class<?>[fields.length];
            boolean changed = false;
            for (int i = 0; i < fields.length; i++) {
                types[i] = fields[i].getType();
                fields[i].setAccessible(true);
                Object v = fields[i].get(obj);
                if (types[i] == long.class) {
                    long old = (Long) v;
                    long nu = op.applyAsLong(old);
                    if (nu != old) { v = nu; changed = true; }
                } else if (isMinecraft(v)) {
                    Object nv = rewrite(v, op, depth + 1);
                    if (nv != v) { v = nv; changed = true; }
                }
                values[i] = v;
            }
            if (!changed) return obj;
            Constructor<?> ctor = c.getDeclaredConstructor(types);
            ctor.setAccessible(true);
            return ctor.newInstance(values);
        }
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.getType() == long.class) {
                    f.setAccessible(true);
                    long old = f.getLong(obj);
                    long nu = op.applyAsLong(old);
                    if (nu != old) f.setLong(obj, nu);
                } else if (!f.getType().isPrimitive()) {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (isMinecraft(v)) {
                        Object nv = rewrite(v, op, depth + 1);
                        if (nv != v) f.set(obj, nv);
                    }
                }
            }
        }
        return obj;
    }

    private static boolean isMinecraft(Object v) {
        return v != null && v.getClass().getName().startsWith("net.minecraft.") && v.getClass().isRecord();
    }
}
