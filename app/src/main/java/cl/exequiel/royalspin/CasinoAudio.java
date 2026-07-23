package cl.exequiel.royalspin;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

/** Self-contained three-bus synthesized audio layer. */
public final class CasinoAudio {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneGenerator ui;
    private ToneGenerator reels;
    private ToneGenerator rewards;
    private boolean enabled;
    private final int featureLevel;

    public CasinoAudio(boolean enabled, int featureLevel) {
        this.enabled = enabled;
        this.featureLevel = featureLevel;
        ui = new ToneGenerator(AudioManager.STREAM_MUSIC, 58);
        reels = new ToneGenerator(AudioManager.STREAM_MUSIC, 68);
        rewards = new ToneGenerator(AudioManager.STREAM_MUSIC, 76);
    }

    public void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) stopRewardSequence();
    }

    public boolean isEnabled() { return enabled; }

    public void playTap() { tone(ui, ToneGenerator.TONE_PROP_BEEP2, 48, 0); }
    public void playError() { tone(ui, ToneGenerator.TONE_PROP_NACK, 150, 0); }

    public void playSpinStart() {
        tone(reels, ToneGenerator.TONE_DTMF_1, 75, 0);
        tone(reels, ToneGenerator.TONE_DTMF_2, 75, 95);
        tone(reels, ToneGenerator.TONE_DTMF_5, 85, 190);
        if (featureLevel >= 6) {
            tone(ui, ToneGenerator.TONE_PROP_PROMPT, 120, 20);
            tone(rewards, ToneGenerator.TONE_DTMF_8, 90, 275);
        }
    }

    public void playReelStop(int reel) {
        int[] tones = {ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_2,
                ToneGenerator.TONE_DTMF_3, ToneGenerator.TONE_DTMF_6,
                ToneGenerator.TONE_DTMF_9};
        int index = Math.max(0, Math.min(tones.length - 1, reel));
        tone(reels, tones[index], 68 + reel * 5, 0);
        if (featureLevel >= 6 && reel >= 3) tone(ui, ToneGenerator.TONE_PROP_BEEP2, 42, 24);
    }

    public void playLose() {
        tone(ui, ToneGenerator.TONE_PROP_BEEP, 80, 0);
        tone(reels, ToneGenerator.TONE_PROP_NACK, 100, 105);
    }

    public void playWin(double multiplier) {
        stopRewardSequence();
        if (multiplier >= 20) {
            int[] seq = {ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_3,
                    ToneGenerator.TONE_DTMF_6, ToneGenerator.TONE_DTMF_9,
                    ToneGenerator.TONE_PROP_ACK, ToneGenerator.TONE_SUP_CONFIRM};
            for (int i = 0; i < seq.length; i++) tone(rewards, seq[i], 110, i * 122);
            if (featureLevel >= 6) {
                tone(reels, ToneGenerator.TONE_DTMF_5, 500, 40);
                tone(ui, ToneGenerator.TONE_PROP_PROMPT, 220, 520);
            }
        } else if (multiplier >= 5) {
            int[] seq = {ToneGenerator.TONE_DTMF_3, ToneGenerator.TONE_DTMF_6,
                    ToneGenerator.TONE_DTMF_9, ToneGenerator.TONE_PROP_ACK};
            for (int i = 0; i < seq.length; i++) tone(rewards, seq[i], 95, i * 112);
        } else {
            tone(rewards, ToneGenerator.TONE_DTMF_6, 90, 0);
            tone(rewards, ToneGenerator.TONE_PROP_ACK, 105, 118);
        }
    }

    public void stopRewardSequence() {
        handler.removeCallbacksAndMessages(null);
        stop(ui); stop(reels); stop(rewards);
    }

    private void tone(ToneGenerator generator, int tone, int durationMs, int delayMs) {
        if (!enabled || generator == null) return;
        handler.postDelayed(() -> {
            if (enabled && generator != null) generator.startTone(tone, durationMs);
        }, delayMs);
    }

    private void stop(ToneGenerator generator) {
        if (generator != null) generator.stopTone();
    }

    public void release() {
        stopRewardSequence();
        release(ui); release(reels); release(rewards);
        ui = reels = rewards = null;
    }

    private void release(ToneGenerator generator) {
        if (generator != null) generator.release();
    }
}
