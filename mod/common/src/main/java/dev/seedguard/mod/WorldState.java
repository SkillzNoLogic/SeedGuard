package dev.seedguard.mod;

import dev.seedguard.shared.SeedKey;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Per-save secret and feature switches, stored in {@code <world>/seedguard.properties}.
 *
 * <p>The file lives in the world folder, so the secret travels with backups and copies. A copied
 * world keeps generating the same way on another server.
 */
public final class WorldState {
    public static final WorldState DISABLED = new WorldState(null, false, false, false, false, false, false);

    public final SeedKey key;
    public final boolean structures, features, slime, endSpikes, carvers, hashedSeed;

    private WorldState(SeedKey key, boolean structures, boolean features, boolean slime, boolean endSpikes, boolean carvers, boolean hashedSeed) {
        this.key = key;
        this.structures = structures;
        this.features = features;
        this.slime = slime;
        this.endSpikes = endSpikes;
        this.carvers = carvers;
        this.hashedSeed = hashedSeed;
    }

    static WorldState loadOrCreate(Path worldDir, SeedGuardConfig cfg) {
        Path file = worldDir.resolve("seedguard.properties");
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
                return fromProps(p);
            } catch (IOException | RuntimeException e) {
                SeedGuard.LOG.error("Unreadable {}; protection DISABLED for safety (fix or delete the file): {}", file, e.toString());
                return DISABLED;
            }
        }

        boolean overworldGenerated = hasRegions(worldDir.resolve("region"));
        boolean endGenerated = hasRegions(worldDir.resolve("DIM1").resolve("region"));
        boolean anyGenerated = overworldGenerated || endGenerated || hasRegions(worldDir.resolve("DIM-1").resolve("region"));
        boolean fresh = !anyGenerated || cfg.protectExistingWorlds;

        p.setProperty("key", SeedKey.generate().toHex());
        p.setProperty("structures", Boolean.toString(fresh));
        p.setProperty("slime-chunks", Boolean.toString(fresh));
        // Moving pillars in an End that already exists would break dragon-respawn crystal positions.
        p.setProperty("end-spikes", Boolean.toString(!endGenerated || cfg.protectExistingWorlds));
        // Re-seeding decoration is seamless (features don't leave chunk-border seams), so it's always safe.
        p.setProperty("features", "true");
        p.setProperty("carvers", Boolean.toString(cfg.carvers && fresh));
        p.setProperty("hashed-seed", "true");
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, """
                     SeedGuard per-world state. KEEP THIS FILE WITH THE WORLD and keep it private.
                     Changing 'key' or a switch moves things in chunks generated afterwards only.""");
        } catch (IOException e) {
            SeedGuard.LOG.error("Could not write {}: {}", file, e.toString());
        }
        SeedGuard.LOG.info("Created {} ({})", file, fresh ? "new world: full protection" : "existing world: structures/slime kept vanilla");
        return fromProps(p);
    }

    private static WorldState fromProps(Properties p) {
        SeedKey key = SeedKey.fromHex(p.getProperty("key"));
        return new WorldState(key,
                flag(p, "structures"), flag(p, "features"), flag(p, "slime-chunks"),
                flag(p, "end-spikes"), flag(p, "carvers"), flag(p, "hashed-seed"));
    }

    private static boolean flag(Properties p, String k) {
        return Boolean.parseBoolean(p.getProperty(k, "false").trim());
    }

    private static boolean hasRegions(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        try (Stream<Path> s = Files.list(dir)) {
            return s.anyMatch(f -> f.getFileName().toString().endsWith(".mca"));
        } catch (IOException e) {
            return true;
        }
    }

    @Override
    public String toString() {
        return "structures=" + structures + " features=" + features + " slime=" + slime
                + " endSpikes=" + endSpikes + " carvers=" + carvers + " hashedSeed=" + hashedSeed;
    }
}
