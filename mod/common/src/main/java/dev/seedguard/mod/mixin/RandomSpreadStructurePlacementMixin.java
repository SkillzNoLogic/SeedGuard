package dev.seedguard.mod.mixin;

import dev.seedguard.mod.SeedGuard;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Grid placement for villages, temples, shipwrecks, monuments, end cities and so on. Same grid, keyed-PRF offsets. */
@Mixin(RandomSpreadStructurePlacement.class)
public abstract class RandomSpreadStructurePlacementMixin {
    @Inject(method = "getPotentialStructureChunk", at = @At("HEAD"), cancellable = true)
    private void seedguard$placement(long seed, int x, int z, CallbackInfoReturnable<ChunkPos> cir) {
        if (!SeedGuard.structuresActive()) return;
        RandomSpreadStructurePlacement self = (RandomSpreadStructurePlacement) (Object) this;
        int salt = ((StructurePlacementAccessor) self).seedguard$salt();
        cir.setReturnValue(SeedGuard.placement(salt, self.spacing(), self.separation(), self.spreadType(), x, z));
    }
}
