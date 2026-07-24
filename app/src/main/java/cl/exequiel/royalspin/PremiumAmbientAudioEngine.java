package cl.exequiel.royalspin;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Generated ambience synthesized off the UI thread. No external audio assets are used. */
public final class PremiumAmbientAudioEngine {
    private static final int SAMPLE_RATE = 16_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService synthesis = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "RoyalSpinAudioSynthesis");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final Map<String, short[]> cache = new ConcurrentHashMap<>();
    private final List<AudioTrack> transients = new ArrayList<>();
    private final AtomicInteger lifecycleGeneration = new AtomicInteger();
    private final AtomicInteger ambientRequest = new AtomicInteger();

    private AudioTrack ambientTrack;
    private boolean enabled;
    private boolean featureMode;
    private boolean reducedMode;
    private boolean suspended;
    private boolean released;
    private float master = .48f;
    private int ambientTag = Integer.MIN_VALUE;
    private int requestedAmbientTag = Integer.MIN_VALUE;

    public void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        if (!enabled) stopAll();
        else if (!suspended) requestAmbient(featureMode, reducedMode);
    }

    public void setMaster(float value) {
        master = clamp(value, 0f, 1f);
        updateAmbientVolume();
    }

    public void updateBed(boolean feature, boolean reduced) {
        featureMode = feature;
        reducedMode = reduced;
        if (!enabled || suspended || released) return;
        int desired = tag(feature, reduced);
        if (ambientTag == desired) {
            updateAmbientVolume();
            return;
        }
        if (requestedAmbientTag != desired) requestAmbient(feature, reduced);
    }

    public void playModeTransition(boolean feature) {
        if (!available()) return;
        String key = feature ? "transition-feature" : "transition-base";
        synthesizeAndPlay(key, 0, .74f, () -> feature
                ? mix(sweep(560, 130, 720, .11, .03, .72),
                        shifted(chord(700, 196, new double[]{1.0, 1.25, 1.5, 2.0}, .075), 120))
                : mix(sweep(420, 580, 170, .075, .02, .68),
                        shifted(chord(480, 146.83, new double[]{1.0, 1.5, 2.0}, .055), 170)));
    }

    public void playAnticipationPulse(int stage) {
        if (!available()) return;
        int safe = Math.max(0, Math.min(3, stage));
        String key = "anticipation-" + safe;
        synthesizeAndPlay(key, 0, .77f, () -> {
            double root = 92 + safe * 24;
            return mix(cue(105, root, .16 + safe * .018, .00, .50, .26),
                    shifted(noise(45, .055 + safe * .010, .00, .38), 10),
                    shifted(cue(62, 650 + safe * 140, .06 + safe * .009,
                            .00, .64, .11), 34));
        });
    }

    public void playIdleSignature(boolean feature) {
        if (!available() || reducedMode) return;
        String key = feature ? "idle-feature" : "idle-base";
        synthesizeAndPlay(key, 0, .48f, () -> {
            double root = feature ? 392.0 : 329.63;
            short[] first = cue(150, root, .040, .02, .58, .18);
            short[] second = shifted(cue(180, root * 1.5, .033, .02, .62, .15), 92);
            if (!feature) return mix(first, second);
            return mix(first, second,
                    shifted(cue(210, root * 2.0, .027, .02, .65, .13), 190));
        });
    }

    public void suspend() {
        suspended = true;
        stopAll();
    }

    public void resume() {
        if (released) return;
        suspended = false;
        if (enabled) requestAmbient(featureMode, reducedMode);
    }

    public void release() {
        released = true;
        suspended = true;
        stopAll();
        synthesis.shutdownNow();
        cache.clear();
    }

    private boolean available() {
        return enabled && !suspended && !released;
    }

    private void requestAmbient(boolean feature, boolean reduced) {
        if (!available()) return;
        int desiredTag = tag(feature, reduced);
        requestedAmbientTag = desiredTag;
        int request = ambientRequest.incrementAndGet();
        int generation = lifecycleGeneration.get();
        String key = "ambient-" + desiredTag;
        synthesis.execute(() -> {
            short[] pcm = cache.computeIfAbsent(key, ignored -> ambient(feature, reduced));
            handler.post(() -> {
                if (!available() || generation != lifecycleGeneration.get()
                        || request != ambientRequest.get() || desiredTag != requestedAmbientTag) return;
                startAmbientPrepared(pcm, desiredTag);
            });
        });
    }

    private void startAmbientPrepared(short[] pcm, int desiredTag) {
        stopAmbientTrack(false);
        AudioTrack track = buildTrack(pcm.length, AudioAttributes.CONTENT_TYPE_MUSIC);
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            requestedAmbientTag = Integer.MIN_VALUE;
            return;
        }
        int written = track.write(pcm, 0, pcm.length);
        if (written <= 0) {
            track.release();
            requestedAmbientTag = Integer.MIN_VALUE;
            return;
        }
        track.setLoopPoints(0, pcm.length, -1);
        ambientTrack = track;
        ambientTag = desiredTag;
        requestedAmbientTag = desiredTag;
        updateAmbientVolume();
        track.play();
    }

    private void synthesizeAndPlay(String key, int delayMs, float gain, Supplier<short[]> factory) {
        if (!available()) return;
        int generation = lifecycleGeneration.get();
        synthesis.execute(() -> {
            short[] pcm = cache.computeIfAbsent(key, ignored -> factory.get());
            handler.postDelayed(() -> {
                if (available() && generation == lifecycleGeneration.get()) playNow(pcm, gain);
            }, Math.max(0, delayMs));
        });
    }

    private void updateAmbientVolume() {
        AudioTrack track = ambientTrack;
        if (track == null) return;
        float level = master * (reducedMode ? .018f : featureMode ? .075f : .046f);
        try { track.setVolume(level); }
        catch (RuntimeException ignored) { }
    }

    private void stopAll() {
        lifecycleGeneration.incrementAndGet();
        ambientRequest.incrementAndGet();
        requestedAmbientTag = Integer.MIN_VALUE;
        handler.removeCallbacksAndMessages(null);
        stopAmbientTrack(true);
        List<AudioTrack> copy;
        synchronized (transients) {
            copy = new ArrayList<>(transients);
            transients.clear();
        }
        for (AudioTrack track : copy) releaseTrack(track);
    }

    private void stopAmbientTrack(boolean resetRequest) {
        AudioTrack track = ambientTrack;
        ambientTrack = null;
        ambientTag = Integer.MIN_VALUE;
        if (resetRequest) requestedAmbientTag = Integer.MIN_VALUE;
        if (track != null) releaseTrack(track);
    }

    private void playNow(short[] pcm, float gain) {
        if (!available() || pcm == null || pcm.length == 0) return;
        AudioTrack track = buildTrack(pcm.length, AudioAttributes.CONTENT_TYPE_SONIFICATION);
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            return;
        }
        track.setVolume(master * clamp(gain, 0f, 1f));
        if (track.write(pcm, 0, pcm.length) <= 0) {
            track.release();
            return;
        }
        synchronized (transients) { transients.add(track); }
        track.play();
        long duration = pcm.length * 1000L / SAMPLE_RATE + 180L;
        handler.postDelayed(() -> releaseTransient(track), duration);
    }

    private AudioTrack buildTrack(int samples, int contentType) {
        return new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(contentType)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(Math.max(2, samples * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
    }

    private void releaseTransient(AudioTrack track) {
        if (track == null) return;
        synchronized (transients) {
            if (!transients.remove(track)) return;
        }
        releaseTrack(track);
    }

    private static void releaseTrack(AudioTrack track) {
        try { track.stop(); } catch (RuntimeException ignored) { }
        track.release();
    }

    private short[] ambient(boolean feature, boolean reduced) {
        int durationMs = feature ? 1800 : 1600;
        int count = samples(durationMs);
        short[] pcm = new short[count];
        double root = feature ? 73.42 : 55.0;
        long noiseState = feature ? 0x71EAF00DL : 0x51A7E5L;
        double filtered = 0d;
        double phaseA = 0d, phaseB = 0d, phaseC = 0d;
        double stepA = 2d * Math.PI * root / SAMPLE_RATE;
        double stepB = 2d * Math.PI * root * 1.5 / SAMPLE_RATE;
        double stepC = 2d * Math.PI * root * 2.0 / SAMPLE_RATE;
        for (int i = 0; i < count; i++) {
            double progress = i / (double) Math.max(1, count - 1);
            double fade = Math.sin(Math.PI * progress);
            double tremolo = .76 + .24 * Math.sin(2d * Math.PI * progress * (feature ? 1.0 : .72));
            phaseA += stepA; phaseB += stepB; phaseC += stepC;
            double tone = Math.sin(phaseA) + .34 * Math.sin(phaseB) + .16 * Math.sin(phaseC);
            noiseState = noiseState * 6364136223846793005L + 1442695040888963407L;
            double white = (((noiseState >>> 40) & 0xFFFF) / 32767.5) - 1.0;
            filtered = filtered * .95 + white * .05;
            double volume = reduced ? .010 : feature ? .036 : .025;
            pcm[i] = sample((tone / 1.5 + filtered * .09) * volume * tremolo * fade);
        }
        return pcm;
    }

    private short[] cue(int ms, double frequency, double volume,
                        double attack, double releaseStart, double harmonic) {
        int count = samples(ms);
        short[] pcm = new short[count];
        double phase = 0d;
        double phase2 = 0d;
        double step = 2d * Math.PI * frequency / SAMPLE_RATE;
        double step2 = 2d * Math.PI * frequency * 2.01 / SAMPLE_RATE;
        for (int i = 0; i < count; i++) {
            phase += step; phase2 += step2;
            double progress = i / (double) Math.max(1, count - 1);
            double value = Math.sin(phase) + harmonic * Math.sin(phase2);
            pcm[i] = sample(value * volume * envelope(progress, attack, releaseStart)
                    / (1d + harmonic));
        }
        return pcm;
    }

    private short[] chord(int ms, double root, double[] ratios, double volume) {
        int count = samples(ms);
        short[] pcm = new short[count];
        double[] phases = new double[ratios.length];
        double[] steps = new double[ratios.length];
        for (int i = 0; i < ratios.length; i++) steps[i] = 2d * Math.PI * root * ratios[i] / SAMPLE_RATE;
        for (int i = 0; i < count; i++) {
            double progress = i / (double) Math.max(1, count - 1);
            double value = 0d;
            for (int note = 0; note < ratios.length; note++) {
                phases[note] += steps[note];
                value += Math.sin(phases[note]);
            }
            pcm[i] = sample(value * volume * envelope(progress, .03, .64) / ratios.length);
        }
        return pcm;
    }

    private short[] sweep(int ms, double from, double to, double volume,
                          double attack, double releaseStart) {
        int count = samples(ms);
        short[] pcm = new short[count];
        double phase = 0d;
        for (int i = 0; i < count; i++) {
            double progress = i / (double) Math.max(1, count - 1);
            double frequency = from + (to - from) * progress * progress;
            phase += 2d * Math.PI * frequency / SAMPLE_RATE;
            double value = Math.sin(phase) + .20 * Math.sin(phase * 2.02);
            pcm[i] = sample(value * volume * envelope(progress, attack, releaseStart) / 1.20);
        }
        return pcm;
    }

    private short[] noise(int ms, double volume, double attack, double releaseStart) {
        int count = samples(ms);
        short[] pcm = new short[count];
        long state = 0xA11CE55L;
        double filtered = 0d;
        for (int i = 0; i < count; i++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            double white = (((state >>> 40) & 0xFFFF) / 32767.5) - 1.0;
            filtered = filtered * .72 + white * .28;
            double progress = i / (double) Math.max(1, count - 1);
            pcm[i] = sample(filtered * volume * envelope(progress, attack, releaseStart));
        }
        return pcm;
    }

    private short[] shifted(short[] source, int delayMs) {
        int offset = samples(delayMs);
        short[] result = new short[offset + source.length];
        System.arraycopy(source, 0, result, offset, source.length);
        return result;
    }

    private short[] mix(short[]... layers) {
        int length = 0;
        for (short[] layer : layers) if (layer != null) length = Math.max(length, layer.length);
        short[] result = new short[length];
        for (int i = 0; i < length; i++) {
            int sum = 0;
            int activeLayers = 0;
            for (short[] layer : layers) {
                if (layer != null && i < layer.length) {
                    sum += layer[i];
                    activeLayers++;
                }
            }
            if (activeLayers > 1) sum /= activeLayers;
            result[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sum));
        }
        return result;
    }

    private static int tag(boolean feature, boolean reduced) {
        return (feature ? 2 : 0) | (reduced ? 1 : 0);
    }

    private static int samples(int ms) {
        return Math.max(1, SAMPLE_RATE * ms / 1000);
    }

    private static double envelope(double progress, double attack, double releaseStart) {
        double in = attack <= 0d ? 1d : Math.min(1d, progress / attack);
        double out = progress <= releaseStart ? 1d
                : Math.max(0d, 1d - (progress - releaseStart)
                / Math.max(.001d, 1d - releaseStart));
        return in * out * out;
    }

    private static short sample(double value) {
        return (short) Math.round(clamp((float) value, -1f, 1f) * 32767f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
