package dev.seedguard.mod;

import dev.seedguard.shared.ModProbe;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-side translation-key probe (see {@link ModProbe}), driven by player ticks. */
public final class ModProbeService {
    private enum Phase { WAITING, OPENED, DONE }

    private static final class State {
        // written on the server thread, read on the netty thread
        volatile Phase phase = Phase.WAITING;
        volatile int sentAt;
        volatile BlockPos pos;
        volatile ModProbe.Probe probe;
    }

    // Keyed by UUID: respawning creates a new ServerPlayer object, and we probe once per session.
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static volatile ModProbe probe;

    private ModProbeService() {}

    private static ModProbe probe() {
        ModProbe p = probe;
        if (p == null) {
            SeedGuardConfig c = SeedGuard.config();
            p = probe = new ModProbe(c.detectionKeys, c.detectionThreshold);
        }
        return p;
    }

    /** ServerPlayer#tick (server thread). */
    public static void tick(ServerPlayer player) {
        SeedGuardConfig cfg = SeedGuard.config();
        if (!cfg.detection) return;
        MinecraftServer server = Compat.server(player);
        if (server == null || !server.isDedicatedServer()) return;

        State st = STATES.computeIfAbsent(player.getUUID(), k -> new State());
        if (st.phase == Phase.DONE) return;
        int now = server.getTickCount();
        if (st.phase == Phase.WAITING && player.tickCount >= Math.max(20, cfg.detectionDelayTicks)) {
            send(player, st, now);
        } else if (st.phase == Phase.OPENED) {
            if (now - st.sentAt == 2) {
                // Closing the editor makes the client send the resolved lines back.
                player.connection.send(new ClientboundContainerClosePacket(player.containerMenu.containerId));
            } else if (now - st.sentAt > cfg.detectionTimeoutTicks) {
                st.phase = Phase.DONE;
                restore(player, st.pos);
                SeedGuard.LOG.info("{} did not answer the mod probe (anti-detection client or lag).", player.getName().getString());
            }
        }
    }

    private static void send(ServerPlayer player, State st, int now) {
        BlockPos base = player.blockPosition();
        BlockPos pos = base.below(2);
        if (player.level().isOutsideBuildHeight(pos)) pos = base.above(3);

        ModProbe.Probe pr = probe().newProbe();
        List<Component> lines = new ArrayList<>(pr.keys().size());
        for (int i = 0; i < pr.keys().size(); i++) {
            lines.add(Component.translatableWithFallback(pr.keys().get(i).key(), pr.nonces().get(i)));
        }
        st.pos = pos;
        st.probe = pr;
        st.sentAt = now;
        st.phase = Phase.OPENED;
        Compat.openFakeSign(player, pos, lines);
    }

    private static void restore(ServerPlayer player, BlockPos pos) {
        if (pos != null) player.connection.send(new ClientboundBlockUpdatePacket(player.level(), pos));
    }

    /** Player disconnected: forget them, so the next session is probed again. */
    public static void onDisconnect(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }

    /**
     * ServerGamePacketListenerImpl#handleSignUpdate. This may run on the netty thread.
     *
     * @return true when the packet answered our probe and must not reach vanilla handling
     */
    public static boolean onSignUpdate(ServerPlayer player, ServerboundSignUpdatePacket packet) {
        State st = STATES.get(player.getUUID());
        if (st == null || st.phase != Phase.OPENED || !Compat.signPos(packet).equals(st.pos)) return false;
        st.phase = Phase.DONE;
        ModProbe.Result r = probe().evaluate(st.probe, Compat.signLines(packet));
        MinecraftServer server = Compat.server(player);
        if (server == null) return true;
        server.execute(() -> {
            restore(player, st.pos);
            if (r.detected()) act(server, player, r);
        });
        return true;
    }

    private static void act(MinecraftServer server, ServerPlayer player, ModProbe.Result r) {
        SeedGuardConfig cfg = SeedGuard.config();
        String name = player.getName().getString();
        SeedGuard.LOG.warn("{} has a seed-cracking client mod (resolved: {})", name, String.join(", ", r.matchedKeys()));
        if (cfg.notifyOps) {
            Component msg = Component.literal("[SeedGuard] ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(name + " was flagged for SeedCrackerX.").withStyle(ChatFormatting.GRAY));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p != player && Compat.isStaff(p)) p.sendSystemMessage(msg);
            }
        }
        if (cfg.detectionKick) {
            player.connection.disconnect(Component.literal(cfg.kickMessage));
        }
    }
}
