package dev.seedguard.mod;

import dev.seedguard.shared.SeedKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Loader-independent core. Every mixin calls into here.
 *
 * <p>World generation code has no level context in most of the hooked methods (structure
 * placements are shared registry objects), so the active save's state is a single global. It's
 * swapped in {@code MinecraftServer#createLevels} before any level or chunk generator is built.
 */
public final class SeedGuard {
    public static final String MOD_ID = "seedguard";
    public static final Logger LOG = LoggerFactory.getLogger("SeedGuard");

    private static Path configDir = Paths.get("config");
    private static SeedGuardConfig config;
    private static volatile WorldState active = WorldState.DISABLED;

    private SeedGuard() {}

    /** Called from each loader's entrypoint. */
    public static void init(Path cfgDir) {
        configDir = cfgDir;
        config();
        LOG.info("SeedGuard loaded");
    }

    public static synchronized SeedGuardConfig config() {
        if (config == null) config = SeedGuardConfig.load(configDir.resolve("seedguard.properties"));
        return config;
    }

    public static WorldState active() {
        return active;
    }

    public static void onCreateLevels(MinecraftServer server) {
        Path worldDir = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        active = WorldState.loadOrCreate(worldDir, config());
        LOG.info("SeedGuard world state: {}", active);
    }

    public static void onServerStopped() {
        active = WorldState.DISABLED;
    }

    // ---- hooks ------------------------------------------------------------------------------

    public static long structureSeed(long seed) {
        WorldState s = active;
        return s.structures ? s.key.structureSeed(seed) : seed;
    }

    /**
     * Replaces the LCG placement in {@code RandomSpreadStructurePlacement}. It keeps vanilla's
     * grid and its spacing, separation and spread distribution, but takes the offsets from a keyed
     * PRF. Watching any number of structures of one type tells you nothing about the others.
     */
    public static ChunkPos placement(int salt, int spacing, int separation, RandomSpreadType spread, int chunkX, int chunkZ) {
        SeedKey key = active.key;
        int rx = Math.floorDiv(chunkX, spacing);
        int rz = Math.floorDiv(chunkZ, spacing);
        int bound = spacing - separation;
        long h1 = key.placement(salt, rx, rz);
        long h2 = key.placement(~salt, rx, rz);
        int ox, oz;
        if (spread == RandomSpreadType.TRIANGULAR) {
            ox = (SeedKey.bounded(h1, bound) + SeedKey.bounded(h1 << 32, bound)) / 2;
            oz = (SeedKey.bounded(h2, bound) + SeedKey.bounded(h2 << 32, bound)) / 2;
        } else {
            ox = SeedKey.bounded(h1, bound);
            oz = SeedKey.bounded(h2, bound);
        }
        return new ChunkPos(rx * spacing + ox, rz * spacing + oz);
    }

    public static boolean structuresActive() {
        return active.structures;
    }

    public static boolean frequency(int salt, int chunkX, int chunkZ, float frequency) {
        return active.key.frequency(salt, chunkX, chunkZ) < frequency;
    }

    public static long featureSeed(long decorationSeed, int index, int step) {
        WorldState s = active;
        return s.features ? s.key.feature(decorationSeed, index, step) : decorationSeed;
    }

    public static long carverSeed(long seed) {
        WorldState s = active;
        return s.carvers ? s.key.carver(seed) : seed;
    }

    public static long spikeSeed(long seed) {
        WorldState s = active;
        return s.endSpikes ? s.key.spikeSeed(seed) : seed;
    }

    /** @return null to keep vanilla slime chunks */
    public static Long slimeSeed(int chunkX, int chunkZ) {
        WorldState s = active;
        return s.slime ? s.key.slimeChunk(chunkX, chunkZ) : null;
    }

    public static long hashedSeed(long worldSeed, long vanillaHashed) {
        WorldState s = active;
        return s.hashedSeed ? s.key.hashedSeed(worldSeed) : vanillaHashed;
    }
}
