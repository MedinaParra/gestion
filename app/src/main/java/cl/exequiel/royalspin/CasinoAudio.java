package cl.exequiel.royalspin;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

/**
 * Synthesized three-bus audio layer. It keeps the prototype self-contained while allowing
 * interface, reel and reward sounds to overlap without external licensing risk.
 */
public final class CasinoAudio {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneGenerator interfaceBus;
    private ToneGenerator reelBus;
    private ToneGenerator rewardBus;
    private boolean enabled;

    public CasinoAudio(boolean enabled) {
        this.enabled = enabled;
        interfaceBus = new ToneGenerator(AudioManager.STREAM_MUSIC, 58);
        reelBus = new ToneGenerator(AudioManager.STREAM_MUSIC, 66);
        rewardBus = new ToneGenerator(AudioManager.STREAM_MUSIC, 78);
    }

    public void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) stopAll();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void playTap() {
        beep(interfaceBus, ToneGenerator.TONE_PROP_BEEP2, 45, 0);
    }

    public void playError() {
        beep(interfaceBus, ToneGenerator.TONE_PROP_NACK, 165, 0);
    }

    public void playSpinStart() {
        beep(reelBus, ToneGenerator.TONE_PROP_PROMPT, 85, 0);
        beep(reelBus, ToneGenerator.TONE_DTMF_2, 70, 105);
        beep(reelBus, ToneGenerator.TONE_DTMF_5, 70, 205);
        beep(reelBus, ToneGenerator.TONE_DTMF_8, 90, 305);
    }

    public void playReelStop(int reel) {
        int[] tones = {
                ToneGenerator.TONE_DTMF_1,
                ToneGenerator.TONE_DTMF_2,
                ToneGenerator.TONE_DTMF_3,
                ToneGenerator.TONE_DTMF_6,
                ToneGenerator.TONE_DTMF_9
        };
        int index = Math.max(0, Math.min(tones.length - 1, reel));
        beep(reelBus, tones[index], 72 + index * 3, 0);
        beep(interfaceBus, ToneGenerator.TONE_PROP_BEEP2, 32, 22);
    }

    public void playLineAccent(int lineIndex) {
        int[] accents = {
                ToneGenerator.TONE_DTMF_3,
                ToneGenerator.TONE_DTMF_6,
                ToneGenerator.TONE_DTMF_9
        };
        beep(rewardBus, accents[Math.floorMod(lineIndex, accents.length)], 62, 0);
    }

    public void playCountTick(int step) {
        int tone = step % 3 == 0 ? ToneGenerator.TONE_DTMF_9 : ToneGenerator.TONE_DTMF_6;
        beep(rewardBus, tone, 35, 0);
    }

    public void playLose() {
        beep(rewardBus, ToneGenerator.TONE_PROP_BEEP, 75, 0);
        beep(rewardBus, ToneGenerator.TONE_PROP_NACK, 105, 105);
    }

    public void playWin(double multiplier) {
        final int[] tones;
        final int duration;
        final int spacing;
        if (multiplier >= 20d) {
            tones = new int[]{ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_3,
                    ToneGenerator.TONE_DTMF_6, ToneGenerator.TONE_DTMF_9,
                    ToneGenerator.TONE_PROP_ACK, ToneGenerator.TONE_PROP_PROMPT};
            duration = 112;
            spacing = 128;
        } else if (multiplier >= 5d) {
            tones = new int[]{ToneGenerator.TONE_DTMF_3, ToneGenerator.TONE_DTMF_6,
                    ToneGenerator.TONE_DTMF_9, ToneGenerator.TONE_PROP_ACK};
            duration = 98;
            spacing = 116;
        } else {
            tones = new int[]{ToneGenerator.TONE_DTMF_6, ToneGenerator.TONE_PROP_ACK};
            duration = 82;
            spacing = 100;
        }
        int delay = 0;
        for (int tone : tones) {
            beep(rewardBus, tone, duration, delay);
            delay += spacing;
        }
    }

    private void beep(ToneGenerator generator, int tone, int durationMs, int delayMs) {
        if (!enabled || generator == null) return;
        handler.postDelayed(() -> {
            if (enabled && generator != null) generator.startTone(tone, durationMs);
        }, Math.max(0, delayMs));
    }

    private void stopAll() {
        handler.removeCallbacksAndMessages(null);
        if (interfaceBus != null) interfaceBus.stopTone();
        if (reelBus != null) reelBus.stopTone();
        if (rewardBus != null) rewardBus.stopTone();
    }

    public void release() {
        stopAll();
        ToneGenerator ui = interfaceBus;
        ToneGenerator reels = reelBus;
        ToneGenerator rewards = rewardBus;
        interfaceBus = null;
        reelBus = null;
        rewardBus = null;
        if (ui != null) ui.release();
        if (reels != null) reels.release();
        if (rewards != null) rewards.release();
    }
}
