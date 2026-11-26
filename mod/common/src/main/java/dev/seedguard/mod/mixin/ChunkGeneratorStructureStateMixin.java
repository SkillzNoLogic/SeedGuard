package dev.seedguard.mod.mixin;

import dev.seedguard.mod.SeedGuard;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Gives the structure system a secret seed instead of the world seed.
 *
 * <p>This covers structure piece layouts, stronghold rings and legacy frequency reducers. Terrain
 * and biomes use {@code RandomState}, which this doesn't touch, so they stay exactly vanilla.
 */
@Mixin(ChunkGeneratorStructureState.class)
public abstract class ChunkGeneratorStructureStateMixin {
    @ModifyVariable(method = {"createForNormal", "createForFlat"}, at = @At("HEAD"), argsOnly = true)
    private static long seedguard$structureSeed(long seed) {
        return SeedGuard.structureSeed(seed);
    }
}
