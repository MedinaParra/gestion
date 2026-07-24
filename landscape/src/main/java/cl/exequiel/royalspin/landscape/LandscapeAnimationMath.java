package cl.exequiel.royalspin.landscape;

public final class LandscapeAnimationMath {
    private LandscapeAnimationMath() {}

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float smoothstep(float value) {
        float t = clamp(value, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static float easeOutBack(float value) {
        float t = clamp(value, 0f, 1f) - 1f;
        float c = 1.70158f;
        return 1f + (c + 1f) * t * t * t + c * t * t;
    }

    public static float breathe(long now, int seed, long periodMs, float amplitude) {
        double phase = ((now + seed * 97L) % Math.max(1L, periodMs))
                / (double) Math.max(1L, periodMs) * Math.PI * 2d;
        return 1f + (float) Math.sin(phase) * amplitude;
    }

    public static float shimmer(long now, int seed, long periodMs) {
        long period = Math.max(1L, periodMs);
        long shifted = Math.floorMod(now + seed * 137L, period);
        return shifted / (float) period;
    }

    public static float dampedLanding(long elapsedMs, float amplitude) {
        if (elapsedMs < 0L) return 0f;
        float seconds = elapsedMs / 1000f;
        return (float) (Math.exp(-6.2f * seconds) * Math.sin(seconds * 22f) * amplitude);
    }

    public static int particleBudget(int qualityTier, boolean reducedMotion, int requested) {
        if (reducedMotion) return Math.min(4, requested);
        if (qualityTier >= 2) return requested;
        if (qualityTier == 1) return Math.max(3, requested / 2);
        return Math.max(2, requested / 4);
    }
}
