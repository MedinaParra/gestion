package cl.exequiel.royalspin;

/** Pure deterministic curves shared by the living-symbol and cabinet presentation layers. */
public final class LivingSymbolMath {
    private LivingSymbolMath() { }

    public static float pulse(long nowMs, int seed, long periodMs, float min, float max) {
        long safePeriod = Math.max(1L, periodMs);
        double phase = ((nowMs + seed * 173L) % safePeriod) / (double) safePeriod;
        float wave = .5f + .5f * (float) Math.sin(phase * Math.PI * 2d);
        return min + (max - min) * wave;
    }

    public static float wave(long nowMs, int seed, long periodMs, float amplitude) {
        long safePeriod = Math.max(1L, periodMs);
        double phase = ((nowMs + seed * 211L) % safePeriod) / (double) safePeriod;
        return (float) Math.sin(phase * Math.PI * 2d) * amplitude;
    }

    public static float shimmer(long nowMs, int seed, long periodMs) {
        long safePeriod = Math.max(1L, periodMs);
        long shifted = Math.floorMod(nowMs + seed * 257L, safePeriod);
        return shifted / (float) safePeriod;
    }

    public static float dampedKick(long elapsedMs, float amplitude) {
        if (elapsedMs <= 0L) return 0f;
        float seconds = elapsedMs / 1000f;
        return amplitude * (float) Math.exp(-4.2f * seconds)
                * (float) Math.sin(seconds * 25f);
    }

    public static float anticipation(long elapsedMs, long startMs, long endMs) {
        if (endMs <= startMs) return elapsedMs >= endMs ? 1f : 0f;
        return smoothstep(clamp((elapsedMs - startMs) / (float) (endMs - startMs)));
    }

    public static int particleCount(int qualityTier, boolean reducedMotion, int ultraCount) {
        if (reducedMotion) return Math.min(2, ultraCount);
        if (qualityTier <= 0) return Math.max(1, ultraCount / 4);
        if (qualityTier == 1) return Math.max(2, ultraCount / 2);
        return Math.max(2, ultraCount);
    }

    public static float orbitX(long nowMs, int seed, float radius, long periodMs) {
        double angle = orbitAngle(nowMs, seed, periodMs);
        return (float) Math.cos(angle) * radius;
    }

    public static float orbitY(long nowMs, int seed, float radius, long periodMs) {
        double angle = orbitAngle(nowMs, seed, periodMs);
        return (float) Math.sin(angle) * radius;
    }

    private static double orbitAngle(long nowMs, int seed, long periodMs) {
        long safePeriod = Math.max(1L, periodMs);
        double phase = Math.floorMod(nowMs + seed * 331L, safePeriod) / (double) safePeriod;
        return phase * Math.PI * 2d;
    }

    public static float smoothstep(float value) {
        float x = clamp(value);
        return x * x * (3f - 2f * x);
    }

    public static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
