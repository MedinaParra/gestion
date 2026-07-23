package cl.exequiel.royalspin;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

/** Three independent synthesized buses: UI, mechanics and rewards. */
public final class CasinoAudio {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneGenerator ui;
    private ToneGenerator mechanics;
    private ToneGenerator rewards;
    private boolean enabled;

    public CasinoAudio(boolean enabled, int ignoredFeatureLevel) {
        this.enabled = enabled;
        ui = new ToneGenerator(AudioManager.STREAM_MUSIC, 54);
        mechanics = new ToneGenerator(AudioManager.STREAM_MUSIC, 68);
        rewards = new ToneGenerator(AudioManager.STREAM_MUSIC, 78);
    }

    public void setEnabled(boolean value) {
        enabled = value;
        if (!enabled) stopRewardSequence();
    }

    public boolean isEnabled() { return enabled; }
    public void playTap() { tone(ui, ToneGenerator.TONE_PROP_BEEP2, 45, 0); }
    public void playError() { tone(ui, ToneGenerator.TONE_PROP_NACK, 165, 0); }

    public void playSpinStart() {
        tone(mechanics, ToneGenerator.TONE_DTMF_1, 75, 0);
        tone(mechanics, ToneGenerator.TONE_DTMF_2, 76, 90);
        tone(mechanics, ToneGenerator.TONE_DTMF_5, 92, 185);
        tone(ui, ToneGenerator.TONE_PROP_PROMPT, 125, 18);
        tone(rewards, ToneGenerator.TONE_DTMF_8, 90, 275);
        tone(mechanics, ToneGenerator.TONE_DTMF_0, 180, 365);
    }

    public void playReelStop(int reel) {
        int[] tones = {ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_2,
                ToneGenerator.TONE_DTMF_3, ToneGenerator.TONE_DTMF_6,
                ToneGenerator.TONE_DTMF_9};
        int index = Math.max(0, Math.min(tones.length - 1, reel));
        tone(mechanics, tones[index], 68 + reel * 6, 0);
        tone(ui, reel >= 3 ? ToneGenerator.TONE_PROP_BEEP2 : ToneGenerator.TONE_PROP_BEEP,
                38 + reel * 2, 22);
        if (reel == 4) tone(rewards, ToneGenerator.TONE_DTMF_8, 82, 45);
    }

    public void playLose() {
        tone(ui, ToneGenerator.TONE_PROP_BEEP, 78, 0);
        tone(mechanics, ToneGenerator.TONE_PROP_NACK, 105, 102);
    }

    public void playWin(double multiplier) {
        playSymbolWin("", 0, multiplier);
    }

    public void playSymbolWin(String symbol, int count, double multiplier) {
        stopRewardSequence();
        if (StakeSlotEngine.BELL.equals(symbol)) {
            bellSequence(count, multiplier);
        } else if (StakeSlotEngine.BAR.equals(symbol)) {
            barSequence(count, multiplier);
        } else if (StakeSlotEngine.SEVEN.equals(symbol)) {
            sevenSequence(count, multiplier);
        } else if (StakeSlotEngine.DIAMOND.equals(symbol)) {
            diamondSequence(count, multiplier);
        } else if (StakeSlotEngine.WILD.equals(symbol)) {
            wildSequence(count, multiplier);
        } else {
            genericWin(multiplier);
        }
    }

    private void bellSequence(int count, double multiplier) {
        int repeats = Math.max(3, Math.min(5, count));
        for (int i = 0; i < repeats; i++) {
            tone(rewards, i % 2 == 0 ? ToneGenerator.TONE_DTMF_6 : ToneGenerator.TONE_DTMF_9,
                    128, i * 145);
            tone(ui, ToneGenerator.TONE_PROP_BEEP2, 42, i * 145 + 30);
        }
        tone(rewards, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 280, repeats * 145 + 35);
        if (multiplier >= 25d) tone(mechanics, ToneGenerator.TONE_DTMF_0, 650, 70);
    }

    private void barSequence(int count, double multiplier) {
        for (int i = 0; i < Math.max(3, count); i++) {
            tone(mechanics, i % 2 == 0 ? ToneGenerator.TONE_DTMF_1 : ToneGenerator.TONE_DTMF_4,
                    92, i * 118);
            tone(ui, ToneGenerator.TONE_PROP_ACK, 50, i * 118 + 40);
        }
        tone(mechanics, ToneGenerator.TONE_DTMF_0, multiplier >= 25d ? 720 : 420, 35);
        tone(rewards, ToneGenerator.TONE_PROP_PROMPT, 180, 520);
    }

