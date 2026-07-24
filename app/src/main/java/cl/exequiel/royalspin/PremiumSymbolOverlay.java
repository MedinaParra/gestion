package cl.exequiel.royalspin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Presentation-only layer for persistent symbol identities, cabinet energy and true anticipation.
 * It observes the already-fixed result and cannot alter the board, credits, RTP or feature state.
 */
public final class PremiumSymbolOverlay extends View implements Choreographer.FrameCallback {
    private static final float W = 360f;
    private static final float H = 800f;
    private static final float REEL_LEFT = 20f;
    private static final float REEL_TOP = 202f;
    private static final float REEL_W = 60f;
    private static final float REEL_GAP = 4f;
    private static final float CELL_H = 78f;

    private final RoyalSpinV2View gameView;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private final Field phaseField;
    private final Field currentField;
    private final Field pendingField;
    private final Field featureField;
    private final Field reducedField;
    private final Field qualityField;
    private final Field phaseStartField;
    private final Field anticipationField;
    private final Field stopTimesField;

    private boolean frameLoop;

    public PremiumSymbolOverlay(Context context, RoyalSpinV2View gameView) {
        super(context);
        this.gameView = gameView;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);

        phaseField = field("phase");
        currentField = field("current");
        pendingField = field("pending");
        featureField = field("featureController");
        reducedField = field("reducedMotion");
        qualityField = field("qualityTier");
        phaseStartField = field("phaseStart");
        anticipationField = field("anticipation");
        stopTimesField = field("stopTimes");
    }

    private static Field field(String name) {
        try {
            Field value = RoyalSpinV2View.class.getDeclaredField(name);
            value.setAccessible(true);
            return value;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        frameLoop = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        frameLoop = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!frameLoop) return;
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void release() {
        frameLoop = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override protected void onDraw(Canvas canvas) {
        Snapshot s = snapshot();
        long now = SystemClock.uptimeMillis();
        float scale = Math.min(getWidth() / W, getHeight() / H);
        float offsetX = (getWidth() - W * scale) * .5f;
        float offsetY = (getHeight() - H * scale) * .5f;

        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        drawVersionBadge(canvas, now, s);
        float top = s.featureActive ? REEL_TOP : REEL_TOP - 25f;
        drawCabinetEnergy(canvas, top, now, s);

        if (isSpinning(s.phase)) {
            if (s.anticipation) drawTrueAnticipation(canvas, top, now, s);
        } else if (s.current != null) {
            drawLivingSymbols(canvas, top, now, s);
        }
        canvas.restore();
    }

    private void drawVersionBadge(Canvas c, long now, Snapshot s) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(102, 70, 258, 87,
                new int[]{0xF0020308, 0xF0180D21, 0xF0020308}, null, Shader.TileMode.CLAMP));
        rect.set(101, 69, 259, 88);
        c.drawRoundRect(rect, 9, 9, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        int alpha = Math.round(LivingSymbolMath.pulse(now, 31, 3200L, .58f, 1f) * 255f);
        p.setColor(withAlpha(0xFFFFD76A, alpha));
        c.drawRoundRect(rect, 9, 9, p);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(6.7f);
        p.setColor(s.featureActive ? 0xFFB8F5FF : 0xFFFFE8A5);
        c.drawText("v3.0 · ROADMAP AUDIOVISUAL", 180, 82.2f, p);
    }

    private void drawCabinetEnergy(Canvas c, float top, long now, Snapshot s) {
        int accent = s.featureActive ? 0xFF58DFFF : 0xFFFFD76A;
        float cabinetBottom = top + CELL_H * 3f + 25f;
        int bulbCount = s.reducedMotion ? 4 : s.qualityTier == 2 ? 10 : s.qualityTier == 1 ? 7 : 5;
        for (int side = 0; side < 2; side++) {
            float x = side == 0 ? 13.5f : 346.5f;
            for (int i = 0; i < bulbCount; i++) {
                float y = top + 7f + i * (CELL_H * 3f - 14f) / Math.max(1, bulbCount - 1);
                float level = s.reducedMotion ? .45f
                        : LivingSymbolMath.pulse(now, i + side * 17, 1900L, .16f, .88f);
                p.setColor(withAlpha(accent, Math.round(level * 210f)));
                c.drawCircle(x, y, level > .62f ? 2.15f : 1.25f, p);
            }
        }

        float sweep = LivingSymbolMath.shimmer(now, 9, s.featureActive ? 2300L : 3100L);
        float x = 18f + sweep * 324f;
        p.setShader(new LinearGradient(x - 38f, top - 20f, x + 38f, top - 12f,
                new int[]{0x00FFFFFF, withAlpha(accent, 150), 0x00FFFFFF},
                null, Shader.TileMode.CLAMP));
        rect.set(18, top - 19, 342, top - 12);
        c.drawRoundRect(rect, 4, 4, p);
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.1f);
        p.setColor(withAlpha(accent, s.qualityTier == 0 ? 70 : 120));
        rect.set(12, top - 17, 348, cabinetBottom);
        c.drawRoundRect(rect, 25, 25, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawLivingSymbols(Canvas c, float top, long now, Snapshot s) {
        StakeSlotEngine.SpinResult result = s.current;
        StakeSlotEngine.LineWin activeWin = activeWin(result, s.phase, now - s.phaseStart);
        for (int reel = 0; reel < StakeSlotEngine.REEL_COUNT; reel++) {
            float left = REEL_LEFT + reel * (REEL_W + REEL_GAP);
            for (int row = 0; row < StakeSlotEngine.ROW_COUNT; row++) {
                String symbol = result.board[reel][row];
                float cx = left + REEL_W * .5f;
                float cy = top + row * CELL_H + CELL_H * .5f;
                boolean winning = activeWin != null && reel < activeWin.count
                        && activeWin.rows[reel] == row;
                int seed = reel * 7 + row * 19 + symbol.hashCode();
                float intensity = winning ? 1f
                        : LivingSymbolMath.pulse(now, seed, 3600L, .28f, .58f);
                if (s.reducedMotion) intensity = winning ? .82f : .28f;
                drawSymbolIdentity(c, symbol, cx, cy, left, top + row * CELL_H,
                        seed, intensity, winning, now, s);
            }
        }
    }

    private void drawSymbolIdentity(Canvas c, String symbol, float cx, float cy,
                                    float left, float top, int seed, float intensity,
                                    boolean winning, long now, Snapshot s) {
        if (StakeSlotEngine.WILD.equals(symbol)) {
            drawWildAura(c, cx, cy, seed, intensity, winning, now, s);
        } else if (StakeSlotEngine.DIAMOND.equals(symbol)) {
            drawDiamondPrism(c, cx, cy, left, top, seed, intensity, winning, now, s);
        } else if (StakeSlotEngine.BELL.equals(symbol)) {
            drawBellMotion(c, cx, cy, seed, intensity, winning, now, s);
        } else if (StakeSlotEngine.BAR.equals(symbol)) {
            drawBarLocks(c, cx, cy, left, top, seed, intensity, winning, now, s);
        } else if (StakeSlotEngine.SEVEN.equals(symbol)) {
            drawSevenEnergy(c, cx, cy, seed, intensity, winning, now, s);
        } else {
            drawRoyalCardPulse(c, cx, cy, left, top, symbol, seed, intensity, winning, now, s);
        }
    }

    private void drawWildAura(Canvas c, float cx, float cy, int seed, float intensity,
                              boolean winning, long now, Snapshot s) {
        int count = LivingSymbolMath.particleCount(s.qualityTier, s.reducedMotion, winning ? 8 : 5);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(winning ? 2.2f : 1.1f);
        p.setColor(withAlpha(0xFFFFD76A, Math.round(intensity * 145f)));
        c.drawCircle(cx, cy - 4f, 23f + LivingSymbolMath.wave(now, seed, 2600L, 2f), p);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < count; i++) {
            float radius = 20f + (i % 2) * 6f;
            float x = cx + LivingSymbolMath.orbitX(now, seed + i * 13, radius, 2500L + i * 170L);
            float y = cy - 4f + LivingSymbolMath.orbitY(now, seed + i * 13, radius * .62f,
                    2500L + i * 170L);
            drawJewel(c, x, y, winning ? 2.7f : 1.7f,
                    withAlpha(i % 2 == 0 ? 0xFFFFE993 : 0xFF9BDFFF,
                            Math.round(intensity * 215f)));
        }
    }

    private void drawDiamondPrism(Canvas c, float cx, float cy, float left, float top,
                                  int seed, float intensity, boolean winning, long now, Snapshot s) {
        int rays = LivingSymbolMath.particleCount(s.qualityTier, s.reducedMotion, winning ? 10 : 6);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(winning ? 1.7f : .85f);
        for (int i = 0; i < rays; i++) {
            double angle = (Math.PI * 2d * i / Math.max(1, rays)) + now * .00035d;
            float length = winning ? 30f : 20f;
            p.setColor(withAlpha(i % 2 == 0 ? 0xFF55DFFF : 0xFFD986FF,
                    Math.round(intensity * (winning ? 185f : 105f))));
            c.drawLine(cx + (float) Math.cos(angle) * 13f,
                    cy + (float) Math.sin(angle) * 13f,
                    cx + (float) Math.cos(angle) * length,
                    cy + (float) Math.sin(angle) * length, p);
        }
        p.setStyle(Paint.Style.FILL);
        float sweep = LivingSymbolMath.shimmer(now, seed, 2100L);
        float x = left - 20f + sweep * (REEL_W + 40f);
        p.setShader(new LinearGradient(x - 10f, top + 8f, x + 10f, top + CELL_H - 8f,
                new int[]{0x00FFFFFF, withAlpha(0xFFFFFFFF, Math.round(intensity * 95f)), 0x00FFFFFF},
                null, Shader.TileMode.CLAMP));
        rect.set(left + 4f, top + 5f, left + REEL_W - 4f, top + CELL_H - 5f);
        c.drawRoundRect(rect, 8, 8, p);
        p.setShader(null);
    }

    private void drawBellMotion(Canvas c, float cx, float cy, int seed, float intensity,
                                boolean winning, long now, Snapshot s) {
        float swing = s.reducedMotion ? 0f : LivingSymbolMath.wave(now, seed, 1800L, winning ? 8f : 3.5f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(winning ? 2f : 1f);
        p.setColor(withAlpha(0xFFFFB548, Math.round(intensity * 165f)));
        rect.set(cx - 25f, cy - 27f, cx + 25f, cy + 27f);
        c.drawArc(rect, 215f + swing, 110f, false, p);
        p.setStyle(Paint.Style.FILL);
        float clapperX = cx + swing * .38f;
        p.setColor(withAlpha(0xFFFFE7A0, Math.round(intensity * 225f)));
        c.drawCircle(clapperX, cy + 25f, winning ? 3.1f : 2f, p);
        if (winning && !s.reducedMotion) {
            for (int i = 0; i < LivingSymbolMath.particleCount(s.qualityTier, false, 6); i++) {
                float x = cx + LivingSymbolMath.orbitX(now, seed + i, 27f, 1250L + i * 90L);
                float y = cy + LivingSymbolMath.orbitY(now, seed + i, 18f, 1250L + i * 90L);
                p.setColor(withAlpha(0xFFFFD76A, 145));
                c.drawCircle(x, y, 1.2f, p);
            }
        }
    }

    private void drawBarLocks(Canvas c, float cx, float cy, float left, float top,
                              int seed, float intensity, boolean winning, long now, Snapshot s) {
        float scan = LivingSymbolMath.shimmer(now, seed, winning ? 950L : 2400L);
        float y = top + 12f + scan * (CELL_H - 24f);
        p.setShader(new LinearGradient(left + 5f, y - 5f, left + REEL_W - 5f, y + 5f,
                new int[]{0x00FFFFFF, withAlpha(0xFFFFFFFF, Math.round(intensity * 130f)), 0x00FFFFFF},
                null, Shader.TileMode.CLAMP));
        rect.set(left + 7f, y - 3f, left + REEL_W - 7f, y + 3f);
        c.drawRoundRect(rect, 3, 3, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(winning ? 2f : 1f);
        p.setColor(withAlpha(0xFFFFD76A, Math.round(intensity * 155f)));
        float lock = winning && !s.reducedMotion ? LivingSymbolMath.dampedKick(now % 850L, 5f) : 0f;
        c.drawLine(left + 10f, cy - 17f - lock, left + 10f, cy + 17f + lock, p);
        c.drawLine(left + REEL_W - 10f, cy - 17f - lock, left + REEL_W - 10f,
                cy + 17f + lock, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(withAlpha(0xFFE9E5D8, Math.round(intensity * 180f)));
        c.drawCircle(left + 10f, cy, 2.1f, p);
        c.drawCircle(left + REEL_W - 10f, cy, 2.1f, p);
    }

    private void drawSevenEnergy(Canvas c, float cx, float cy, int seed, float intensity,
                                 boolean winning, long now, Snapshot s) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(winning ? 2.8f : 1.35f);
        p.setColor(withAlpha(0xFFFF4058, Math.round(intensity * 190f)));
        float drift = s.reducedMotion ? 0f : LivingSymbolMath.wave(now, seed, 1450L, 4f);
        c.drawLine(cx - 22f + drift, cy + 25f, cx + 22f - drift, cy - 25f, p);
        p.setStrokeWidth(winning ? 1.5f : .8f);
        p.setColor(withAlpha(0xFFFFD76A, Math.round(intensity * 150f)));
        c.drawLine(cx - 17f - drift, cy + 28f, cx + 26f + drift, cy - 20f, p);
        p.setStyle(Paint.Style.FILL);
        int sparks = LivingSymbolMath.particleCount(s.qualityTier, s.reducedMotion, winning ? 9 : 4);
        for (int i = 0; i < sparks; i++) {
            float t = LivingSymbolMath.shimmer(now, seed + i * 5, 1150L + i * 70L);
            float x = cx - 22f + 44f * t;
            float y = cy + 25f - 50f * t + LivingSymbolMath.wave(now, seed + i, 700L, 3f);
            p.setColor(withAlpha(i % 2 == 0 ? 0xFFFF425B : 0xFFFFE18A,
                    Math.round(intensity * 175f)));
            c.drawCircle(x, y, winning ? 1.7f : 1f, p);
        }
    }

    private void drawRoyalCardPulse(Canvas c, float cx, float cy, float left, float top,
                                    String symbol, int seed, float intensity, boolean winning,
                                    long now, Snapshot s) {
        int color = StakeSlotEngine.symbolColor(symbol);
        float inset = 7f + LivingSymbolMath.pulse(now, seed, 3100L, 0f, winning ? 2f : 1f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(winning ? 1.7f : .75f);
        p.setColor(withAlpha(color, Math.round(intensity * 130f)));
        rect.set(left + inset, top + inset, left + REEL_W - inset, top + CELL_H - inset);
        c.drawRoundRect(rect, 7, 7, p);
        p.setStyle(Paint.Style.FILL);
        float width = winning ? 31f : 20f;
        float x = cx - width * .5f + LivingSymbolMath.shimmer(now, seed, 2600L) * width;
        p.setShader(new LinearGradient(x - 7f, cy + 22f, x + 7f, cy + 24f,
                new int[]{0x00FFFFFF, withAlpha(color, 170), 0x00FFFFFF},
                null, Shader.TileMode.CLAMP));
        rect.set(cx - width * .5f, cy + 21f, cx + width * .5f, cy + 24f);
        c.drawRoundRect(rect, 2, 2, p);
        p.setShader(null);
    }

    private void drawTrueAnticipation(Canvas c, float top, long now, Snapshot s) {
        long elapsed = Math.max(0L, now - s.phaseStart);
        long start = s.stopTimes.length > 3 ? s.stopTimes[3] : 900L;
        long end = s.stopTimes.length > 4 ? s.stopTimes[4] : start + 900L;
        float progress = LivingSymbolMath.anticipation(elapsed, start, end);
        if (progress <= 0f) return;

        float left = REEL_LEFT + 4f * (REEL_W + REEL_GAP);
        float pulse = s.reducedMotion ? .5f : LivingSymbolMath.pulse(now, 71, 520L, .25f, 1f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f + progress * 5f);
        p.setColor(withAlpha(0xFFFFD76A, Math.round((.25f + .75f * progress * pulse) * 235f)));
        rect.set(left - 7f, top - 9f, left + REEL_W + 7f, top + CELL_H * 3f + 9f);
        c.drawRoundRect(rect, 14, 14, p);
        p.setStyle(Paint.Style.FILL);

        int sparks = LivingSymbolMath.particleCount(s.qualityTier, s.reducedMotion, 12);
        for (int i = 0; i < sparks; i++) {
            float y = top + LivingSymbolMath.shimmer(now, i + 90, 780L + i * 45L) * CELL_H * 3f;
            float side = i % 2 == 0 ? left - 5f : left + REEL_W + 5f;
            p.setColor(withAlpha(i % 3 == 0 ? 0xFF58DFFF : 0xFFFFD76A,
                    Math.round(progress * 190f)));
            c.drawCircle(side, y, 1.2f + progress * 1.5f, p);
        }
    }

    private static StakeSlotEngine.LineWin activeWin(StakeSlotEngine.SpinResult result,
                                                       String phase, long elapsed) {
        if (result == null || result.lineWins == null || result.lineWins.isEmpty()) return null;
        if (!phase.endsWith("REVEALING")) return null;
        int index = (int) ((Math.max(0L, elapsed) / 760L) % result.lineWins.size());
        return result.lineWins.get(index);
    }

    private void drawJewel(Canvas c, float cx, float cy, float size, int color) {
        path.reset();
        path.moveTo(cx, cy - size);
        path.lineTo(cx + size, cy);
        path.lineTo(cx, cy + size);
        path.lineTo(cx - size, cy);
        path.close();
        p.setColor(color);
        c.drawPath(path, p);
    }

    private Snapshot snapshot() {
        Snapshot s = new Snapshot();
        try {
            s.phase = readString(phaseField, "IDLE");
            s.current = (StakeSlotEngine.SpinResult) readObject(currentField);
            s.pending = (StakeSlotEngine.SpinResult) readObject(pendingField);
            s.reducedMotion = readBoolean(reducedField, false);
            s.qualityTier = readInt(qualityField, 1);
            s.phaseStart = readLong(phaseStartField, 0L);
            s.anticipation = readBoolean(anticipationField, false);
            Object stops = readObject(stopTimesField);
            if (stops instanceof long[]) s.stopTimes = ((long[]) stops).clone();
            Object controller = readObject(featureField);
            if (controller instanceof FeatureSessionController) {
                s.featureActive = ((FeatureSessionController) controller).isActive();
            }
        } catch (RuntimeException ignored) {
            s.phase = "IDLE";
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

    private static int withAlpha(int color, int alpha) {
        int safe = Math.max(0, Math.min(255, alpha));
        return (safe << 24) | (color & 0x00FFFFFF);
    }

    private static final class Snapshot {
        String phase = "IDLE";
        StakeSlotEngine.SpinResult current;
        StakeSlotEngine.SpinResult pending;
        boolean featureActive;
        boolean reducedMotion;
        int qualityTier = 1;
        long phaseStart;
        boolean anticipation;
        long[] stopTimes = new long[0];
    }
}
