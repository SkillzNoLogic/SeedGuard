package dev.seedguard.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.SignTextSlot;

import java.util.ArrayList;
import java.util.List;

/** Version-specific calls for Minecraft 26.3+. */
final class Compat {
    private Compat() {}

    static MinecraftServer server(ServerPlayer p) {
        return p.level().getServer();
    }

    static boolean isStaff(ServerPlayer p) {
        MinecraftServer s = server(p);
        return s != null && s.getPlayerList().isOp(p.nameAndId());
    }

    static void openFakeSign(ServerPlayer p, BlockPos pos, List<Component> lines) {
        BlockState state = Blocks.OAK_SIGN.defaultBlockState();
        SignBlockEntity sign = new SignBlockEntity(pos, state);
        sign.setLevel(p.level());
        List<Component> msgs = new ArrayList<>(lines);
        while (msgs.size() < SignText.LINES) msgs.add(Component.empty());
        sign.setText(new SignText(msgs, msgs, DyeColor.BLACK, false), SignTextSlot.FRONT);
        p.connection.send(new ClientboundBlockUpdatePacket(pos, state));
        p.connection.send(ClientboundBlockEntityDataPacket.create(sign));
        p.connection.send(new ClientboundOpenSignEditorPacket(pos, SignTextSlot.FRONT));
    }

    static BlockPos signPos(ServerboundSignUpdatePacket packet) {
        return packet.pos();
    }

    static String[] signLines(ServerboundSignUpdatePacket packet) {
        return packet.lines().toArray(String[]::new);
    }
}
