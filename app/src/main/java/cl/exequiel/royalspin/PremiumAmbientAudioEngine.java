package cl.exequiel.royalspin;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/** Generated low-level ambience and anticipation cues. No external audio assets are used. */
public final class PremiumAmbientAudioEngine {
    private static final int SAMPLE_RATE = 22_050;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AudioTrack> transients = new ArrayList<>();
    private AudioTrack ambientTrack;
    private boolean enabled;
    private boolean featureMode;
    private boolean reducedMode;
    private boolean suspended;
    private float master = .52f;

    public void setEnabled(boolean value) {
        if (enabled == value) return;
        enabled = value;
        if (!enabled) stopAll();
    }

    public void setMaster(float value) {
        master = clamp(value, 0f, 1f);
        updateAmbientVolume();
    }

    public void updateBed(boolean feature, boolean reduced) {
        featureMode = feature;
        reducedMode = reduced;
        if (!enabled || suspended) return;
        if (ambientTrack == null) startAmbient(feature, reduced);
        else if (ambientTag != tag(feature, reduced)) {
            stopAmbient();
            startAmbient(feature, reduced);
        } else updateAmbientVolume();
    }

    private int ambientTag = Integer.MIN_VALUE;

    private static int tag(boolean feature, boolean reduced) {
        return (feature ? 2 : 0) | (reduced ? 1 : 0);
    }

    public void playModeTransition(boolean feature) {
        if (!enabled || suspended) return;
        if (feature) {
            play(0, sweep(780, 130, 760, .13, .03, .72), .85f);
            play(160, chord(920, 196, new double[]{1.0, 1.25, 1.5, 2.0}, .09), .80f);
        } else {
            play(0, sweep(520, 620, 170, .09, .02, .68), .72f);
            play(240, chord(620, 146.83, new double[]{1.0, 1.5, 2.0}, .07), .68f);
        }
    }

    public void playAnticipationPulse(int stage) {
        if (!enabled || suspended) return;
        int safe = Math.max(0, Math.min(3, stage));
        double root = 92 + safe * 24;
        play(0, cue(120, root, .20 + safe * .025, .00, .50, .28), .90f);
        play(16, noise(52, .08 + safe * .015, .00, .38), .75f);
        play(45, cue(72, 680 + safe * 150, .08 + safe * .012, .00, .64, .12), .82f);
    }

    public void playIdleSignature(boolean feature) {
        if (!enabled || suspended || reducedMode) return;
        double root = feature ? 392.0 : 329.63;
        play(0, cue(180, root, .055, .02, .58, .20), .62f);
        play(115, cue(220, root * 1.5, .045, .02, .62, .18), .58f);
        if (feature) play(235, cue(260, root * 2.0, .038, .02, .65, .15), .55f);
    }

    public void suspend() {
        suspended = true;
        stopAll();
    }

    public void resume() {
        if (!suspended) return;
        suspended = false;
        if (enabled) startAmbient(featureMode, reducedMode);
    }

    public void release() {
        suspended = true;
        stopAll();
    }

