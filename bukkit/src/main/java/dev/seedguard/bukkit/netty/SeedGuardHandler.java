package dev.seedguard.bukkit.netty;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

import java.util.UUID;
import java.util.function.LongUnaryOperator;

/**
 * Sits just before vanilla's "packet_handler", so it sees decoded packet objects in both
 * directions.
 * <ul>
 *   <li>outbound: login/respawn packets get the hashed seed replaced</li>
 *   <li>inbound: sign updates that answer a pending mod probe are consumed</li>
 * </ul>
 * Every other packet costs one cached class lookup.
 */
public final class SeedGuardHandler extends ChannelDuplexHandler {
    public static final String NAME = "seedguard";

    public interface ProbeSink {
        /** @return true if the packet answered a probe and must be swallowed */
        boolean onSignUpdate(UUID player, String[] lines);
    }

    private final LongUnaryOperator hashedSeedMapper;
    private final ProbeSink probeSink;
    private volatile UUID player;

    public SeedGuardHandler(LongUnaryOperator hashedSeedMapper, ProbeSink probeSink) {
        this.hashedSeedMapper = hashedSeedMapper;
        this.probeSink = probeSink;
    }

    public void bind(UUID player) {
        this.player = player;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (hashedSeedMapper != null && PacketRewriter.isSpawnPacket(msg.getClass())) {
            msg = PacketRewriter.rewriteLongs(msg, hashedSeedMapper);
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        UUID p = player;
        if (p != null && probeSink != null && PacketRewriter.isSignUpdate(msg.getClass())) {
            String[] lines = PacketRewriter.signLines(msg);
            if (lines != null && probeSink.onSignUpdate(p, lines)) {
                return; // probe answer: never reaches the server
            }
        }
        super.channelRead(ctx, msg);
    }
}
