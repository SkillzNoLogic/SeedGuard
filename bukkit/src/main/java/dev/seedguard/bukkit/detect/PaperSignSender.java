package dev.seedguard.bukkit.detect;

import dev.seedguard.shared.ModProbe;
import io.netty.channel.Channel;
import io.papermc.paper.math.Position;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Fake sign used by the probe.
 *
 * <p>The block and its translatable text go through the Paper API (Adventure). The editor opens
 * through {@code Player#openVirtualSign} where Paper has it (newer 1.21.x and 26.x). Older Paper
 * has no virtual-sign call ({@code openSign} needs a real placed sign and resends the real
 * block), so there the editor-open packet is built by reflection and written to the player's
 * channel. Mojang names (1.20.5+) are tried first, then Spigot names (1.20–1.20.4).
 *
 * <p>The JVM loads this class only when Paper is detected, so the jar still loads on Spigot.
 */
final class PaperSignSender {
    private static final String PKG = "net.minecraft.network.protocol.game.";
    /** Paper 1.21.x+/26.x: the official API. */
    private static Method openVirtualSign;
    /** Older Paper: build ClientboundOpenSignEditorPacket(BlockPos, boolean | SignTextSlot) ourselves. */
    private static Constructor<?> openCtor;
    private static Object frontArg;
    private static Constructor<?> posCtor;

    static boolean supported() {
        try {
            Player.class.getMethod("sendSignChange", Location.class, List.class);
            Component.class.getMethod("translatable", String.class, String.class);
        } catch (Throwable t) {
            return false;
        }
        try {
            openVirtualSign = Player.class.getMethod("openVirtualSign", Position.class, Side.class);
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Class<?> open = first(PKG + "ClientboundOpenSignEditorPacket", PKG + "PacketPlayOutOpenSignEditor");
            Class<?> pos = first("net.minecraft.core.BlockPos", "net.minecraft.core.BlockPosition");
            posCtor = pos.getConstructor(int.class, int.class, int.class);
            for (Constructor<?> c : open.getDeclaredConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length != 2 || p[0] != pos) continue;
                if (p[1] == boolean.class) {
                    frontArg = true;
                } else if (p[1].isEnum()) {
                    frontArg = enumConstant(p[1], "FRONT");
                } else {
                    continue;
                }
                c.setAccessible(true);
                openCtor = c;
            }
            return openCtor != null && frontArg != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumConstant(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    private static Class<?> first(String... names) throws ClassNotFoundException {
        for (String n : names) {
            try {
                return Class.forName(n);
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new ClassNotFoundException(names[0]);
    }

    /** Step 1: show a client-side sign whose lines are translatable(key, nonce). */
    static void place(Player player, Location at, ModProbe.Probe probe) {
        List<Component> lines = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            lines.add(i < probe.keys().size()
                    ? Component.translatable(probe.keys().get(i).key(), probe.nonces().get(i))
                    : Component.empty());
        }
        player.sendBlockChange(at, Material.OAK_SIGN.createBlockData());
        player.sendSignChange(at, lines);
    }

    /** Step 2 (next tick): open the editor on it. */
    static boolean openEditor(Player player, Channel channel, Location at) {
        try {
            if (openVirtualSign != null) {
                openVirtualSign.invoke(player, Position.block(at), Side.FRONT);
                return true;
            }
            Object pos = posCtor.newInstance(at.getBlockX(), at.getBlockY(), at.getBlockZ());
            channel.writeAndFlush(openCtor.newInstance(pos, frontArg));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
