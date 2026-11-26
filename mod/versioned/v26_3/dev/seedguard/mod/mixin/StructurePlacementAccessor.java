package dev.seedguard.mod.mixin;

import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 26.3+: salt moved to AbstractSpreadingStructurePlacement (StructurePlacement became an interface). */
@Mixin(AbstractSpreadingStructurePlacement.class)
public interface StructurePlacementAccessor {
    @Accessor("salt")
    int seedguard$salt();
}
