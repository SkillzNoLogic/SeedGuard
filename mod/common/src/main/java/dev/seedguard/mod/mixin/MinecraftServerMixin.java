package dev.seedguard.mod.mixin;

import dev.seedguard.mod.SeedGuard;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loads the save's secret before any level or chunk generator exists. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "createLevels", at = @At("HEAD"))
    private void seedguard$loadWorldState(CallbackInfo ci) {
        SeedGuard.onCreateLevels((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("TAIL"))
    private void seedguard$reset(CallbackInfo ci) {
        SeedGuard.onServerStopped();
    }
}
