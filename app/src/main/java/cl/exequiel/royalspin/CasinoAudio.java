package cl.exequiel.royalspin;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

/** Lightweight synthesized effects so the demo has no external audio licensing risk. */
public final class CasinoAudio {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneGenerator toneGenerator;
    private boolean enabled;

    public CasinoAudio(boolean enabled) {
        this.enabled = enabled;
        toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 72);
    }

    public void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) {
            handler.removeCallbacksAndMessages(null);
            if (toneGenerator != null) toneGenerator.stopTone();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void playTap() {
        beep(ToneGenerator.TONE_PROP_BEEP2, 55, 0);
    }

    public void playError() {
        beep(ToneGenerator.TONE_PROP_NACK, 160, 0);
    }

    public void playSpinStart() {
        beep(ToneGenerator.TONE_PROP_PROMPT, 100, 0);
        beep(ToneGenerator.TONE_DTMF_2, 75, 130);
        beep(ToneGenerator.TONE_DTMF_5, 75, 235);
    }

    public void playReelStop(int reel) {
        int[] tones = {
                ToneGenerator.TONE_DTMF_1,
                ToneGenerator.TONE_DTMF_2,
                ToneGenerator.TONE_DTMF_3,
                ToneGenerator.TONE_DTMF_6,
                ToneGenerator.TONE_DTMF_9
        };
        beep(tones[Math.max(0, Math.min(tones.length - 1, reel))], 75, 0);
    }

    public void playLose() {
        beep(ToneGenerator.TONE_PROP_BEEP, 90, 0);
        beep(ToneGenerator.TONE_PROP_NACK, 110, 120);
    }

    public void playWin(double multiplier) {
        int delay = 0;
        int[] tones = multiplier >= 10
                ? new int[]{ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_3,
                ToneGenerator.TONE_DTMF_6, ToneGenerator.TONE_DTMF_9,
                ToneGenerator.TONE_PROP_ACK}
                : new int[]{ToneGenerator.TONE_DTMF_3, ToneGenerator.TONE_DTMF_6,
                ToneGenerator.TONE_PROP_ACK};
        for (int tone : tones) {
            beep(tone, 95, delay);
            delay += 115;
        }
    }

    private void beep(int tone, int durationMs, int delayMs) {
        if (!enabled) return;
        handler.postDelayed(() -> {
            ToneGenerator generator = toneGenerator;
            if (enabled && generator != null) {
                generator.startTone(tone, durationMs);
            }
        }, delayMs);
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
        ToneGenerator generator = toneGenerator;
        toneGenerator = null;
        if (generator != null) generator.release();
    }
}
