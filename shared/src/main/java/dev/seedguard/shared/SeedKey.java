package dev.seedguard.shared;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * A per-world 128-bit secret, plus every value derived from it.
 *
 * <p>Each kind of derived value uses its own domain constant, so learning one kind of
 * output (for example, slime chunks) says nothing about another (for example, structure
 * positions).
 */
public final class SeedKey {
    private static final long D_PLACEMENT = 0x504c4143454d4e54L; // "PLACEMNT"
    private static final long D_FEATURE = 0x464541545552452eL;
    private static final long D_STRUCT_SEED = 0x5354525543545344L;
    private static final long D_HASHED = 0x4841534845445344L;
    private static final long D_SPIKES = 0x5350494b45535344L;
    private static final long D_SLIME = 0x534c494d45434843L;
    private static final long D_CARVER = 0x4341525645525344L;
    private static final long D_FREQ = 0x4652455155454e43L;
    private static final long D_SALT = 0x53414c5453414c54L;

    private final long k0, k1;

    public SeedKey(long k0, long k1) {
        this.k0 = k0;
        this.k1 = k1;
    }

    public static SeedKey generate() {
        SecureRandom rng = new SecureRandom();
        return new SeedKey(rng.nextLong(), rng.nextLong());
    }

    public static SeedKey fromHex(String hex) {
        String h = hex.trim();
        if (h.length() != 32) throw new IllegalArgumentException("SeedGuard key must be 32 hex chars");
        return new SeedKey(Long.parseUnsignedLong(h.substring(0, 16), 16), Long.parseUnsignedLong(h.substring(16), 16));
    }

    public String toHex() {
        return String.format("%016x%016x", k0, k1);
    }

    /** Sub-key bound to a label (e.g. a Bukkit world name), so worlds sharing a master key still differ. */
    public SeedKey derive(String label) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < label.length(); i++) {
            h ^= label.charAt(i);
            h *= 0x100000001b3L;
        }
        return new SeedKey(SipHash.hash(k0, k1, D_SALT, h, 0), SipHash.hash(k0, k1, D_SALT, h, 1));
    }

    // ---- world-gen derivations ------------------------------------------------------------

    /** Raw 64-bit placement entropy for one structure set in one region. */
    public long placement(int salt, int regionX, int regionZ) {
        return SipHash.hash(k0, k1, D_PLACEMENT, salt, pack(regionX, regionZ));
    }

    /** Replacement population seed for one feature in one chunk (the vanilla decoration seed is unique per chunk). */
    public long feature(long decorationSeed, int index, int step) {
        return SipHash.hash(k0, k1, D_FEATURE ^ decorationSeed, pack(index, step));
    }

    /** Seed used for structure layout, stronghold rings and legacy frequency reducers. */
    public long structureSeed(long worldSeed) {
        return SipHash.hash(k0, k1, D_STRUCT_SEED, worldSeed);
    }

    /** Fake biome-zoom ("hashed") seed sent to clients in login/respawn packets. */
    public long hashedSeed(long worldSeed) {
        return SipHash.hash(k0, k1, D_HASHED, worldSeed);
    }

    public long spikeSeed(long worldSeed) {
        return SipHash.hash(k0, k1, D_SPIKES, worldSeed);
    }

    public long slimeChunk(int chunkX, int chunkZ) {
        return SipHash.hash(k0, k1, D_SLIME, pack(chunkX, chunkZ));
    }

    public long carver(long carverSeed) {
        return SipHash.hash(k0, k1, D_CARVER, carverSeed);
    }

    /** Uniform double in [0,1) for structure frequency reduction (e.g. mineshafts). */
    public double frequency(int salt, int chunkX, int chunkZ) {
        return (SipHash.hash(k0, k1, D_FREQ, salt, pack(chunkX, chunkZ)) >>> 11) * 0x1.0p-53;
    }

    /** A random int salt for Spigot's seed-* options (never equal to a vanilla salt). */
    public int spigotSalt(String option) {
        long h = derive("spigot:" + option).k0;
        int v = (int) (h ^ (h >>> 32));
        return v == 0 ? 1 : v;
    }

    private static long pack(int a, int b) {
        return ((long) a << 32) | (b & 0xffffffffL);
    }

    // ---- vanilla helpers --------------------------------------------------------------------

    /** Vanilla {@code BiomeManager.obfuscateSeed}: first 8 bytes (LE) of SHA-256 over the seed (LE). */
    public static long vanillaHashedSeed(long seed) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] in = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(seed).array();
            byte[] out = md.digest(in);
            return ByteBuffer.wrap(out, 0, 8).order(ByteOrder.LITTLE_ENDIAN).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Maps a 64-bit PRF output to [0, bound) with negligible bias. */
    public static int bounded(long r, int bound) {
        return (int) (((r >>> 32) * (long) bound) >>> 32);
    }
}
