package cl.exequiel.royalspin;

import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

import java.lang.reflect.Field;

/**
 * Applies hysteresis to presentation-only overlays. Mathematical rendering and controls remain
 * active at all times; only decorative layers are suspended when sustained Lite mode is detected.
 */
public final class AdaptivePresentationGovernor implements Choreographer.FrameCallback {
    private static final long SAMPLE_INTERVAL_NANOS = 250_000_000L;
    private static final long LITE_CONFIRM_MS = 650L;
    private static final long RECOVERY_CONFIRM_MS = 2600L;

    private final RoyalSpinV2View gameView;
    private final View typographyOverlay;
    private final View symbolOverlay;
    private final Field qualityField;
    private final Field phaseField;
    private final Field reducedField;

    private boolean running;
    private boolean suspended;
    private boolean decorationSuspended;
    private long lastSampleNanos;
    private long liteSince;
    private long healthySince;

    public AdaptivePresentationGovernor(RoyalSpinV2View gameView,
                                        View typographyOverlay,
                                        View symbolOverlay) {
        this.gameView = gameView;
        this.typographyOverlay = typographyOverlay;
        this.symbolOverlay = symbolOverlay;
        qualityField = field("qualityTier");
        phaseField = field("phase");
        reducedField = field("reducedMotion");
    }

    private static Field field(String name) {
        try {
            Field field = RoyalSpinV2View.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public void start() {
        if (running) return;
        running = true;
        suspended = false;
        lastSampleNanos = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void suspend() {
        suspended = true;
        if (running) {
            running = false;
            Choreographer.getInstance().removeFrameCallback(this);
        }
    }

    public void resume() {
        suspended = false;
        if (!running) start();
    }

    public void release() {
        running = false;
        suspended = true;
        Choreographer.getInstance().removeFrameCallback(this);
        setDecorationsVisible(true);
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if (!suspended && (lastSampleNanos == 0L
                || frameTimeNanos - lastSampleNanos >= SAMPLE_INTERVAL_NANOS)) {
            lastSampleNanos = frameTimeNanos;
            evaluate(SystemClock.uptimeMillis());
        }
        if (running) Choreographer.getInstance().postFrameCallback(this);
    }

    private void evaluate(long now) {
        int quality = readInt(qualityField, 1);
        boolean reduced = readBoolean(reducedField, false);
        String phase = readString(phaseField, "IDLE");
        boolean intensivePhase = phase.endsWith("SPINNING") || phase.endsWith("REVEALING")
                || "FEATURE_INTRO".equals(phase) || "RETRIGGER".equals(phase)
                || "FEATURE_SUMMARY".equals(phase);

        boolean shouldProtect = reduced || (quality <= 0 && intensivePhase);
        if (shouldProtect) {
            healthySince = 0L;
            if (liteSince == 0L) liteSince = now;
            if (!decorationSuspended && now - liteSince >= LITE_CONFIRM_MS) {
                setDecorationsVisible(false);
            }
            return;
        }

        liteSince = 0L;
        if (!decorationSuspended) return;
        boolean safeRecoveryPhase = "IDLE".equals(phase) || "FEATURE_READY".equals(phase);
        if (quality >= 1 && safeRecoveryPhase) {
            if (healthySince == 0L) healthySince = now;
            if (now - healthySince >= RECOVERY_CONFIRM_MS) setDecorationsVisible(true);
        } else healthySince = 0L;
    }

    private void setDecorationsVisible(boolean visible) {
        decorationSuspended = !visible;
        int state = visible ? View.VISIBLE : View.INVISIBLE;
        if (typographyOverlay.getVisibility() != state) typographyOverlay.setVisibility(state);
        if (symbolOverlay.getVisibility() != state) symbolOverlay.setVisibility(state);
    }

    private Object readObject(Field field) {
        if (field == null) return null;
        try { return field.get(gameView); }
        catch (IllegalAccessException ignored) { return null; }
    }

    private int readInt(Field field, int fallback) {
        if (field == null) return fallback;
        try { return field.getInt(gameView); }
        catch (IllegalAccessException ignored) { return fallback; }
    }

    private boolean readBoolean(Field field, boolean fallback) {
        if (field == null) return fallback;
        try { return field.getBoolean(gameView); }
        catch (IllegalAccessException ignored) { return fallback; }
    }

    private String readString(Field field, String fallback) {
        Object value = readObject(field);
        return value == null ? fallback : String.valueOf(value);
    }
}
