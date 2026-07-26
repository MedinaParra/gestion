package cl.exequiel.royalspin.landscape;

/** Pure timing model shared by the renderer and unit tests. */
public final class AnimationProfile {
    private static final long ACCEL_MS = 260L;
    private static final long DECEL_MS = 620L;
    private static final float MAX_CELLS_PER_SECOND = 20.5f;

    private static final long[] NORMAL_STOPS = {1320L, 1620L, 1940L, 2290L, 2680L};
    private static final long[] ANTICIPATION_STOPS = {1320L, 1650L, 2020L, 2540L, 3380L};

    private AnimationProfile() {}

    public static long[] stopTimes(boolean anticipation) {
        return (anticipation ? ANTICIPATION_STOPS : NORMAL_STOPS).clone();
    }

    public static float reelDistanceCells(long elapsedMs, long stopMs) {
        if (elapsedMs <= 0L) return 0f;
        long decelStart = Math.max(ACCEL_MS, stopMs - DECEL_MS);

        if (elapsedMs < ACCEL_MS) {
            double t = elapsedMs / 1000.0;
            double accelSeconds = ACCEL_MS / 1000.0;
            double acceleration = MAX_CELLS_PER_SECOND / accelSeconds;
            return (float) (0.5 * acceleration * t * t);
        }

        double accelSeconds = ACCEL_MS / 1000.0;
        double accelDistance = 0.5 * MAX_CELLS_PER_SECOND * accelSeconds;
        if (elapsedMs < decelStart) {
            double seconds = (elapsedMs - ACCEL_MS) / 1000.0;
            return (float) (accelDistance + MAX_CELLS_PER_SECOND * seconds);
        }

        double cruiseSeconds = Math.max(0L, decelStart - ACCEL_MS) / 1000.0;
        double beforeDecel = accelDistance + MAX_CELLS_PER_SECOND * cruiseSeconds;
        double s = clamp((elapsedMs - decelStart) / (double) Math.max(1L, stopMs - decelStart), 0.0, 1.0);
        double decelSeconds = Math.max(1L, stopMs - decelStart) / 1000.0;
        double integrated = s - s * s + (s * s * s) / 3.0;
        return (float) (beforeDecel + MAX_CELLS_PER_SECOND * decelSeconds * integrated);
    }

    public static float reelVelocity(long elapsedMs, long stopMs) {
        if (elapsedMs <= 0L || elapsedMs >= stopMs) return 0f;
        long decelStart = Math.max(ACCEL_MS, stopMs - DECEL_MS);
        if (elapsedMs < ACCEL_MS) {
            return MAX_CELLS_PER_SECOND * elapsedMs / (float) ACCEL_MS;
        }
        if (elapsedMs < decelStart) return MAX_CELLS_PER_SECOND;
        float s = clamp((elapsedMs - decelStart) / (float) Math.max(1L, stopMs - decelStart), 0f, 1f);
        float remaining = 1f - s;
        return MAX_CELLS_PER_SECOND * remaining * remaining;
    }

    public static float stopBounce(long elapsedSinceStopMs) {
        if (elapsedSinceStopMs < 0L || elapsedSinceStopMs > 520L) return 0f;
        float t = elapsedSinceStopMs / 1000f;
        return (float) (18.0 * Math.exp(-8.2 * t) * Math.sin(31.0 * t));
    }

    public static float pressScale(long elapsedMs) {
        float t = clamp(elapsedMs / 180f, 0f, 1f);
        return 1f - 0.08f * (float) Math.sin(Math.PI * t);
    }

    public static float revealProgress(long elapsedMs, long durationMs) {
        float t = clamp(elapsedMs / (float) Math.max(1L, durationMs), 0f, 1f);
        return 1f - (float) Math.pow(1f - t, 3.0);
    }

    public static float smoothStep(float t) {
        t = clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
