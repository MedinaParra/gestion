package cl.exequiel.royalspin;

/** Rolling frame-time metrics used to expose FPS and slow-frame percentage in the demo. */
public final class FrameStats {
    private static final int SAMPLE_COUNT = 120;
    private static final long SLOW_FRAME_NS = 24_000_000L;

    private final long[] samples = new long[SAMPLE_COUNT];
    private int sampleIndex;
    private int sampleSize;
    private long previousFrameNanos;
    private long totalFrames;
    private long slowFrames;

    public float record(long frameTimeNanos) {
        if (previousFrameNanos == 0L) {
            previousFrameNanos = frameTimeNanos;
            return 0f;
        }
        long delta = Math.max(0L, frameTimeNanos - previousFrameNanos);
        previousFrameNanos = frameTimeNanos;
        samples[sampleIndex] = delta;
        sampleIndex = (sampleIndex + 1) % SAMPLE_COUNT;
        sampleSize = Math.min(SAMPLE_COUNT, sampleSize + 1);
        totalFrames++;
        if (delta > SLOW_FRAME_NS) slowFrames++;
        return delta / 1_000_000_000f;
    }

    public int fps() {
        if (sampleSize == 0) return 0;
        long total = 0L;
        for (int i = 0; i < sampleSize; i++) total += samples[i];
        if (total <= 0L) return 0;
        double average = total / (double) sampleSize;
        return (int) Math.round(1_000_000_000d / average);
    }

    public float slowFramePercent() {
        if (totalFrames == 0L) return 0f;
        return slowFrames * 100f / totalFrames;
    }

    public void resetClock() {
        previousFrameNanos = 0L;
    }
}
