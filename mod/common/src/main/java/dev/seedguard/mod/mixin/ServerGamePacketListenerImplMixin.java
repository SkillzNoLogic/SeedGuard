package dev.seedguard.mod.mixin;

import dev.seedguard.mod.ModProbeService;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Consumes the client's answer to the probe sign, so it never reaches vanilla's sign-edit logic. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Inject(method = "handleSignUpdate", at = @At("HEAD"), cancellable = true)
    private void seedguard$probeAnswer(ServerboundSignUpdatePacket packet, CallbackInfo ci) {
        if (ModProbeService.onSignUpdate(((ServerGamePacketListenerImpl) (Object) this).player, packet)) ci.cancel();
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void seedguard$forget(CallbackInfo ci) {
        ModProbeService.onDisconnect(((ServerGamePacketListenerImpl) (Object) this).player);
    }
}
