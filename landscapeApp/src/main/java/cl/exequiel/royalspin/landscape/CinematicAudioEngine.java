package cl.exequiel.royalspin.landscape;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * License-free procedural audio. Multiple short voices may overlap so reel stops,
 * anticipation and win accents remain synchronized instead of cutting each other off.
 */
public final class CinematicAudioEngine {
    public enum Cue {
        BUTTON,
        SPIN_START,
        STOP_1,
        STOP_2,
        STOP_3,
        STOP_4,
        STOP_5,
        ANTICIPATION,
        SMALL_WIN,
        BIG_WIN,
        MEGA_WIN,
        FEATURE,
        BELL,
        BAR,
        SEVEN,
        DIAMOND,
        WILD
    }

    private static final int SAMPLE_RATE = 44100;
    private final ExecutorService voices = Executors.newFixedThreadPool(5, runnable -> {
        Thread thread = new Thread(runnable, "royal-audio-voice");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final Map<Cue, short[]> cache = new EnumMap<>(Cue.class);
    private final AtomicBoolean released = new AtomicBoolean(false);
    private volatile boolean enabled = true;
    private volatile float masterVolume = 0.78f;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setMasterVolume(float volume) {
        masterVolume = Math.max(0f, Math.min(1f, volume));
    }

    public void play(Cue cue) {
        if (!enabled || released.get()) return;
        voices.execute(() -> playInternal(cue));
    }

    public void playStop(int reel) {
        Cue[] stops = {Cue.STOP_1, Cue.STOP_2, Cue.STOP_3, Cue.STOP_4, Cue.STOP_5};
        play(stops[Math.max(0, Math.min(stops.length - 1, reel))]);
    }

    public void release() {
        if (!released.compareAndSet(false, true)) return;
        voices.shutdownNow();
        synchronized (cache) {
            cache.clear();
        }
    }

    private void playInternal(Cue cue) {
        if (!enabled || released.get()) return;
        short[] pcm;
        synchronized (cache) {
            pcm = cache.get(cue);
            if (pcm == null) {
                pcm = synthesize(cue);
                cache.put(cue, pcm);
            }
        }

        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.length * 2)
                    .build();
            track.write(pcm, 0, pcm.length);
            track.setVolume(masterVolume);
            track.play();
            Thread.sleep(Math.max(50L, pcm.length * 1000L / SAMPLE_RATE + 25L));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ignored) {
            // Audio must never be able to terminate the game on vendor-specific devices.
        } finally {
            if (track != null) {
                try { track.stop(); } catch (RuntimeException ignored) {}
                try { track.release(); } catch (RuntimeException ignored) {}
            }
        }
    }

    private short[] synthesize(Cue cue) {
        switch (cue) {
            case BUTTON: return clickSpark();
            case SPIN_START: return spinLaunch();
            case STOP_1: return reelImpact(0, 145.0, 0.54);
            case STOP_2: return reelImpact(1, 168.0, 0.57);
            case STOP_3: return reelImpact(2, 194.0, 0.60);
            case STOP_4: return reelImpact(3, 224.0, 0.65);
            case STOP_5: return reelImpact(4, 262.0, 0.76);
            case ANTICIPATION: return anticipationRise();
            case BIG_WIN: return winFanfare(false);
            case MEGA_WIN: return winFanfare(true);
            case FEATURE: return featureFanfare();
            case BELL: return bellSignature();
            case BAR: return barSignature();
            case SEVEN: return sevenSignature();
            case DIAMOND: return diamondSignature();
            case WILD: return wildSignature();
            case SMALL_WIN:
            default: return smallWin();
        }
    }

    private short[] clickSpark() {
        return render(0.11, (t, x, seed) -> {
            double env = Math.exp(-30.0 * x);
            double sparkle = Math.sin(2 * Math.PI * 1500 * t)
                    + 0.45 * Math.sin(2 * Math.PI * 2600 * t);
            return sparkle * env * 0.24;
        });
    }

