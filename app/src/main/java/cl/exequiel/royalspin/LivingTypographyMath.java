package cl.exequiel.royalspin;

/** Pure animation curves for premium living typography. */
public final class LivingTypographyMath {
    private LivingTypographyMath() { }

    public static float breathe(long nowMs, long periodMs, float amplitude) {
        if (periodMs <= 0L || amplitude <= 0f) return 1f;
        double phase = (nowMs % periodMs) / (double) periodMs * Math.PI * 2d;
        return 1f + amplitude * (float) Math.sin(phase);
    }

    public static float letterWave(long nowMs, int index, long periodMs, float amplitude) {
        if (periodMs <= 0L || amplitude == 0f) return 0f;
        double phase = (nowMs % periodMs) / (double) periodMs * Math.PI * 2d
                + index * .58d;
        return amplitude * (float) Math.sin(phase);
    }

    public static float tracking(long nowMs, long periodMs, float base, float amplitude) {
        if (periodMs <= 0L) return base;
        double phase = (nowMs % periodMs) / (double) periodMs * Math.PI * 2d;
        return base + amplitude * (.5f + .5f * (float) Math.sin(phase));
    }

    public static float shimmer(long nowMs, long periodMs) {
        if (periodMs <= 0L) return 0f;
        return (nowMs % periodMs) / (float) periodMs;
    }

    public static float glow(long nowMs, long periodMs, float minimum, float maximum) {
        float q = .5f + .5f * (float) Math.sin((nowMs % periodMs)
                / (double) periodMs * Math.PI * 2d);
        return minimum + (maximum - minimum) * q;
    }

    public static float stagger(long elapsedMs, int index, long delayMs, long durationMs) {
        if (durationMs <= 0L) return 1f;
        float q = (elapsedMs - index * delayMs) / (float) durationMs;
        q = clamp(q);
        float overshoot = 1.70158f;
        float t = q - 1f;
        return 1f + (overshoot + 1f) * t * t * t + overshoot * t * t;
    }

    public static float flash(long elapsedMs, long durationMs) {
        if (elapsedMs < 0L || elapsedMs >= durationMs || durationMs <= 0L) return 0f;
        float q = elapsedMs / (float) durationMs;
        return (float) Math.sin(q * Math.PI);
    }

    static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
