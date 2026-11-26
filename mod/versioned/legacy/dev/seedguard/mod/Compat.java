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

import java.util.List;

/** Version-specific calls for Minecraft 1.20.1 – 1.21.x. */
final class Compat {
    private Compat() {}

    static MinecraftServer server(ServerPlayer p) {
        return p.getServer();
    }

    static boolean isStaff(ServerPlayer p) {
        return p.hasPermissions(2);
    }

    static void openFakeSign(ServerPlayer p, BlockPos pos, List<Component> lines) {
        BlockState state = Blocks.OAK_SIGN.defaultBlockState();
        SignBlockEntity sign = new SignBlockEntity(pos, state);
        sign.setLevel(p.level());
        SignText text = new SignText();
        for (int i = 0; i < lines.size(); i++) text = text.setMessage(i, lines.get(i));
        sign.setText(text, true);
        p.connection.send(new ClientboundBlockUpdatePacket(pos, state));
        p.connection.send(ClientboundBlockEntityDataPacket.create(sign));
        p.connection.send(new ClientboundOpenSignEditorPacket(pos, true));
    }

    static BlockPos signPos(ServerboundSignUpdatePacket packet) {
        return packet.getPos();
    }

    static String[] signLines(ServerboundSignUpdatePacket packet) {
        return packet.getLines();
    }
}
