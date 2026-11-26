package dev.seedguard.bukkit;

import dev.seedguard.shared.SeedKey;
import org.bukkit.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Maps each world's vanilla hashed seed (SHA-256 of the seed) to a secret per-world stand-in.
 *
 * <p>The netty handler rewrites matching longs in login/respawn packets. It matches by value,
 * not by field name, so it works the same on Spigot-mapped and Mojang-mapped servers, and on
 * both record and class packet layouts.
 */
final class HashedSeedSpoofer {
    private final Map<Long, Long> replacements = new ConcurrentHashMap<>();
    private final Function<String, SeedKey> keyForWorld;

    HashedSeedSpoofer(Function<String, SeedKey> keyForWorld) {
        this.keyForWorld = keyForWorld;
    }

    void register(World world) {
        long seed = world.getSeed();
        replacements.put(SeedKey.vanillaHashedSeed(seed), keyForWorld.apply(world.getName()).hashedSeed(seed));
    }

    /** Returns the replacement, or the input unchanged when it isn't a known hashed seed. */
    long map(long value) {
        Long r = replacements.get(value);
        return r == null ? value : r;
    }

    boolean isEmpty() {
        return replacements.isEmpty();
    }
}
