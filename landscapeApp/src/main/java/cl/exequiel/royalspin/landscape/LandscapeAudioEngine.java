package cl.exequiel.royalspin.landscape;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Locally synthesized, license-free game audio with symbol-specific signatures. */
public final class LandscapeAudioEngine {
    public enum Cue { SPIN, STOP_1, STOP_2, STOP_3, STOP_4, STOP_5, SMALL_WIN, BELL, BAR, SEVEN, DIAMOND, WILD, FEATURE }

    private static final int SAMPLE_RATE = 44100;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<Cue, short[]> cache = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;
    private volatile boolean released;

    public void setEnabled(boolean value) { enabled = value; }
    public boolean isEnabled() { return enabled; }

    public void play(Cue cue) {
        if (!enabled || released) return;
        executor.execute(() -> {
            if (!enabled || released) return;
            short[] data = cache.computeIfAbsent(cue, this::synthesize);
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
                        .setBufferSizeInBytes(data.length * 2)
                        .build();
                track.write(data, 0, data.length);
                track.play();
                Thread.sleep(Math.max(45L, data.length * 1000L / SAMPLE_RATE + 20L));
            } catch (RuntimeException ignored) {
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                if (track != null) {
                    try { track.stop(); } catch (RuntimeException ignored) {}
                    track.release();
                }
            }
        });
    }

    public void release() {
        released = true;
        executor.shutdownNow();
        cache.clear();
    }

    private short[] synthesize(Cue cue) {
        switch (cue) {
            case SPIN: return sweep(0.48, 180, 720, 0.34, 0.12);
            case STOP_1: return impact(0.12, 190, 0.42);
            case STOP_2: return impact(0.12, 220, 0.45);
            case STOP_3: return impact(0.13, 250, 0.48);
            case STOP_4: return impact(0.14, 285, 0.52);
            case STOP_5: return impact(0.16, 330, 0.58);
            case BELL: return chord(0.75, new double[]{660, 990, 1320}, 0.44, true);
            case BAR: return metallic(0.52, 105, 0.62);
            case SEVEN: return layeredSweep(0.72, 280, 1450, 0.62);
            case DIAMOND: return chord(0.68, new double[]{880, 1320, 1760, 2217}, 0.38, false);
            case WILD: return chord(0.84, new double[]{392, 523.25, 659.25, 783.99}, 0.55, true);
            case FEATURE: return fanfare();
            case SMALL_WIN:
            default: return chord(0.42, new double[]{523.25, 659.25, 783.99}, 0.34, false);
        }
    }

    private short[] sweep(double seconds, double f0, double f1, double gain, double noiseGain) {
        int n = (int) (seconds * SAMPLE_RATE);
        short[] data = new short[n];
        long seed = 0x67ab91L;
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double x = i / (double) Math.max(1, n - 1);
            double freq = f0 * Math.pow(f1 / f0, x);
            phase += 2 * Math.PI * freq / SAMPLE_RATE;
            seed = seed * 1664525L + 1013904223L;
            double noise = (((seed >>> 16) & 0xffff) / 32768.0 - 1.0);
            double env = Math.sin(Math.PI * x) * Math.pow(1 - x, 0.25);
            double sample = (Math.sin(phase) * gain + noise * noiseGain) * env;
            data[i] = pcm(sample);
        }
        return data;
    }

    private short[] impact(double seconds, double frequency, double gain) {
        int n = (int) (seconds * SAMPLE_RATE);
        short[] data = new short[n];
        long seed = 0x5512acL;
        for (int i = 0; i < n; i++) {
            double x = i / (double) n;
            seed = seed * 1103515245L + 12345L;
            double noise = (((seed >>> 15) & 0xffff) / 32768.0 - 1.0);
            double env = Math.exp(-8.5 * x);
            double body = Math.sin(2 * Math.PI * frequency * i / SAMPLE_RATE)
                    + 0.45 * Math.sin(2 * Math.PI * frequency * 2.04 * i / SAMPLE_RATE);
            data[i] = pcm((body * 0.38 + noise * 0.28) * env * gain);
        }
        return data;
    }

    private short[] chord(double seconds, double[] frequencies, double gain, boolean bellEnvelope) {
        int n = (int) (seconds * SAMPLE_RATE);
        short[] data = new short[n];
        for (int i = 0; i < n; i++) {
            double x = i / (double) n;
            double attack = Math.min(1.0, x * 32);
            double decay = bellEnvelope ? Math.exp(-4.2 * x) : Math.sin(Math.PI * Math.min(1, x));
            double sample = 0;
            for (int k = 0; k < frequencies.length; k++) {
                double f = frequencies[k];
                sample += Math.sin(2 * Math.PI * f * i / SAMPLE_RATE) / (k + 1.0);
                sample += 0.22 * Math.sin(2 * Math.PI * f * 2.01 * i / SAMPLE_RATE) / (k + 1.0);
            }
            sample /= frequencies.length;
            data[i] = pcm(sample * attack * decay * gain);
        }
        return data;
    }

    private short[] metallic(double seconds, double frequency, double gain) {
        int n = (int) (seconds * SAMPLE_RATE);
        short[] data = new short[n];
        long seed = 91;
        for (int i = 0; i < n; i++) {
            double x = i / (double) n;
            seed = seed * 48271L % 2147483647L;
            double noise = seed / 1073741824.0 - 1.0;
            double body = Math.sin(2 * Math.PI * frequency * i / SAMPLE_RATE)
                    + 0.5 * Math.sin(2 * Math.PI * frequency * 3.17 * i / SAMPLE_RATE)
                    + 0.25 * Math.sin(2 * Math.PI * frequency * 7.3 * i / SAMPLE_RATE);
            double env = Math.exp(-7 * x);
            data[i] = pcm((body * 0.25 + noise * 0.25) * env * gain);
        }
        return data;
    }

    private short[] layeredSweep(double seconds, double f0, double f1, double gain) {
        int n = (int) (seconds * SAMPLE_RATE);
        short[] data = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double x = i / (double) n;
            double freq = f0 + (f1 - f0) * x * x;
            phase += 2 * Math.PI * freq / SAMPLE_RATE;
            double env = Math.sin(Math.PI * x) * (0.65 + 0.35 * Math.sin(Math.PI * x));
            double sample = Math.sin(phase) + 0.42 * Math.sin(phase * 1.498) + 0.2 * Math.sin(phase * 2.01);
            data[i] = pcm(sample * env * gain / 1.62);
        }
        return data;
    }

    private short[] fanfare() {
        double seconds = 1.18;
        int n = (int) (seconds * SAMPLE_RATE);
        short[] data = new short[n];
        double[] notes = {392, 523.25, 659.25, 783.99};
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            int step = Math.min(notes.length - 1, (int) (t / 0.22));
            double local = (t - step * 0.22) / 0.22;
            double env = Math.min(1, local * 12) * Math.exp(-2.4 * Math.max(0, local));
            double f = notes[step];
            double sample = Math.sin(2 * Math.PI * f * t)
                    + 0.45 * Math.sin(2 * Math.PI * f * 2 * t)
                    + 0.2 * Math.sin(2 * Math.PI * f * 3 * t);
            double finale = t > 0.88 ? Math.sin(Math.PI * (t - 0.88) / 0.30) : 0;
            sample += finale * (Math.sin(2 * Math.PI * 523.25 * t)
                    + Math.sin(2 * Math.PI * 659.25 * t)
                    + Math.sin(2 * Math.PI * 783.99 * t)) * 0.32;
            data[i] = pcm(sample * env * 0.42);
        }
        return data;
    }

    private static short pcm(double value) {
        double limited = Math.tanh(value * 1.25);
        return (short) Math.round(Math.max(-1, Math.min(1, limited)) * 32767);
    }
}