    private short[] spinLaunch() {
        return render(0.78, (t, x, seed) -> {
            double f = 92.0 * Math.pow(9.2, x);
            double phase = 2 * Math.PI * f * t;
            double body = Math.sin(phase) + 0.35 * Math.sin(phase * 0.503);
            double noise = noise(seed) * (0.20 + 0.12 * x);
            double attack = smooth(0.0, 0.08, x);
            double release = 1.0 - smooth(0.62, 1.0, x);
            double sub = Math.sin(2 * Math.PI * (58 + 38 * x) * t) * Math.exp(-3.2 * x);
            return (body * 0.22 + noise + sub * 0.28) * attack * release;
        });
    }

    private short[] reelImpact(int index, double frequency, double gain) {
        return render(index == 4 ? 0.34 : 0.23, (t, x, seed) -> {
            double env = Math.exp(-(10.0 + index * 0.55) * x);
            double metal = Math.sin(2 * Math.PI * frequency * t)
                    + 0.48 * Math.sin(2 * Math.PI * frequency * 2.09 * t)
                    + 0.19 * Math.sin(2 * Math.PI * frequency * 4.72 * t);
            double transientNoise = noise(seed) * Math.exp(-34 * x);
            double sub = Math.sin(2 * Math.PI * (68 + index * 9) * t) * Math.exp(-7 * x);
            double tail = index == 4
                    ? 0.18 * Math.sin(2 * Math.PI * 680 * t) * Math.exp(-5 * x)
                    : 0.0;
            return (metal * 0.24 + transientNoise * 0.42 + sub * 0.34 + tail) * env * gain;
        });
    }

    private short[] anticipationRise() {
        return render(1.12, (t, x, seed) -> {
            double f = 240 + 1180 * x * x;
            double tremolo = 0.58 + 0.42 * Math.sin(2 * Math.PI * (5.5 + x * 5.0) * t);
            double tone = Math.sin(2 * Math.PI * f * t)
                    + 0.42 * Math.sin(2 * Math.PI * f * 1.5 * t);
            double air = noise(seed) * 0.08;
            double env = smooth(0.0, 0.15, x) * (1.0 - smooth(0.88, 1.0, x));
            return (tone * 0.25 * tremolo + air) * env;
        });
    }

    private short[] smallWin() {
        return arpeggio(new double[]{523.25, 659.25, 783.99}, 0.55, 0.20, 0.36);
    }

    private short[] winFanfare(boolean mega) {
        double[] notes = mega
                ? new double[]{392.0, 523.25, 659.25, 783.99, 1046.5}
                : new double[]{440.0, 554.37, 659.25, 880.0};
        double seconds = mega ? 1.78 : 1.22;
        return render(seconds, (t, x, seed) -> {
            double stepDuration = mega ? 0.25 : 0.22;
            int step = Math.min(notes.length - 1, (int) (t / stepDuration));
            double local = (t - step * stepDuration) / stepDuration;
            double f = notes[step];
            double env = Math.min(1.0, local * 18.0) * Math.exp(-2.8 * Math.max(0.0, local));
            double brass = Math.sin(2 * Math.PI * f * t)
                    + 0.52 * Math.sin(2 * Math.PI * f * 2 * t)
                    + 0.20 * Math.sin(2 * Math.PI * f * 3 * t);
            double shimmer = Math.sin(2 * Math.PI * f * 4.02 * t) * 0.12;
            double finale = x > 0.72 ? Math.sin(Math.PI * (x - 0.72) / 0.28) : 0.0;
            double chord = finale * (Math.sin(2 * Math.PI * 523.25 * t)
                    + Math.sin(2 * Math.PI * 659.25 * t)
                    + Math.sin(2 * Math.PI * 783.99 * t)) * 0.20;
            return (brass * 0.28 + shimmer + chord) * env;
        });
    }

