package dev.seedguard.neoforge;

import dev.seedguard.mod.SeedGuard;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

@Mod(SeedGuard.MOD_ID)
public final class SeedGuardNeoForge {
    public SeedGuardNeoForge() {
        SeedGuard.init(FMLPaths.CONFIGDIR.get());
    }
}
