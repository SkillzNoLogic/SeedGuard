package dev.seedguard.mod.mixin;

import dev.seedguard.mod.ModProbeService;
import dev.seedguard.mod.SeedGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    /** Hashed seed for dimension changes and respawns (all versions). */
    @Redirect(method = "*",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/BiomeManager;obfuscateSeed(J)J"),
            require = 0)
    private long seedguard$hashedSeed(long seed) {
        return SeedGuard.hashedSeed(seed, BiomeManager.obfuscateSeed(seed));
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void seedguard$probeTick(CallbackInfo ci) {
        ModProbeService.tick((ServerPlayer) (Object) this);
    }
}
