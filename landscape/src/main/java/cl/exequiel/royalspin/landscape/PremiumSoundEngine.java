package cl.exequiel.royalspin.landscape;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class PremiumSoundEngine {
    private static final int SAMPLE_RATE = 44_100;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<String, short[]> cache = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;

    void setEnabled(boolean value) { enabled = value; }
    boolean isEnabled() { return enabled; }
    void playSpin() { play("spin", () -> synthWhoosh(0.72, 80, 920, 0.34)); }
    void playReelStop(int reel) {
        play("stop-" + reel, () -> mix(
                synthTone(0.16, 170 + reel * 42, 0.34, 8),
                synthNoise(0.08, 0.16, 11 + reel)));
    }
    void playSymbol(String symbol) {
        if (LandscapeGameEngine.BELL.equals(symbol)) play("bell", this::synthBell);
        else if (LandscapeGameEngine.BAR.equals(symbol)) play("bar", this::synthBarSlam);
        else if (LandscapeGameEngine.SEVEN.equals(symbol)) play("seven", this::synthSevenBurst);
        else if (LandscapeGameEngine.DIAMOND.equals(symbol)) play("diamond", this::synthDiamond);
        else if (LandscapeGameEngine.WILD.equals(symbol)) play("wild", this::synthWild);
        else play("small", () -> mix(synthTone(0.24, 660, 0.16, 14), synthTone(0.24, 990, 0.10, 12)));
    }
    void playFeature() {
        play("feature", () -> mix(
                synthWhoosh(1.25, 90, 1800, 0.28),
                synthChord(1.45, new double[]{261.63, 329.63, 392.0, 523.25}, 0.24),
                synthNoise(0.22, 0.12, 77)));
    }
    void playRetrigger() {
        play("retrigger", () -> mix(
                synthChord(0.85, new double[]{392, 493.88, 587.33, 783.99}, 0.22),
                synthTone(0.22, 110, 0.26, 4)));
    }
    void playCountTick(int step) {
        play("tick-" + (step % 4), () -> synthTone(0.07, 720 + (step % 4) * 90, 0.10, 20));
    }
    void release() { enabled = false; executor.shutdownNow(); cache.clear(); }

    private void play(String key, Factory factory) {
        if (!enabled) return;
        executor.execute(() -> {
            if (!enabled || Thread.currentThread().isInterrupted()) return;
            short[] pcm = cache.computeIfAbsent(key, ignored -> factory.create());
            AudioTrack track = createTrack(pcm.length);
            if (track == null) return;
            try {
                track.play();
                track.write(pcm, 0, pcm.length);
                track.stop();
            } catch (IllegalStateException ignored) {
            } finally {
                track.release();
            }
        });
    }

    private static AudioTrack createTrack(int sampleCount) {
        int bytes = sampleCount * 2;
        try {
            return new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(Math.max(bytes, AudioTrack.getMinBufferSize(
                            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT)))
                    .build();
        } catch (UnsupportedOperationException | IllegalArgumentException error) {
            return null;
        }
    }

    private short[] synthBell() {
        int n = samples(1.2);
        double[] frequencies = {660, 990, 1320, 1815, 2310};
        double[] amplitudes = {.48, .28, .17, .11, .07};
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double value = 0;
            for (int h = 0; h < frequencies.length; h++) {
                value += Math.sin(2 * Math.PI * frequencies[h] * t)
                        * amplitudes[h] * Math.exp(-(2.8 + h * .8) * t);
            }
            out[i] = pcm(value * .72);
        }
        return out;
    }
    private short[] synthBarSlam() {
        return mix(synthTone(0.48, 92, 0.44, 3), synthTone(0.32, 184, 0.22, 6), synthNoise(0.28, 0.30, 41));
    }
    private short[] synthSevenBurst() {
        int n = samples(1.05);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double progress = t / 1.05;
            double f = 280 + 1700 * progress * progress;
            double tone = Math.sin(2 * Math.PI * f * t) * Math.exp(-1.8 * t);
            double sub = Math.sin(2 * Math.PI * 74 * t) * Math.exp(-7.5 * t);
            out[i] = pcm((tone * .30 + sub * .30) * envelope(t, 1.05, .03, .22));
        }
        return out;
    }
    private short[] synthDiamond() {
        return mix(synthTone(0.92, 1046.5, 0.22, 10), synthTone(0.92, 1569.8, 0.16, 12), synthTone(0.92, 2093.0, 0.10, 16));
    }
    private short[] synthWild() {
        return mix(synthWhoosh(0.95, 110, 1450, 0.24), synthChord(1.05, new double[]{196, 246.94, 293.66, 392}, 0.23), synthTone(0.28, 72, 0.30, 3));
    }
    private static short[] synthWhoosh(double duration, double startHz, double endHz, double gain) {
        int n = samples(duration);
        short[] out = new short[n];
        long state = 0x1234ABCDL;
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double progress = t / duration;
            double frequency = startHz + (endHz - startHz) * progress * progress;
            phase += 2 * Math.PI * frequency / SAMPLE_RATE;
            state = state * 1664525L + 1013904223L;
            double noise = (((state >>> 16) & 0x7FFF) / 16384.0 - 1.0);
            double value = Math.sin(phase) * .42 + noise * .58;
            out[i] = pcm(value * gain * envelope(t, duration, .08, .18));
        }
        return out;
    }
    private static short[] synthChord(double duration, double[] frequencies, double gain) {
        int n = samples(duration);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double value = 0;
            for (double frequency : frequencies) value += Math.sin(2 * Math.PI * frequency * t);
            value /= Math.max(1, frequencies.length);
            out[i] = pcm(value * gain * envelope(t, duration, .04, .35));
        }
        return out;
    }
    private static short[] synthTone(double duration, double frequency, double gain, double decay) {
        int n = samples(duration);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            double value = Math.sin(2 * Math.PI * frequency * t)
                    + .25 * Math.sin(2 * Math.PI * frequency * 2.01 * t);
            value *= Math.exp(-decay * t);
            out[i] = pcm(value * gain);
        }
        return out;
    }
    private static short[] synthNoise(double duration, double gain, long seed) {
        int n = samples(duration);
        short[] out = new short[n];
        long state = seed;
        for (int i = 0; i < n; i++) {
            state = state * 1103515245L + 12345L;
            double value = (((state >>> 16) & 0x7FFF) / 16384.0 - 1.0);
            double t = i / (double) SAMPLE_RATE;
            out[i] = pcm(value * gain * Math.exp(-12 * t));
        }
        return out;
    }
    private static short[] mix(short[]... sources) {
        int length = 0;
        for (short[] source : sources) length = Math.max(length, source.length);
        short[] out = new short[length];
        for (int i = 0; i < length; i++) {
            int value = 0;
            for (short[] source : sources) if (i < source.length) value += source[i];
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
        }
        return out;
    }
    private static double envelope(double t, double duration, double attack, double release) {
        double in = Math.min(1.0, t / Math.max(.001, attack));
        double out = Math.min(1.0, (duration - t) / Math.max(.001, release));
        return Math.max(0, Math.min(in, out));
    }
    private static int samples(double seconds) { return Math.max(1, (int) Math.round(seconds * SAMPLE_RATE)); }
    private static short pcm(double value) { return (short) Math.round(Math.max(-1.0, Math.min(1.0, value)) * 32767.0); }
    private interface Factory { short[] create(); }
}
