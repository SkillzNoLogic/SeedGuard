package dev.seedguard.shared;

/**
 * SipHash-2-4 over 64-bit words.
 *
 * <p>SipHash is a keyed PRF with a 128-bit key: without the key its outputs can't be
 * predicted or inverted. Java's LCG ({@code java.util.Random}) is the opposite. One observed
 * structure or decorator leaks its whole 48-bit state, and seed crackers exploit exactly that.
 * SeedGuard swaps the LCG for SipHash wherever a value would otherwise leak the world seed.
 *
 * <p>The state lives in a short-lived local object, which HotSpot's escape analysis
 * scalar-replaces. So the call doesn't allocate on world-gen hot paths.
 */
public final class SipHash {
    private long v0, v1, v2, v3;

    private SipHash(long k0, long k1) {
        v0 = 0x736f6d6570736575L ^ k0;
        v1 = 0x646f72616e646f6dL ^ k1;
        v2 = 0x6c7967656e657261L ^ k0;
        v3 = 0x7465646279746573L ^ k1;
    }

    private void round() {
        v0 += v1; v1 = Long.rotateLeft(v1, 13); v1 ^= v0; v0 = Long.rotateLeft(v0, 32);
        v2 += v3; v3 = Long.rotateLeft(v3, 16); v3 ^= v2;
        v0 += v3; v3 = Long.rotateLeft(v3, 21); v3 ^= v0;
        v2 += v1; v1 = Long.rotateLeft(v1, 17); v1 ^= v2; v2 = Long.rotateLeft(v2, 32);
    }

    private void absorb(long m) {
        v3 ^= m;
        round();
        round();
        v0 ^= m;
    }

    private long finish(int words) {
        long b = ((long) ((words * 8) & 0xff)) << 56;
        absorb(b);
        v2 ^= 0xff;
        round(); round(); round(); round();
        return v0 ^ v1 ^ v2 ^ v3;
    }

    public static long hash(long k0, long k1, long m0, long m1) {
        SipHash s = new SipHash(k0, k1);
        s.absorb(m0);
        s.absorb(m1);
        return s.finish(2);
    }

    public static long hash(long k0, long k1, long m0, long m1, long m2) {
        SipHash s = new SipHash(k0, k1);
        s.absorb(m0);
        s.absorb(m1);
        s.absorb(m2);
        return s.finish(3);
    }

    public static long hash(long k0, long k1, long... m) {
        SipHash s = new SipHash(k0, k1);
        for (long w : m) s.absorb(w);
        return s.finish(m.length);
    }
}
