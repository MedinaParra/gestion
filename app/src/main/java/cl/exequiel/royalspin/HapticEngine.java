package cl.exequiel.royalspin;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Optional haptic layer with short, bounded patterns for gameplay events. */
public final class HapticEngine {
    private final Vibrator vibrator;
    private boolean enabled;

    public HapticEngine(Context context, boolean enabled) {
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        this.enabled = enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
        if (!enabled && vibrator != null) vibrator.cancel();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void tap() {
        oneShot(18L, 55);
    }

    public void error() {
        waveform(new long[]{0L, 55L, 45L, 90L}, new int[]{0, 115, 0, 165});
    }

    public void reelStop(int reel) {
        oneShot(20L + Math.max(0, reel) * 2L, Math.min(155, 70 + reel * 16));
    }

    public void win(double multiplier) {
        if (multiplier >= 20d) {
            waveform(new long[]{0L, 55L, 45L, 80L, 55L, 140L},
                    new int[]{0, 100, 0, 145, 0, 210});
        } else if (multiplier >= 5d) {
            waveform(new long[]{0L, 45L, 45L, 95L}, new int[]{0, 95, 0, 165});
        } else {
            oneShot(55L, 105);
        }
    }

    private void oneShot(long durationMs, int amplitude) {
        if (!canVibrate()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs,
                    Math.max(1, Math.min(255, amplitude))));
        } else {
            //noinspection deprecation
            vibrator.vibrate(durationMs);
        }
    }

    private void waveform(long[] timings, int[] amplitudes) {
        if (!canVibrate()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
        } else {
            //noinspection deprecation
            vibrator.vibrate(timings, -1);
        }
    }

    private boolean canVibrate() {
        return enabled && vibrator != null && vibrator.hasVibrator();
    }

    public void release() {
        if (vibrator != null) vibrator.cancel();
    }
}
