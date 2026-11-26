package dev.seedguard.mod.mixin;

import dev.seedguard.mod.SeedGuard;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * StructureCheck predicts structures in chunks that haven't generated yet. /locate, explorer and
 * treasure maps, and dolphins all rely on it. It keeps its own copy of the world seed, so it must
 * get the same secret structure seed as generation. Otherwise /locate would point at structures
 * that never generate.
 */
@Mixin(StructureCheck.class)
public abstract class StructureCheckMixin {
    @Shadow
    @Final
    @Mutable
    private long seed;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void seedguard$structureSeed(CallbackInfo ci) {
        this.seed = SeedGuard.structureSeed(this.seed);
    }
}
