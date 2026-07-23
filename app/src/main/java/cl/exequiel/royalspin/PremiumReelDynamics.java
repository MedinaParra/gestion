package cl.exequiel.royalspin;

/** Pure deterministic presentation math. Never changes RNG, board or payout. */
public final class PremiumReelDynamics {
    private PremiumReelDynamics() { }

    public static final long[] BASE_STOPS = {820L, 1110L, 1410L, 1730L, 2070L};
    public static final long ANTICIPATION_EXTRA_MS = 720L;

    public static boolean shouldAnticipate(StakeSlotEngine.SpinResult result) {
        if (result == null) return false;
        StakeSlotEngine.LineWin best = SymbolAnimationDirector.primaryWin(result.lineWins);
        return result.payoutMultiplier() >= 5d || (best != null && best.count >= 4);
    }

    public static long stopTime(int reel, boolean anticipation, boolean reducedMotion) {
        int r = Math.max(0, Math.min(4, reel));
        long base = BASE_STOPS[r];
        if (anticipation && r == 4) base += ANTICIPATION_EXTRA_MS;
        if (reducedMotion) base = Math.round(base * .72f);
        return base;
    }

    public static float velocity(long elapsedMs, long stopMs) {
        if (stopMs <= 0L || elapsedMs >= stopMs) return 0f;
        float q = clamp(elapsedMs / (float) stopMs);
        float accelerate = smoothStep(clamp(q / .18f));
        float decelerate = 1f - smoothStep(clamp((q - .67f) / .33f));
        return .32f + 2.25f * accelerate * decelerate;
    }

    public static float cylinderScale(float normalizedDistanceFromCenter) {
        float d = clamp(Math.abs(normalizedDistanceFromCenter));
        return .58f + .42f * (float) Math.cos(d * Math.PI * .5);
    }

    public static float cylinderAlpha(float normalizedDistanceFromCenter) {
        float d = clamp(Math.abs(normalizedDistanceFromCenter));
        return .28f + .72f * (1f - d * d);
    }

    public static float landingOffset(long elapsedAfterStopMs, boolean reducedMotion) {
        if (elapsedAfterStopMs < 0L || elapsedAfterStopMs > 700L) return 0f;
        float amplitude = reducedMotion ? 3.2f : 11.5f;
        float frequency = reducedMotion ? .028f : .038f;
        return (float) (Math.sin(elapsedAfterStopMs * frequency)
                * Math.exp(-elapsedAfterStopMs / 170f) * amplitude);
    }

    public static float impactPulse(long elapsedAfterStopMs) {
        if (elapsedAfterStopMs < 0L || elapsedAfterStopMs > 420L) return 0f;
        float q = elapsedAfterStopMs / 420f;
        return (1f - q) * (1f - q);
    }

    public static float anticipationPulse(long elapsedMs, long lastBaseStopMs) {
        if (elapsedMs < lastBaseStopMs) return 0f;
        float q = clamp((elapsedMs - lastBaseStopMs) / (float) ANTICIPATION_EXTRA_MS);
        return (float) (Math.sin(q * Math.PI * 5) * .5 + .5) * (1f - .28f * q);
    }

    public static float cameraZoom(double multiplier, long revealElapsedMs, boolean reducedMotion) {
        if (reducedMotion || multiplier < 5d) return 1f;
        float magnitude = multiplier >= 100d ? .075f : multiplier >= 25d ? .055f : .035f;
        float enter = smoothStep(clamp(revealElapsedMs / 420f));
        float settle = 1f - smoothStep(clamp((revealElapsedMs - 1250f) / 1200f));
        return 1f + magnitude * enter * settle;
    }

    public static int particleBudget(double multiplier, boolean reducedMotion, int qualityTier) {
        int base = multiplier >= 100d ? 190 : multiplier >= 25d ? 145 : multiplier >= 5d ? 92 : 48;
        if (qualityTier <= 0) base = Math.round(base * .34f);
        else if (qualityTier == 1) base = Math.round(base * .65f);
        if (reducedMotion) base = Math.min(36, Math.round(base * .35f));
        return Math.max(8, base);
    }

    static float smoothStep(float x) {
        x = clamp(x);
        return x * x * (3f - 2f * x);
    }

    static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}