    private void startAmbient(boolean feature, boolean reduced) {
        if (!enabled || suspended) return;
        short[] pcm = ambient(feature, reduced);
        AudioTrack track = buildTrack(pcm.length, AudioAttributes.CONTENT_TYPE_MUSIC);
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            return;
        }
        track.write(pcm, 0, pcm.length);
        track.setLoopPoints(0, pcm.length, -1);
        ambientTrack = track;
        ambientTag = tag(feature, reduced);
        updateAmbientVolume();
        track.play();
    }

    private void updateAmbientVolume() {
        if (ambientTrack == null) return;
        float level = master * (reducedMode ? .035f : featureMode ? .105f : .072f);
        try { ambientTrack.setVolume(level); }
        catch (RuntimeException ignored) { }
    }

    private void stopAmbient() {
        AudioTrack track = ambientTrack;
        ambientTrack = null;
        ambientTag = Integer.MIN_VALUE;
        if (track == null) return;
        try { track.stop(); } catch (RuntimeException ignored) { }
        track.release();
    }

    private void stopAll() {
        handler.removeCallbacksAndMessages(null);
        stopAmbient();
        List<AudioTrack> copy;
        synchronized (transients) {
            copy = new ArrayList<>(transients);
            transients.clear();
        }
        for (AudioTrack track : copy) {
            try { track.stop(); } catch (RuntimeException ignored) { }
            track.release();
        }
    }

    private void play(int delayMs, short[] pcm, float gain) {
        if (!enabled || suspended || pcm == null || pcm.length == 0) return;
        handler.postDelayed(() -> playNow(pcm, gain), Math.max(0, delayMs));
    }

    private void playNow(short[] pcm, float gain) {
        if (!enabled || suspended) return;
        AudioTrack track = buildTrack(pcm.length, AudioAttributes.CONTENT_TYPE_SONIFICATION);
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            return;
        }
        track.setVolume(master * clamp(gain, 0f, 1f));
        track.write(pcm, 0, pcm.length);
        synchronized (transients) { transients.add(track); }
        track.play();
        long duration = pcm.length * 1000L / SAMPLE_RATE + 220L;
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
        try { track.stop(); } catch (RuntimeException ignored) { }
        track.release();
    }

    private short[] ambient(boolean feature, boolean reduced) {
        int durationMs = feature ? 3600 : 3200;
        int count = samples(durationMs);
        short[] pcm = new short[count];
        double root = feature ? 73.42 : 55.0;
        long noiseState = feature ? 0x71EAF00DL : 0x51A7E5L;
        double filtered = 0d;
        for (int i = 0; i < count; i++) {
            double t = i / (double) SAMPLE_RATE;
            double progress = i / (double) Math.max(1, count - 1);
            double fade = Math.sin(Math.PI * progress);
            double tremolo = .72 + .28 * Math.sin(2d * Math.PI * t / (feature ? 1.8 : 2.4));
            double tone = Math.sin(2d * Math.PI * root * t)
                    + .36 * Math.sin(2d * Math.PI * root * 1.5 * t)
                    + .18 * Math.sin(2d * Math.PI * root * 2.0 * t);
            noiseState = noiseState * 6364136223846793005L + 1442695040888963407L;
            double white = (((noiseState >>> 40) & 0xFFFF) / 32767.5) - 1.0;
            filtered = filtered * .94 + white * .06;
            double volume = reduced ? .018 : feature ? .052 : .038;
            pcm[i] = sample((tone / 1.54 + filtered * .12) * volume * tremolo * fade);
        }
        return pcm;
    }

    private short[] cue(int ms, double frequency, double volume,
                        double attack, double releaseStart, double harmonic) {
        int count = samples(ms);
        short[] pcm = new short[count];
        for (int i = 0; i < count; i++) {
            double t = i / (double) SAMPLE_RATE;
            double progress = i / (double) Math.max(1, count - 1);
            double value = Math.sin(2d * Math.PI * frequency * t)
                    + harmonic * Math.sin(2d * Math.PI * frequency * 2.01 * t);
            pcm[i] = sample(value * volume * envelope(progress, attack, releaseStart)
                    / (1d + harmonic));
        }
        return pcm;
    }

    private short[] chord(int ms, double root, double[] ratios, double volume) {
        int count = samples(ms);
        short[] pcm = new short[count];
        for (int i = 0; i < count; i++) {
            double t = i / (double) SAMPLE_RATE;
            double progress = i / (double) Math.max(1, count - 1);
            double value = 0d;
            for (double ratio : ratios) value += Math.sin(2d * Math.PI * root * ratio * t);
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
            double value = Math.sin(phase) + .22 * Math.sin(phase * 2.02);
            pcm[i] = sample(value * volume * envelope(progress, attack, releaseStart) / 1.22);
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
