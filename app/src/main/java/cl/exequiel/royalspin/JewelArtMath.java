package cl.exequiel.royalspin;

/** Pure curves used by the Royal Spin 4.0 jewel-art renderer. */
public final class JewelArtMath {
    private JewelArtMath() { }

    public static float cycle(long nowMs, int seed, long periodMs) {
        long safe = Math.max(1L, periodMs);
        return Math.floorMod(nowMs + seed * 283L, safe) / (float) safe;
    }

    public static float sine(long nowMs, int seed, long periodMs) {
        return (float) Math.sin(cycle(nowMs, seed, periodMs) * Math.PI * 2d);
    }

    public static float breathe(long nowMs, int seed, long periodMs, float amplitude) {
        return 1f + sine(nowMs, seed, periodMs) * Math.max(0f, amplitude);
    }

    public static float shimmer(long nowMs, int seed, long periodMs) {
        return cycle(nowMs, seed, periodMs);
    }

    public static float landing(long elapsedMs, int reel, boolean reducedMotion) {
        if (reducedMotion) return 0f;
        long local = elapsedMs - reel * 82L;
        if (local <= 0L || local >= 760L) return 0f;
        float seconds = local / 1000f;
        return (float) (Math.exp(-5.2f * seconds) * Math.sin(seconds * 27f));
    }

    public static float winPulse(long elapsedMs, int seed, boolean winning) {
        if (!winning) return .28f + .12f * sine(elapsedMs, seed, 3300L);
        float attack = clamp(elapsedMs / 230f);
        float wave = .72f + .28f * sine(elapsedMs, seed, 620L);
        return clamp(attack * wave);
    }

    public static float jewelSpark(long nowMs, int seed) {
        float x = cycle(nowMs, seed, 1750L + Math.floorMod(seed, 5) * 190L);
        float distance = Math.abs(x - .5f) * 2f;
        return (float) Math.pow(Math.max(0f, 1f - distance), 7d);
    }

    public static int facetCount(int qualityTier, boolean reducedMotion, boolean suppressed) {
        if (suppressed || reducedMotion || qualityTier <= 0) return 3;
        if (qualityTier == 1) return 5;
        return 8;
    }

    public static int particleCount(int qualityTier, boolean reducedMotion,
                                    boolean suppressed, int ultraCount) {
        if (suppressed || reducedMotion) return Math.min(2, Math.max(0, ultraCount));
        if (qualityTier <= 0) return Math.max(1, ultraCount / 4);
        if (qualityTier == 1) return Math.max(2, ultraCount / 2);
        return Math.max(2, ultraCount);
    }

    public static float smoothstep(float value) {
        float x = clamp(value);
        return x * x * (3f - 2f * x);
    }

    public static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
