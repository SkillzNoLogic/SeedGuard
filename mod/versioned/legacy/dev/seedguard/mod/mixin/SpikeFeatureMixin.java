package dev.seedguard.mod.mixin;

import dev.seedguard.mod.SeedGuard;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** End pillar heights and order. SeedCrackerX reads 16 bits of the seed from them. */
@Mixin(SpikeFeature.class)
public abstract class SpikeFeatureMixin {
    @ModifyArg(method = "getSpikesForLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;create(J)Lnet/minecraft/util/RandomSource;"),
            index = 0)
    private static long seedguard$spikeSeed(long seed) {
        return SeedGuard.spikeSeed(seed);
    }
}
