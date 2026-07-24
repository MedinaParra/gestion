package cl.exequiel.royalspin;

import android.view.Choreographer;

import java.lang.reflect.Field;

/** Observes presentation state and synchronizes the generated ambient layer without touching logic. */
public final class PremiumAudioConductor implements Choreographer.FrameCallback {
    private final RoyalSpinV2View gameView;
    private final PremiumAmbientAudioEngine audio = new PremiumAmbientAudioEngine();

    private final Field phaseField;
    private final Field soundField;
    private final Field reducedField;
    private final Field qualityField;
    private final Field featureField;
    private final Field anticipationField;
    private final Field phaseStartField;
    private final Field stopTimesField;

    private boolean running;
    private boolean suspended;
    private boolean initialized;
    private boolean lastFeatureActive;
    private String lastPhase = "";
    private int anticipationStage = -1;
    private long lastProcessedNanos;
    private long lastIdleSignature;

    public PremiumAudioConductor(RoyalSpinV2View gameView) {
        this.gameView = gameView;
        phaseField = field("phase");
        soundField = field("soundEnabled");
        reducedField = field("reducedMotion");
        qualityField = field("qualityTier");
        featureField = field("featureController");
        anticipationField = field("anticipation");
        phaseStartField = field("phaseStart");
        stopTimesField = field("stopTimes");
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
        lastProcessedNanos = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void suspend() {
        suspended = true;
        audio.suspend();
    }

    public void resume() {
        if (!running) start();
        suspended = false;
        audio.resume();
    }

    public void release() {
        running = false;
        suspended = true;
        Choreographer.getInstance().removeFrameCallback(this);
        audio.release();
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if (!suspended && (lastProcessedNanos == 0L
                || frameTimeNanos - lastProcessedNanos >= 50_000_000L)) {
            lastProcessedNanos = frameTimeNanos;
            update(frameTimeNanos / 1_000_000L);
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void update(long nowMs) {
        Snapshot s = snapshot();
        audio.setEnabled(s.soundEnabled);
        audio.updateBed(s.featureActive, s.reducedMotion || s.qualityTier <= 0);

        if (!initialized) {
            initialized = true;
            lastFeatureActive = s.featureActive;
            lastPhase = s.phase;
            lastIdleSignature = nowMs;
        } else {
            if (s.featureActive != lastFeatureActive) {
                audio.playModeTransition(s.featureActive);
                lastFeatureActive = s.featureActive;
            }
            if (!s.phase.equals(lastPhase)) {
                if (!isSpinning(s.phase)) anticipationStage = -1;
                lastPhase = s.phase;
            }
        }

        if (isSpinning(s.phase) && s.anticipation && !s.reducedMotion) {
            updateAnticipation(nowMs, s);
        } else anticipationStage = -1;

        boolean quietState = "IDLE".equals(s.phase) || "FEATURE_READY".equals(s.phase);
        long interval = s.featureActive ? 11_500L : 15_500L;
        if (quietState && s.soundEnabled && !s.reducedMotion
                && nowMs - lastIdleSignature >= interval) {
            lastIdleSignature = nowMs;
            audio.playIdleSignature(s.featureActive);
        }
    }

    private void updateAnticipation(long nowMs, Snapshot s) {
        long elapsed = Math.max(0L, nowMs - s.phaseStart);
        long finalStop = s.stopTimes.length > 4 ? s.stopTimes[4] : 2500L;
        long start = s.stopTimes.length > 3 ? s.stopTimes[3] : finalStop - 900L;
        if (elapsed < start || elapsed > finalStop + 120L) return;
        float progress = LivingSymbolMath.anticipation(elapsed, start, finalStop);
        int stage = Math.min(3, Math.max(0, (int) Math.floor(progress * 4f)));
        while (anticipationStage < stage) {
            anticipationStage++;
            audio.playAnticipationPulse(anticipationStage);
        }
    }

    private Snapshot snapshot() {
        Snapshot s = new Snapshot();
        s.phase = readString(phaseField, "IDLE");
        s.soundEnabled = readBoolean(soundField, true);
        s.reducedMotion = readBoolean(reducedField, false);
        s.qualityTier = readInt(qualityField, 1);
        s.anticipation = readBoolean(anticipationField, false);
        s.phaseStart = readLong(phaseStartField, 0L);
        Object times = readObject(stopTimesField);
        if (times instanceof long[]) s.stopTimes = ((long[]) times).clone();
        Object controller = readObject(featureField);
        if (controller instanceof FeatureSessionController) {
            s.featureActive = ((FeatureSessionController) controller).isActive();
        }
        return s;
    }

    private Object readObject(Field field) {
        if (field == null) return null;
        try { return field.get(gameView); }
        catch (IllegalAccessException ignored) { return null; }
    }

    private String readString(Field field, String fallback) {
        Object value = readObject(field);
        return value == null ? fallback : String.valueOf(value);
    }

    private boolean readBoolean(Field field, boolean fallback) {
        if (field == null) return fallback;
        try { return field.getBoolean(gameView); }
        catch (IllegalAccessException ignored) { return fallback; }
    }

    private int readInt(Field field, int fallback) {
        if (field == null) return fallback;
        try { return field.getInt(gameView); }
        catch (IllegalAccessException ignored) { return fallback; }
    }

    private long readLong(Field field, long fallback) {
        if (field == null) return fallback;
        try { return field.getLong(gameView); }
        catch (IllegalAccessException ignored) { return fallback; }
    }

    private static boolean isSpinning(String phase) {
        return "BASE_SPINNING".equals(phase) || "FEATURE_SPINNING".equals(phase);
    }

    private static final class Snapshot {
        String phase = "IDLE";
        boolean soundEnabled = true;
        boolean reducedMotion;
        int qualityTier = 1;
        boolean featureActive;
        boolean anticipation;
        long phaseStart;
        long[] stopTimes = new long[0];
    }
}