    private short[] featureFanfare() {
        return render(2.05, (t, x, seed) -> {
            double[] notes = {329.63, 392.0, 523.25, 659.25, 783.99};
            int step = Math.min(notes.length - 1, (int) (t / 0.29));
            double local = (t - step * 0.29) / 0.29;
            double f = notes[step];
            double env = Math.min(1.0, local * 16.0) * Math.exp(-2.0 * local);
            double lead = Math.sin(2 * Math.PI * f * t)
                    + 0.45 * Math.sin(2 * Math.PI * f * 2.0 * t)
                    + 0.16 * Math.sin(2 * Math.PI * f * 3.0 * t);
            double choir = x > 0.58
                    ? (Math.sin(2 * Math.PI * 523.25 * t) + Math.sin(2 * Math.PI * 659.25 * t)) * 0.18
                    : 0.0;
            double sparkle = noise(seed) * 0.035 * smooth(0.45, 0.95, x);
            return (lead * 0.31 + choir + sparkle) * env;
        });
    }

    private short[] bellSignature() {
        return harmonicChord(new double[]{660, 990, 1320, 1980}, 0.95, 4.6, 0.36);
    }

    private short[] barSignature() {
        return render(0.68, (t, x, seed) -> {
            double env = Math.exp(-6.5 * x);
            double body = Math.sin(2 * Math.PI * 118 * t)
                    + 0.52 * Math.sin(2 * Math.PI * 372 * t)
                    + 0.30 * Math.sin(2 * Math.PI * 861 * t);
            return (body * 0.30 + noise(seed) * 0.12) * env;
        });
    }

    private short[] sevenSignature() {
        return arpeggio(new double[]{392.0, 587.33, 783.99, 1174.66}, 0.92, 0.18, 0.42);
    }

    private short[] diamondSignature() {
        return harmonicChord(new double[]{880, 1320, 1760, 2217.46}, 0.86, 3.8, 0.30);
    }

    private short[] wildSignature() {
        return harmonicChord(new double[]{392, 523.25, 659.25, 783.99, 1046.5}, 1.08, 3.1, 0.38);
    }

    private short[] arpeggio(double[] notes, double seconds, double stepDuration, double gain) {
        return render(seconds, (t, x, seed) -> {
            int step = Math.min(notes.length - 1, (int) (t / stepDuration));
            double local = (t - step * stepDuration) / stepDuration;
            double env = Math.min(1.0, local * 20.0) * Math.exp(-3.8 * local);
            double f = notes[step];
            double tone = Math.sin(2 * Math.PI * f * t)
                    + 0.32 * Math.sin(2 * Math.PI * f * 2.01 * t);
            return tone * env * gain;
        });
    }

    private short[] harmonicChord(double[] frequencies, double seconds, double decay, double gain) {
        return render(seconds, (t, x, seed) -> {
            double attack = Math.min(1.0, x * 40.0);
            double env = attack * Math.exp(-decay * x);
            double sample = 0.0;
            for (int i = 0; i < frequencies.length; i++) {
                double f = frequencies[i];
                sample += Math.sin(2 * Math.PI * f * t) / (i + 1.0);
                sample += 0.16 * Math.sin(2 * Math.PI * f * 2.01 * t) / (i + 1.0);
            }
            return sample * env * gain / frequencies.length;
        });
    }

    private short[] render(double seconds, SampleGenerator generator) {
        int count = Math.max(1, (int) Math.round(seconds * SAMPLE_RATE));
        short[] data = new short[count];
        long seed = 0x524f59414c535049L;
        for (int i = 0; i < count; i++) {
            double t = i / (double) SAMPLE_RATE;
            double x = i / (double) Math.max(1, count - 1);
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            data[i] = pcm(generator.sample(t, x, seed));
        }
        return data;
    }

    private static double noise(long seed) {
        return (((seed >>> 32) & 0xffffL) / 32768.0) - 1.0;
    }

    private static double smooth(double edge0, double edge1, double value) {
        if (edge1 <= edge0) return value >= edge1 ? 1.0 : 0.0;
        double t = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    private static short pcm(double value) {
        double limited = Math.tanh(value * 1.22);
        return (short) Math.round(Math.max(-1.0, Math.min(1.0, limited)) * 32767.0);
    }

    private interface SampleGenerator {
        double sample(double timeSeconds, double normalized, long seed);
    }
}
