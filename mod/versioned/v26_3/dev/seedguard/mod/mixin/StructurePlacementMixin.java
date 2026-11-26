package dev.seedguard.mod.mixin;

import dev.seedguard.mod.SeedGuard;
import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Frequency reduction (mineshafts, for example) uses the keyed PRF instead of an LCG seeded from the world seed. */
@Mixin(AbstractSpreadingStructurePlacement.class)
public abstract class StructurePlacementMixin {
    @Redirect(method = "applyAdditionalChunkRestrictions",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/placement/AbstractSpreadingStructurePlacement$FrequencyReductionMethod;shouldGenerate(JIIIF)Z"),
            require = 0)
    private boolean seedguard$frequency(AbstractSpreadingStructurePlacement.FrequencyReductionMethod method, long seed, int salt, int x, int z, float frequency) {
        if (SeedGuard.structuresActive()) return SeedGuard.frequency(salt, x, z, frequency);
        return method.shouldGenerate(seed, salt, x, z, frequency);
    }
}