    private void sevenSequence(int count, double multiplier) {
        int[] rise = {ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_3,
                ToneGenerator.TONE_DTMF_6, ToneGenerator.TONE_DTMF_9,
                ToneGenerator.TONE_SUP_CONFIRM};
        for (int i = 0; i < rise.length; i++) {
            tone(rewards, rise[i], 104, i * 95);
        }
        tone(mechanics, ToneGenerator.TONE_DTMF_5, multiplier >= 25d ? 760 : 430, 18);
        tone(ui, ToneGenerator.TONE_PROP_ACK, 170, 470);
        if (count >= 5) tone(rewards, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 260, 650);
    }

    private void diamondSequence(int count, double multiplier) {
        int[] prism = {ToneGenerator.TONE_DTMF_3, ToneGenerator.TONE_DTMF_6,
                ToneGenerator.TONE_DTMF_9, ToneGenerator.TONE_PROP_ACK};
        for (int i = 0; i < prism.length; i++) {
            tone(rewards, prism[i], 85 + i * 8, i * 108);
        }
        tone(ui, ToneGenerator.TONE_PROP_BEEP2, 62, 55);
        if (count >= 5 || multiplier >= 25d) {
            tone(rewards, ToneGenerator.TONE_SUP_CONFIRM, 220, 470);
        }
    }

    private void wildSequence(int count, double multiplier) {
        int[] crown = {ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_3,
                ToneGenerator.TONE_DTMF_6, ToneGenerator.TONE_DTMF_9,
                ToneGenerator.TONE_PROP_ACK, ToneGenerator.TONE_SUP_CONFIRM};
        for (int i = 0; i < crown.length; i++) {
            tone(rewards, crown[i], 118, i * 120);
        }
        tone(mechanics, ToneGenerator.TONE_DTMF_0, multiplier >= 100d ? 1100 : 720, 35);
        tone(ui, ToneGenerator.TONE_PROP_PROMPT, 240, 520);
        if (count >= 5) tone(rewards, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 320, 780);
    }

    private void genericWin(double multiplier) {
        if (multiplier >= 20) {
            int[] melody = {ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_3,
                    ToneGenerator.TONE_DTMF_6, ToneGenerator.TONE_DTMF_9,
                    ToneGenerator.TONE_PROP_ACK, ToneGenerator.TONE_SUP_CONFIRM};
            for (int i = 0; i < melody.length; i++) tone(rewards, melody[i], 112, i * 120);
            tone(mechanics, ToneGenerator.TONE_DTMF_5, 620, 35);
            tone(ui, ToneGenerator.TONE_PROP_PROMPT, 220, 500);
            tone(rewards, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 240, 745);
        } else if (multiplier >= 5) {
            int[] melody = {ToneGenerator.TONE_DTMF_3, ToneGenerator.TONE_DTMF_6,
                    ToneGenerator.TONE_DTMF_9, ToneGenerator.TONE_PROP_ACK};
            for (int i = 0; i < melody.length; i++) tone(rewards, melody[i], 98, i * 110);
            tone(mechanics, ToneGenerator.TONE_DTMF_2, 350, 25);
        } else {
            tone(rewards, ToneGenerator.TONE_DTMF_6, 88, 0);
            tone(rewards, ToneGenerator.TONE_PROP_ACK, 108, 116);
            tone(ui, ToneGenerator.TONE_PROP_BEEP2, 46, 42);
        }
    }

    public void stopRewardSequence() {
        handler.removeCallbacksAndMessages(null);
        stop(ui); stop(mechanics); stop(rewards);
    }

    private void tone(ToneGenerator generator, int tone, int durationMs, int delayMs) {
        if (!enabled || generator == null) return;
        handler.postDelayed(() -> {
            if (enabled && generator != null) generator.startTone(tone, durationMs);
        }, delayMs);
    }

    private static void stop(ToneGenerator generator) {
        if (generator != null) generator.stopTone();
    }

    public void release() {
        stopRewardSequence();
        release(ui); release(mechanics); release(rewards);
        ui = mechanics = rewards = null;
    }

    private static void release(ToneGenerator generator) {
        if (generator != null) generator.release();
    }
}
