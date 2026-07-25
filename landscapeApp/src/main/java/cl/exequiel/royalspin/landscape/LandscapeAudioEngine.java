package cl.exequiel.royalspin.landscape;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Locally synthesized, license-free, polyphonic game audio. */
public final class LandscapeAudioEngine {
    public enum Cue {
        SPIN, STOP_1, STOP_2, STOP_3, STOP_4, STOP_5,
        SMALL_WIN, BELL, BAR, SEVEN, DIAMOND, WILD, FEATURE
    }

    private static final int SAMPLE_RATE = 44100;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private final Map<Cue, short[]> cache = new ConcurrentHashMap<>();
    private volatile boolean enabled = true;
    private volatile boolean released;

    public void setEnabled(boolean value) { enabled = value; }
    public boolean isEnabled() { return enabled; }

    public void play(Cue cue) {
        if (!enabled || released) return;
        executor.execute(() -> playBuffer(cache.computeIfAbsent(cue, this::synthesize)));
    }

    private void playBuffer(short[] data) {
        if (!enabled || released || data == null || data.length == 0) return;
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
            Thread.sleep(Math.max(55L, data.length * 1000L / SAMPLE_RATE + 30L));
        } catch (RuntimeException ignored) {
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            if (track != null) {
                try { track.stop(); } catch (RuntimeException ignored) {}
                track.release();
            }
        }
    }

    public void release() {
        released = true;
        executor.shutdownNow();
        cache.clear();
    }

    private short[] synthesize(Cue cue) {
        switch (cue) {
            case SPIN:
                return mix(
                        sweep(0.82, 115, 980, 0.48, 0.13),
                        turbine(0.82, 0.34),
                        subDrop(0.28, 105, 48, 0.34));
            case STOP_1: return reelStop(185, 0.50, 0);
            case STOP_2: return reelStop(215, 0.54, 1);
            case STOP_3: return reelStop(248, 0.58, 2);
            case STOP_4: return reelStop(286, 0.63, 3);
            case STOP_5: return reelStop(342, 0.72, 4);
            case BELL:
                return mix(chord(0.94, new double[]{660, 990, 1320}, 0.54, true),
                        sparkle(0.72, 1320, 0.26));
            case BAR:
                return mix(metallic(0.68, 92, 0.72), impact(0.19, 155, 0.42));
            case SEVEN:
                return mix(layeredSweep(0.96, 240, 1780, 0.72),
                        chord(0.66, new double[]{523.25, 783.99, 1046.5}, 0.30, false));
            case DIAMOND:
                return mix(chord(0.92, new double[]{880, 1320, 1760, 2217}, 0.50, false),
                        sparkle(0.84, 1760, 0.32));
            case WILD:
                return mix(chord(1.12, new double[]{392, 523.25, 659.25, 783.99}, 0.66, true),
                        layeredSweep(0.88, 310, 1320, 0.38));
            case FEATURE:
                return featureFanfare();
            case SMALL_WIN:
            default:
                return mix(chord(0.58, new double[]{523.25, 659.25, 783.99}, 0.45, false),
                        sparkle(0.40, 1046.5, 0.20));
        }
    }

    private short[] reelStop(double frequency, double gain, int index) {
        return mix(
                impact(0.18 + index * .012, frequency, gain),
                click(0.08, 1800 + index * 160, 0.24 + index * .025),
                index == 4 ? subDrop(0.30, 92, 44, 0.38) : new short[0]);
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
            double attack = Math.min(1.0, x * 16.0);
            double release = Math.pow(1.0 - x, .32);
            double sample = (Math.sin(phase) * gain + noise * noiseGain) * attack * release;
            data[i] = pcm(sample);
        }
        return data;
    }

    private short[] turbine(double seconds, double gain) {
        int n = (int) (seconds * SAMPLE_RATE);
        short[] data = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double x = i / (double) Math.max(1, n - 1);
            double freq = 72 + 205 * x + 24 * Math.sin(x * Math.PI * 4);
            phase += 2 * Math.PI * freq / SAMPLE_RATE;
            double env = Math.min(1, x * 12) * Math.pow(1 - x, .22);
            double sample = Math.sin(phase) + .38 * Math.sin(phase * 2.01)
                    + .16 * Math.sin(phase * 3.98);
            data[i] = pcm(sample * env * gain / 1.45);
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
            double env = Math.exp(-9.4 * x);
            double pitch = frequency * (1.0 - .18 * x);
            double body = Math.sin(2 * Math.PI * pitch * i / SAMPLE_RATE)
                    + .46 * Math.sin(2 * Math.PI * pitch * 2.04 * i / SAMPLE_RATE);
            data[i] = pcm((body * .42 + noise * .30) * env * gain);
        }
        return data;
    }

    private short[] click(double seconds, double frequency, double gain) {
        int n = (int) (seconds * SAMPLE_RATE);
        short[] data = new short[n];
        for (int i = 0; i < n; i++) {
            double x = i / (double) n;
            double env = Math.exp(-18 * x);
            data[i] = pcm(Math.sin(2 * Math.PI * frequency * i / SAMPLE_RATE) * env * gain);
        }
        return data;
    }

    private short[] subDrop(double seconds, double f0, double f1, double gain) {
        int n = (int) (seconds * SAMPLE_RATE);
        short[] data = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double x = i / (double) Math.max(1, n - 1);
            double freq = f0 + (f1 - f0) * x;
            phase += 2 * Math.PI * freq / SAMPLE_RATE;
            double env = Math.sin(Math.PI * x) * Math.pow(1 - x, .30);
            data[i] = pcm(Math.sin(phase) * env * gain);
        }
        return data;
    }

    private short[] chord(double seconds, double[] frequencies, double gain, boolean bellEnvelope) {
        int n = (int) (seconds * SAMPLE_RATE);
        short[] data = new short[n];
        for (int i = 0; i < n; i++) {
            double x = i / (double) n;
            double attack = Math.min(1.0, x * 34);
            double decay = bellEnvelope ? Math.exp(-3.8 * x)
                    : Math.sin(Math.PI * Math.min(1, x));
            double sample = 0;
            for (int k = 0; k < frequencies.length; k++) {
                double f = frequencies[k];
                sample += Math.sin(2 * Math.PI * f * i / SAMPLE_RATE) / (k + 1.0);
                sample += .20 * Math.sin(2 * Math.PI * f * 2.01 * i / SAMPLE_RATE) / (k + 1.0);
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
                    + .5 * Math.sin(2 * Math.PI * frequency * 3.17 * i / SAMPLE_RATE)
                    + .25 * Math.sin(2 * Math.PI * frequency * 7.3 * i / SAMPLE_RATE);
            double env = Math.exp(-6.6 * x);
            data[i] = pcm((body * .27 + noise * .23) * env * gain);
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
            double env = Math.sin(Math.PI * x) * (.65 + .35 * Math.sin(Math.PI * x));
            double sample = Math.sin(phase) + .42 * Math.sin(phase * 1.498)
                    + .2 * Math.sin(phase * 2.01);
            data[i] = pcm(sample * env * gain / 1.62);
        }
        return data;
    }

    private short[] sparkle(double seconds, double baseFrequency, double gain) {
        int n = (int) (seconds * SAMPLE_RATE);
        short[] data = new short[n];
        double[] ratios = {1.0, 1.25, 1.5, 2.0};
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            int step = Math.min(ratios.length - 1,
                    (int) (t / Math.max(.05, seconds / ratios.length)));
            double local = (t - step * seconds / ratios.length) /
                    Math.max(.001, seconds / ratios.length);
            double env = Math.min(1, local * 20) * Math.exp(-5.4 * Math.max(0, local));
            double f = baseFrequency * ratios[step];
            data[i] = pcm((Math.sin(2 * Math.PI * f * t)
                    + .24 * Math.sin(2 * Math.PI * f * 2.02 * t)) * env * gain);
        }
        return data;
    }

    private short[] featureFanfare() {
        double seconds = 1.65;
        int n = (int) (seconds * SAMPLE_RATE);
        short[] data = new short[n];
        double[] notes = {392, 523.25, 659.25, 783.99, 1046.5};
        for (int i = 0; i < n; i++) {
            double t = i / (double) SAMPLE_RATE;
            int step = Math.min(notes.length - 1, (int) (t / .23));
            double local = (t - step * .23) / .23;
            double env = Math.min(1, local * 13) * Math.exp(-2.2 * Math.max(0, local));
            double f = notes[step];
            double sample = Math.sin(2 * Math.PI * f * t)
                    + .48 * Math.sin(2 * Math.PI * f * 2 * t)
                    + .22 * Math.sin(2 * Math.PI * f * 3 * t);
            if (t > 1.02) {
                double finale = Math.sin(Math.PI * (t - 1.02) / .63);
                sample += finale * (Math.sin(2 * Math.PI * 523.25 * t)
                        + Math.sin(2 * Math.PI * 659.25 * t)
                        + Math.sin(2 * Math.PI * 783.99 * t)) * .38;
            }
            data[i] = pcm(sample * env * .50);
        }
        return mix(data, subDrop(0.52, 96, 42, .42), sparkle(1.28, 1046.5, .23));
    }

    private static short[] mix(short[]... sources) {
        int length = 0;
        for (short[] source : sources) {
            if (source != null) length = Math.max(length, source.length);
        }
        short[] result = new short[length];
        for (int i = 0; i < length; i++) {
            double sum = 0;
            int active = 0;
            for (short[] source : sources) {
                if (source != null && i < source.length) {
                    sum += source[i] / 32768.0;
                    active++;
                }
            }
            if (active > 0) result[i] = pcm(sum * .82);
        }
        return result;
    }

    private static short pcm(double value) {
        double limited = Math.tanh(value * 1.22);
        return (short) Math.round(Math.max(-1, Math.min(1, limited)) * 32767);
    }
}
