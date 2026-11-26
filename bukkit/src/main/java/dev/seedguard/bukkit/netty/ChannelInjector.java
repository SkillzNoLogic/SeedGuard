package dev.seedguard.bukkit.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Puts a {@link SeedGuardHandler} into every player connection, with no NMS imports and no
 * ProtocolLib.
 * <ul>
 *   <li>Paper/Purpur: {@code io.papermc.paper.network.ChannelInitializeListenerHolder}.
 *       This is the hook built for exactly this job. It runs after vanilla has set up the
 *       pipeline, before any packet is sent.</li>
 *   <li>Spigot/CraftBukkit: an acceptor on the server's listening channels, as ProtocolLib
 *       does.</li>
 * </ul>
 */
public final class ChannelInjector {
    private static final String ACCEPTOR = "seedguard_acceptor";
    private static final Pattern CONN_TYPE = Pattern.compile(".*(Connection|PacketListener|NetworkManager).*");

    private final Logger log;
    private final Supplier<SeedGuardHandler> factory;
    private final List<Channel> serverChannels = new ArrayList<>();
    private Object paperKey;
    private String mode = "none";

    public ChannelInjector(Logger log, Supplier<SeedGuardHandler> factory) {
        this.log = log;
        this.factory = factory;
    }

    public String mode() {
        return mode;
    }

    /** @return false if no strategy worked; the caller may retry once the server is ticking. */
    public boolean start() {
        if (tryPaper()) {
            mode = "paper-channel-listener";
            return true;
        }
        if (trySpigot()) {
            mode = "server-channel-acceptor";
            return true;
        }
        return false;
    }

    public void stop() {
        try {
            if (paperKey != null) {
                Class<?> holder = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
                Class<?> keyClass = Class.forName("net.kyori.adventure.key.Key");
                holder.getMethod("removeListener", keyClass).invoke(null, paperKey);
            }
        } catch (Throwable ignored) {
        }
        for (Channel ch : serverChannels) {
            ch.eventLoop().execute(() -> {
                if (ch.pipeline().get(ACCEPTOR) != null) ch.pipeline().remove(ACCEPTOR);
            });
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            Channel ch = channelOf(p);
            if (ch != null) {
                ch.eventLoop().execute(() -> {
                    if (ch.pipeline().get(SeedGuardHandler.NAME) != null) ch.pipeline().remove(SeedGuardHandler.NAME);
                });
            }
        }
    }

    // ---- Paper ----------------------------------------------------------------------------

    private boolean tryPaper() {
        try {
            Class<?> holder = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
            Class<?> listener = Class.forName("io.papermc.paper.network.ChannelInitializeListener");
            Class<?> keyClass = Class.forName("net.kyori.adventure.key.Key");
            Object key = keyClass.getMethod("key", String.class, String.class).invoke(null, "seedguard", "handler");
            Object proxy = Proxy.newProxyInstance(ChannelInjector.class.getClassLoader(), new Class<?>[]{listener},
                    (self, method, args) -> switch (method.getName()) {
                        case "afterInitChannel" -> {
                            inject((Channel) args[0]);
                            yield null;
                        }
                        case "hashCode" -> System.identityHashCode(self);
                        case "equals" -> self == args[0];
                        case "toString" -> "SeedGuardChannelListener";
                        default -> null;
                    });
            holder.getMethod("addListener", keyClass, listener).invoke(null, key, proxy);
            paperKey = key;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- Spigot ---------------------------------------------------------------------------

    private boolean trySpigot() {
        try {
            Object craftServer = Bukkit.getServer();
            Object mcServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);
            List<ChannelFuture> futures = findChannelFutures(mcServer);
            if (futures.isEmpty()) return false;
            for (ChannelFuture f : futures) {
                Channel server = f.channel();
                serverChannels.add(server);
                server.pipeline().addFirst(ACCEPTOR, new Acceptor());
            }
            return true;
        } catch (Throwable t) {
            log.fine("Spigot channel injection failed: " + t);
            return false;
        }
    }

    @ChannelHandler.Sharable
    private final class Acceptor extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof Channel child) {
                child.pipeline().addLast(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        inject(ch);
                    }
                });
            }
            super.channelRead(ctx, msg);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ChannelFuture> findChannelFutures(Object mcServer) throws IllegalAccessException {
        for (Class<?> c = mcServer.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive() || f.getType().isArray()) continue;
                if (!f.getType().getName().startsWith("net.minecraft.")) continue;
                try {
                    f.setAccessible(true);
                } catch (RuntimeException e) {
                    continue;
                }
                Object listener = f.get(mcServer);
                if (listener == null) continue;
                for (Field lf : listener.getClass().getDeclaredFields()) {
                    if (!List.class.isAssignableFrom(lf.getType()) || Modifier.isStatic(lf.getModifiers())) continue;
                    lf.setAccessible(true);
                    Object list = lf.get(listener);
                    if (list instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof ChannelFuture) {
                        synchronized (l) {
                            return new ArrayList<>((List<ChannelFuture>) l);
                        }
                    }
                }
            }
        }
        return List.of();
    }

    // ---- common ---------------------------------------------------------------------------

    public void inject(Channel ch) {
        if (ch == null) return;
        if (!ch.eventLoop().inEventLoop()) {
            ch.eventLoop().execute(() -> inject(ch));
            return;
        }
        ChannelPipeline p = ch.pipeline();
        if (p.get(SeedGuardHandler.NAME) != null) return;
        if (p.get("packet_handler") != null) {
            p.addBefore("packet_handler", SeedGuardHandler.NAME, factory.get());
        } else {
            // Pipeline not finished yet (initializer ordering); retry on the next loop pass.
            ch.eventLoop().execute(() -> {
                if (p.get(SeedGuardHandler.NAME) == null && p.get("packet_handler") != null) {
                    p.addBefore("packet_handler", SeedGuardHandler.NAME, factory.get());
                }
            });
        }
    }

    /** The handler on a joined player's channel, injecting it late if needed (e.g. after /reload). */
    public SeedGuardHandler handlerFor(Player player) {
        Channel ch = channelOf(player);
        if (ch == null) return null;
        ChannelHandler h = ch.pipeline().get(SeedGuardHandler.NAME);
        if (h == null && ch.pipeline().get("packet_handler") != null) {
            SeedGuardHandler nh = factory.get();
            try {
                ch.pipeline().addBefore("packet_handler", SeedGuardHandler.NAME, nh);
                return nh;
            } catch (IllegalArgumentException race) {
                h = ch.pipeline().get(SeedGuardHandler.NAME);
            }
        }
        return h instanceof SeedGuardHandler s ? s : null;
    }

    /**
     * Finds the netty channel by walking ServerPlayer → game listener → Connection → Channel.
     * Only fields whose type name looks like a connection are followed, so the walk works
     * under both Spigot and Mojang mappings.
     */
    public static Channel channelOf(Player player) {
        try {
            Method getHandle = player.getClass().getMethod("getHandle");
            return find(getHandle.invoke(player), 0);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Channel find(Object obj, int depth) throws IllegalAccessException {
        if (obj == null || depth > 3) return null;
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Class<?> t = f.getType();
                if (Channel.class.isAssignableFrom(t)) {
                    f.setAccessible(true);
                    Object v = f.get(obj);
                    if (v != null) return (Channel) v;
                } else if (t.getName().startsWith("net.minecraft.") && CONN_TYPE.matcher(t.getSimpleName()).matches()) {
                    f.setAccessible(true);
                    Channel ch = find(f.get(obj), depth + 1);
                    if (ch != null) return ch;
                }
            }
        }
        return null;
    }
}
