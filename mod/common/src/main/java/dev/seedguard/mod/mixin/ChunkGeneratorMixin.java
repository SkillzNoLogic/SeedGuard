package dev.seedguard.mod.mixin;

import dev.seedguard.mod.SeedGuard;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Decorators: dungeons, desert wells, end gateways, geodes, ores, trees and the rest.
 *
 * <p>Each (chunk, step, feature index) gets an independent PRF seed. Cracking one dungeon's RNG
 * state reveals only that dungeon. It doesn't reveal the world seed or any other chunk.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @ModifyArg(method = "applyBiomeDecoration",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setFeatureSeed(JII)V"),
            index = 0)
    private long seedguard$featureSeed(long decorationSeed, int index, int step) {
        return SeedGuard.featureSeed(decorationSeed, index, step);
    }
}
