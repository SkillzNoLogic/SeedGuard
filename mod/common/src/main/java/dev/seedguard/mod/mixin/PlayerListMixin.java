package dev.seedguard.mod.mixin;

import dev.seedguard.mod.SeedGuard;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hashed seed in login/respawn packets (≤1.20.1 builds the packets here; 1.20.2+ goes through
 * ServerPlayer#createCommonSpawnInfo, see {@link ServerPlayerMixin}).
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Redirect(method = "*",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/biome/BiomeManager;obfuscateSeed(J)J"),
            require = 0)
    private long seedguard$hashedSeed(long seed) {
        return SeedGuard.hashedSeed(seed, BiomeManager.obfuscateSeed(seed));
    }
}
