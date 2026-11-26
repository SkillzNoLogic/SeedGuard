package dev.seedguard.forge;

import dev.seedguard.mod.SeedGuard;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(SeedGuard.MOD_ID)
public final class SeedGuardForge {
    public SeedGuardForge() {
        SeedGuard.init(FMLPaths.CONFIGDIR.get());
    }
}
