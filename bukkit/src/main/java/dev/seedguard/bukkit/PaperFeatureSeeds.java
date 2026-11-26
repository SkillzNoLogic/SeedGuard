package dev.seedguard.bukkit;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Logger;

/**
 * Turns on Paper's {@code feature-seeds.generate-random-seeds-for-all}.
 *
 * <p>With it on, Paper gives every configured feature (dungeons, desert wells, end gateways,
 * geodes, ores...) an independent SecureRandom seed per world, stored in
 * {@code <world>/paper-world.yml}. Decorator-based cracking (SeedCrackerX's dungeon, desert
 * well and end gateway finders) then has nothing tied to the world seed to work with.
 *
 * <p>Paper reads the world defaults before plugins load. So the first time SeedGuard flips the
 * flag, the server needs one restart.
 */
final class PaperFeatureSeeds {
    enum State { ACTIVE, ENABLED_RESTART_REQUIRED, UNAVAILABLE }

    static State ensure(File serverRoot, Logger log) {
        File file = new File(new File(serverRoot, "config"), "paper-world-defaults.yml");
        if (!file.isFile()) {
            log.warning("Could not find " + file + ". Is this Paper 1.19+? Feature seeds not configured.");
            return State.UNAVAILABLE;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (yaml.getBoolean("feature-seeds.generate-random-seeds-for-all", false)) {
                return State.ACTIVE;
            }
            yaml.set("feature-seeds.generate-random-seeds-for-all", true);
            yaml.save(file);
            return State.ENABLED_RESTART_REQUIRED;
        } catch (Exception e) {
            log.warning("Failed to update " + file + ": " + e);
            return State.UNAVAILABLE;
        }
    }
}
