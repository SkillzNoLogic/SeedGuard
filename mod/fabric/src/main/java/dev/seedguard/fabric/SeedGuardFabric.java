package dev.seedguard.fabric;

import dev.seedguard.mod.SeedGuard;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class SeedGuardFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SeedGuard.init(FabricLoader.getInstance().getConfigDir());
    }
}
