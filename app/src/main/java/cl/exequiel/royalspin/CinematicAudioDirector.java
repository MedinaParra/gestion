package cl.exequiel.royalspin;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * Low-latency generated PCM cues. No external audio licence is required and each symbol
 * receives a distinct layered identity. This class never affects game outcomes.
 */
public final class CinematicAudioDirector {
    private static final int SAMPLE_RATE = 44_100;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<AudioTrack> active = new ArrayList<>();
    private boolean enabled;
    private float master = 0.82f;

    public CinematicAudioDirector(boolean enabled) {
        this.enabled = enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) stopAll();
    }

    public boolean isEnabled() { return enabled; }
    public void setMaster(float value) { master = clamp(value, 0f, 1f); }

    public void playTap() {
        play(0, cue(55, 840, 0.22, 0.01, 0.75, 0.18));
        play(18, cue(42, 1260, 0.10, 0.00, 0.85, 0.12));
    }

    public void playError() {
        play(0, sweep(180, 310, 105, 0.28, 0.02, 0.55));
        play(70, sweep(220, 190, 78, 0.24, 0.01, 0.55));
    }

    public void playSpinStart() {
        play(0, sweep(360, 95, 460, 0.26, 0.03, 0.72));
        play(35, noise(230, 0.10, 0.03, 0.65));
        play(210, sweep(280, 240, 880, 0.18, 0.02, 0.75));
    }

    public void playReelStop(int reel) {
        int safe = Math.max(0, Math.min(4, reel));
        double base = 115 + safe * 18;
        play(0, cue(95, base, 0.40, 0.00, 0.36, 0.34));
        play(4, noise(62, 0.18, 0.00, 0.40));
        play(22, cue(70, 760 + safe * 80, 0.14, 0.00, 0.65, 0.16));
        if (safe == 4) play(58, sweep(145, 330, 720, 0.15, 0.01, 0.70));
    }

    public void playLose() {
        play(0, cue(100, 330, 0.13, 0.01, 0.45, 0.22));
        play(90, sweep(210, 280, 145, 0.13, 0.01, 0.48));
    }

    public void playSymbolWin(String symbol, int count, double multiplier) {
        stopRewardLayers();
        int strength = multiplier >= 50 ? 3 : multiplier >= 15 ? 2 : multiplier >= 5 ? 1 : 0;
        if (StakeSlotEngine.BELL.equals(symbol)) bell(count, strength);
        else if (StakeSlotEngine.BAR.equals(symbol)) bar(count, strength);
        else if (StakeSlotEngine.SEVEN.equals(symbol)) seven(count, strength);
        else if (StakeSlotEngine.DIAMOND.equals(symbol)) diamond(count, strength);
        else if (StakeSlotEngine.WILD.equals(symbol)) wild(count, strength);
        else generic(strength);
    }

    public void playFeatureIntro(int wildCount) {
        stopRewardLayers();
        play(0, sweep(1250, 90, 1160, 0.20, 0.08, 0.72));
        play(0, cue(820, 55, 0.35, 0.02, 0.50, 0.45));
        for (int i = 0; i < Math.max(3, wildCount); i++) {
            play(220 + i * 180, chord(180, 420 + i * 70,
                    new double[]{1.0, 1.5, 2.0}, 0.18 + i * 0.018));
        }
        play(1180, noise(170, 0.30, 0.00, 0.45));
        play(1190, chord(1250, 220, new double[]{1.0, 1.25, 1.5, 2.0}, 0.26));
        for (int i = 0; i < 10; i++) {
            play(1550 + i * 75, cue(55, 760 + i * 65, 0.10, 0.00, 0.70, 0.12));
        }
    }

    public void playRetrigger(int added) {
        stopRewardLayers();
        play(0, sweep(650, 180, 980, 0.22, 0.03, 0.75));
        play(430, chord(720, 260, new double[]{1.0, 1.25, 1.5, 2.0}, 0.25));
        for (int i = 0; i < Math.min(10, Math.max(3, added)); i++) {
            play(520 + i * 65, cue(48, 840 + i * 45, 0.09, 0.00, 0.72, 0.10));
        }
    }

    public void playFeatureSummary(double multiplier) {
        stopRewardLayers();
        play(0, cue(900, 82, 0.32, 0.01, 0.45, 0.40));
        play(80, sweep(1150, 170, 900, 0.20, 0.04, 0.72));
        double root = multiplier >= 50 ? 293.66 : multiplier >= 15 ? 261.63 : 220.0;
        int[] delays = {250, 480, 710, 980};
        double[] ratios = {1.0, 1.25, 1.5, 2.0};
        for (int i = 0; i < delays.length; i++) {
            play(delays[i], chord(700, root * ratios[i], new double[]{1.0, 1.25, 1.5}, 0.20));
        }
    }

    private void bell(int count, int strength) {
        int notes = Math.max(3, Math.min(5, count));
        for (int i = 0; i < notes; i++) {
            double root = 620 + i * 72;
            play(i * 150, metallic(520 + strength * 120, root, 0.22));
        }
        play(notes * 150, chord(620, 440, new double[]{1.0, 1.25, 1.5, 2.0}, 0.18));
    }

    private void bar(int count, int strength) {
        for (int i = 0; i < Math.max(3, count); i++) {
            play(i * 125, cue(95, 115 + i * 18, 0.42, 0.00, 0.33, 0.42));
            play(i * 125 + 12, noise(75, 0.17, 0.00, 0.42));
        }
        play(460, sweep(310 + strength * 120, 145, 510, 0.20, 0.01, 0.62));
    }

    private void seven(int count, int strength) {
        play(0, sweep(470, 190, 1320, 0.21, 0.02, 0.72));
        play(250, noise(120, 0.24, 0.00, 0.38));
        play(270, cue(440 + strength * 120, 72, 0.43, 0.00, 0.35, 0.50));
        for (int i = 0; i < Math.max(3, count); i++) {
            play(340 + i * 95, cue(110, 520 + i * 105, 0.14, 0.00, 0.68, 0.16));
        }
    }

    private void diamond(int count, int strength) {
        int rays = Math.max(4, count + 1);
        for (int i = 0; i < rays; i++) {
            play(i * 85, chord(330, 720 + i * 90,
                    new double[]{1.0, 1.33, 2.0}, 0.10 + strength * 0.015));
        }
        play(420, sweep(520, 650, 1680, 0.12, 0.03, 0.72));
    }

    private void wild(int count, int strength) {
        play(0, cue(820, 58, 0.40, 0.01, 0.42, 0.48));
        play(40, sweep(900, 140, 1180, 0.20, 0.05, 0.78));
        for (int i = 0; i < Math.max(3, count); i++) {
            play(190 + i * 145, chord(420, 330 + i * 82,
                    new double[]{1.0, 1.25, 1.5, 2.0}, 0.17));
        }
        play(820, chord(900 + strength * 250, 220,
                new double[]{1.0, 1.25, 1.5, 2.0, 3.0}, 0.22));
    }

    private void generic(int strength) {
        play(0, sweep(300 + strength * 160, 420, 850, 0.15, 0.02, 0.70));
        play(180, chord(420, 440, new double[]{1.0, 1.25, 1.5}, 0.14));
    }

    private short[] cue(int ms, double frequency, double volume,
                        double attack, double releaseStart, double harmonic) {
        int count = samples(ms);
        short[] pcm = new short[count];
        for (int i = 0; i < count; i++) {
            double t = i / (double) SAMPLE_RATE;
            double progress = i / (double) Math.max(1, count - 1);
            double env = envelope(progress, attack, releaseStart);
            double value = Math.sin(2 * Math.PI * frequency * t)
                    + harmonic * Math.sin(2 * Math.PI * frequency * 2.01 * t);
            pcm[i] = sample(value * volume * env / (1.0 + harmonic));
        }
        return pcm;
    }

    private short[] chord(int ms, double root, double[] ratios, double volume) {
        int count = samples(ms);
        short[] pcm = new short[count];
        for (int i = 0; i < count; i++) {
            double t = i / (double) SAMPLE_RATE;
            double progress = i / (double) Math.max(1, count - 1);
            double value = 0;
            for (double ratio : ratios) value += Math.sin(2 * Math.PI * root * ratio * t);
            pcm[i] = sample(value * volume * envelope(progress, 0.03, 0.58) / ratios.length);
        }
        return pcm;
    }

    private short[] metallic(int ms, double root, double volume) {
        return chord(ms, root, new double[]{1.0, 1.41, 2.17, 2.83, 4.06}, volume);
    }

    private short[] sweep(int ms, double from, double to, double volume,
                          double attack, double releaseStart) {
        int count = samples(ms);
        short[] pcm = new short[count];
        double phase = 0;
        for (int i = 0; i < count; i++) {
            double progress = i / (double) Math.max(1, count - 1);
            double frequency = from + (to - from) * progress * progress;
            phase += 2 * Math.PI * frequency / SAMPLE_RATE;
            double value = Math.sin(phase) + 0.25 * Math.sin(phase * 2.03);
            pcm[i] = sample(value * volume * envelope(progress, attack, releaseStart) / 1.25);
        }
        return pcm;
    }

    private short[] noise(int ms, double volume, double attack, double releaseStart) {
        int count = samples(ms);
        short[] pcm = new short[count];
        long state = 0x51A7E5L;
        double filtered = 0;
        for (int i = 0; i < count; i++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            double white = (((state >>> 40) & 0xFFFF) / 32767.5) - 1.0;
            filtered = filtered * 0.72 + white * 0.28;
            double progress = i / (double) Math.max(1, count - 1);
            pcm[i] = sample(filtered * volume * envelope(progress, attack, releaseStart));
        }
        return pcm;
    }

    private void play(int delayMs, short[] pcm) {
        if (!enabled || pcm == null || pcm.length == 0) return;
        handler.postDelayed(() -> playNow(pcm), Math.max(0, delayMs));
    }

    private void playNow(short[] pcm) {
        if (!enabled) return;
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(pcm.length * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        track.setVolume(master);
        track.write(pcm, 0, pcm.length);
        synchronized (active) { active.add(track); }
        track.setNotificationMarkerPosition(pcm.length);
        track.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override public void onMarkerReached(AudioTrack audioTrack) { releaseTrack(audioTrack); }
            @Override public void onPeriodicNotification(AudioTrack audioTrack) { }
        }, handler);
        track.play();
        handler.postDelayed(() -> releaseTrack(track), pcm.length * 1000L / SAMPLE_RATE + 300L);
    }

    private void releaseTrack(AudioTrack track) {
        if (track == null) return;
        synchronized (active) {
            if (!active.remove(track)) return;
        }
        try { track.stop(); } catch (RuntimeException ignored) { }
        track.release();
    }

    private void stopRewardLayers() {
        handler.removeCallbacksAndMessages(null);
        stopTracks();
    }

    public void stopAll() {
        handler.removeCallbacksAndMessages(null);
        stopTracks();
    }

    private void stopTracks() {
        List<AudioTrack> copy;
        synchronized (active) {
            copy = new ArrayList<>(active);
            active.clear();
        }
        for (AudioTrack track : copy) {
            try { track.stop(); } catch (RuntimeException ignored) { }
            track.release();
        }
    }

    public void release() { stopAll(); }

    private static int samples(int ms) { return Math.max(1, SAMPLE_RATE * ms / 1000); }
    private static double envelope(double p, double attack, double releaseStart) {
        double in = attack <= 0 ? 1.0 : Math.min(1.0, p / attack);
        double out = p <= releaseStart ? 1.0
                : Math.max(0.0, 1.0 - (p - releaseStart) / Math.max(0.001, 1.0 - releaseStart));
        return in * out * out;
    }
    private static short sample(double value) {
        return (short) Math.round(clamp((float) value, -1f, 1f) * 32767f);
    }
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
