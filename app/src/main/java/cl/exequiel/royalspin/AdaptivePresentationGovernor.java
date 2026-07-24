package cl.exequiel.royalspin;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

import java.lang.reflect.Field;

/**
 * Applies hysteresis to optional typography effects and a temporary runtime reduced-motion mode.
 * The Jewel Art renderer remains visible in every quality tier and only lowers its internal budget.
 */
public final class AdaptivePresentationGovernor implements Choreographer.FrameCallback {
    private static final long SAMPLE_INTERVAL_NANOS = 250_000_000L;
    private static final long LITE_CONFIRM_MS = 500L;
    private static final long RECOVERY_CONFIRM_MS = 1800L;
    private static final long RUNTIME_MOTION_RECOVERY_MS = 300L;

    private final RoyalSpinV2View gameView;
    private final View typographyOverlay;
    private final JewelArtFinalOverlay jewelArtOverlay;
    private final Field qualityField;
    private final Field phaseField;
    private final Field reducedField;
    private final Field prefsField;

    private boolean running;
    private boolean suspended;
    private boolean decorationSuspended;
    private boolean preferenceInitialized;
    private boolean userReducedMotion;
    private boolean automaticReducedMotion;
    private long lastSampleNanos;
    private long liteSince;
    private long healthySince;
    private long safePhaseSince;
    private String lastPhase = "";

    public AdaptivePresentationGovernor(RoyalSpinV2View gameView,
                                        View typographyOverlay,
                                        JewelArtFinalOverlay jewelArtOverlay) {
        this.gameView = gameView;
        this.typographyOverlay = typographyOverlay;
        this.jewelArtOverlay = jewelArtOverlay;
        qualityField = field("qualityTier");
        phaseField = field("phase");
        reducedField = field("reducedMotion");
        prefsField = field("prefs");
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
        restoreUserMotionPreference();
    }

    public void resume() {
        suspended = false;
        if (!running) start();
    }

    public void release() {
        running = false;
        suspended = true;
        Choreographer.getInstance().removeFrameCallback(this);
        restoreUserMotionPreference();
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
        boolean currentReduced = readBoolean(reducedField, false);
        String phase = readString(phaseField, "IDLE");
        boolean safePhase = "IDLE".equals(phase) || "FEATURE_READY".equals(phase);
        boolean intensivePhase = phase.endsWith("SPINNING") || phase.endsWith("REVEALING")
                || "FEATURE_INTRO".equals(phase) || "RETRIGGER".equals(phase)
                || "FEATURE_SUMMARY".equals(phase);

        if (!preferenceInitialized) {
            preferenceInitialized = true;
            userReducedMotion = currentReduced;
            lastPhase = phase;
        } else if (!automaticReducedMotion && safePhase && currentReduced != userReducedMotion) {
            userReducedMotion = currentReduced;
        }

        boolean liteIntensive = quality <= 0 && intensivePhase && !userReducedMotion;
        if (liteIntensive) {
            healthySince = 0L;
            safePhaseSince = 0L;
            if (liteSince == 0L) liteSince = now;
            if (now - liteSince >= LITE_CONFIRM_MS) {
                if (!automaticReducedMotion) enableAutomaticReducedMotion();
                if (!decorationSuspended) setDecorationsVisible(false);
            }
        } else {
            liteSince = 0L;
            if (safePhase) {
                if (safePhaseSince == 0L) safePhaseSince = now;
                if (automaticReducedMotion
                        && now - safePhaseSince >= RUNTIME_MOTION_RECOVERY_MS) {
                    restoreUserMotionPreference();
                }
            } else safePhaseSince = 0L;

            if (decorationSuspended && safePhase) {
                if (healthySince == 0L) healthySince = now;
                if (now - healthySince >= RECOVERY_CONFIRM_MS) setDecorationsVisible(true);
            } else if (!safePhase) healthySince = 0L;
        }

        if (automaticReducedMotion && !phase.equals(lastPhase)) preserveUserPreference();
        lastPhase = phase;
    }

    private void enableAutomaticReducedMotion() {
        automaticReducedMotion = true;
        writeReducedMotion(true);
        preserveUserPreference();
    }

    private void restoreUserMotionPreference() {
        if (!preferenceInitialized || !automaticReducedMotion) return;
        automaticReducedMotion = false;
        writeReducedMotion(userReducedMotion);
        preserveUserPreference();
    }

    private void writeReducedMotion(boolean value) {
        if (reducedField == null) return;
        try { reducedField.setBoolean(gameView, value); }
        catch (IllegalAccessException ignored) { }
    }

    private void preserveUserPreference() {
        Object value = readObject(prefsField);
        if (!(value instanceof SharedPreferences)) return;
        ((SharedPreferences) value).edit()
                .putBoolean("reduced_motion", userReducedMotion)
                .apply();
    }

    private void setDecorationsVisible(boolean visible) {
        decorationSuspended = !visible;
        int state = visible ? View.VISIBLE : View.INVISIBLE;
        if (typographyOverlay.getVisibility() != state) typographyOverlay.setVisibility(state);
        jewelArtOverlay.setPerformanceSuppressed(!visible);
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
