package cl.exequiel.royalspin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

import java.lang.reflect.Field;

/**
 * Royal Spin 4.0 art replacement layer.
 *
 * This view deliberately masks the former flat title and reel symbols, then redraws them as
 * independent 2.5D jewel objects. It only observes an already-calculated round and cannot change
 * RNG, stops, credits, payouts, RTP or free-spin state.
 */
public final class JewelArtOverlay extends View implements Choreographer.FrameCallback {
    private static final float W = 360f;
    private static final float H = 800f;
    private static final float REEL_LEFT = 20f;
    private static final float REEL_TOP = 202f;
    private static final float REEL_W = 60f;
    private static final float REEL_GAP = 4f;
    private static final float CELL_H = 78f;

    private static final int GOLD = 0xFFFFD76A;
    private static final int GOLD_DARK = 0xFF7D3E08;
    private static final int GOLD_LIGHT = 0xFFFFF1A4;
    private static final int NIGHT = 0xFF030611;
    private static final int RUBY = 0xFFE3234E;
    private static final int SAPPHIRE = 0xFF168DFF;
    private static final int EMERALD = 0xFF24BF73;
    private static final int AMETHYST = 0xFFA33DDE;

    private final RoyalSpinV2View gameView;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path path = new Path();
    private final Path path2 = new Path();
    private final RectF rect = new RectF();
    private final Rect textBounds = new Rect();
    private final Matrix matrix = new Matrix();
    private final Typeface jewelTypeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC);
    private final Typeface plaqueTypeface = Typeface.create(Typeface.SERIF, Typeface.BOLD);

    private final Field phaseField;
    private final Field currentField;
    private final Field featureField;
    private final Field reducedField;
    private final Field qualityField;
    private final Field phaseStartField;
    private final Field anticipationField;
    private final Field stopTimesField;

    private boolean frameLoop;
    private boolean performanceSuppressed;
    private long lastFrameNanos;

    public JewelArtOverlay(Context context, RoyalSpinV2View gameView) {
        super(context);
        this.gameView = gameView;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);

        phaseField = field("phase");
        currentField = field("current");
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

    public void setPerformanceSuppressed(boolean value) {
        if (performanceSuppressed == value) return;
        performanceSuppressed = value;
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        frameLoop = true;
        lastFrameNanos = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        frameLoop = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!frameLoop) return;
        // Full/High animate at display cadence; suppressed/Lite remains alive at ~30 fps.
        long interval = performanceSuppressed ? 33_000_000L : 16_000_000L;
        if (lastFrameNanos == 0L || frameTimeNanos - lastFrameNanos >= interval) {
            lastFrameNanos = frameTimeNanos;
            invalidate();
        }
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

        drawRoyalHeader(canvas, now, s);
        float top = s.featureActive ? REEL_TOP : REEL_TOP - 25f;
        drawReelCabinet(canvas, top, now, s);
        if (isSpinning(s.phase)) drawPremiumSpinBlur(canvas, top, now, s);
        else if (s.current != null) drawBoard(canvas, top, now, s);

        canvas.restore();
    }

    private void drawRoyalHeader(Canvas c, long now, Snapshot s) {
        // Opaque replacement: old flat title is not visible below this layer.
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(52, 0, 308, 96,
                new int[]{0xFF02040B, 0xFF140B22, 0xFF050714}, null, Shader.TileMode.CLAMP));
        rect.set(54, 0, 306, 96);
        c.drawRoundRect(rect, 28, 28, p);
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.8f);
        p.setShader(new LinearGradient(54, 5, 306, 92,
                new int[]{GOLD_DARK, GOLD_LIGHT, GOLD, GOLD_DARK}, null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(57, 3, 303, 93), 25, 25, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);

        float breathe = s.reducedMotion ? 1f : JewelArtMath.breathe(now, 5, 4200L, .018f);
        c.save();
        c.scale(breathe, breathe, 180, 58);
        drawCrown(c, 180, 23, 20f, now, s);
        drawJewelText(c, "ROYAL SPIN", 180, 65, 33f, GOLD, AMETHYST,
                37, now, true, s);
        c.restore();

        // Animated underline behaves like a polished metal light pass.
        float sweep = JewelArtMath.shimmer(now, 17, 3300L);
        float x = 92f + sweep * 176f;
        p.setShader(new LinearGradient(x - 42, 80, x + 42, 85,
                new int[]{0x00FFFFFF, 0xB0FFF0A8, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        rect.set(89, 80, 271, 86);
        c.drawRoundRect(rect, 3, 3, p);
        p.setShader(null);
    }

    private void drawReelCabinet(Canvas c, float top, long now, Snapshot s) {
        float bottom = top + CELL_H * 3f;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(10, top - 15, 350, bottom + 16,
                new int[]{0xFF170A23, 0xFF020612, 0xFF080716, 0xFF1C0A2B},
                null, Shader.TileMode.CLAMP));
        rect.set(11, top - 15, 349, bottom + 15);
        c.drawRoundRect(rect, 24, 24, p);
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4.2f);
        p.setShader(new LinearGradient(10, top, 350, bottom,
                new int[]{0xFF7A3907, 0xFFFFE796, 0xFFFFB72D, 0xFFFFF0A7, 0xFF713005},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(13, top - 12, 347, bottom + 12), 21, 21, p);
        p.setStrokeWidth(1.2f);
        p.setColor(s.featureActive ? 0xFF68E6FF : 0xFFFFD76A);
        c.drawRoundRect(new RectF(17, top - 8, 343, bottom + 8), 18, 18, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);

        // Jewel crest over the reel frame.
        drawJewel(c, 180, top - 12f, 7.2f, AMETHYST, GOLD_LIGHT, now, 91, s);

        for (int reel = 0; reel < StakeSlotEngine.REEL_COUNT; reel++) {
            float left = REEL_LEFT + reel * (REEL_W + REEL_GAP);
            for (int row = 0; row < StakeSlotEngine.ROW_COUNT; row++) {
                float cellTop = top + row * CELL_H;
                drawCellBackground(c, left, cellTop, reel, row, now, s);
            }
        }
    }

    private void drawCellBackground(Canvas c, float left, float top, int reel, int row,
                                    long now, Snapshot s) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(left + REEL_W * .5f, top + CELL_H * .45f,
                CELL_H * .7f,
                s.featureActive
                        ? new int[]{0xFF102A43, 0xFF050B17, 0xFF02040A}
                        : new int[]{0xFF11182C, 0xFF050711, 0xFF020309},
                new float[]{0f, .58f, 1f}, Shader.TileMode.CLAMP));
        rect.set(left + 1.6f, top + 1.5f, left + REEL_W - 1.6f, top + CELL_H - 1.5f);
        c.drawRoundRect(rect, 7, 7, p);
        p.setShader(null);

        // Quiet embossed diamond pattern, inspired by premium cabinet upholstery.
        if (!performanceSuppressed && s.qualityTier > 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(.45f);
            p.setColor(0x182E4168);
            float drift = JewelArtMath.shimmer(now, reel * 13 + row, 9000L) * 8f;
            for (float y = top - 10f + drift; y < top + CELL_H + 10f; y += 13f) {
                for (float x = left + 4f; x < left + REEL_W; x += 16f) {
                    path.reset();
                    path.moveTo(x, y - 3f);
                    path.lineTo(x + 4f, y);
                    path.lineTo(x, y + 3f);
                    path.lineTo(x - 4f, y);
                    path.close();
                    c.drawPath(path, p);
                }
            }
            p.setStyle(Paint.Style.FILL);
        }

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(.75f);
        p.setColor(reel == 4 ? 0x558CCAFF : 0x44E9B94D);
        c.drawRoundRect(rect, 7, 7, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawBoard(Canvas c, float top, long now, Snapshot s) {
        long elapsed = Math.max(0L, now - s.phaseStart);
        StakeSlotEngine.LineWin active = activeWin(s.current, s.phase, elapsed);
        for (int reel = 0; reel < StakeSlotEngine.REEL_COUNT; reel++) {
            float left = REEL_LEFT + reel * (REEL_W + REEL_GAP);
            float landing = JewelArtMath.landing(elapsed, reel, s.reducedMotion);
            for (int row = 0; row < StakeSlotEngine.ROW_COUNT; row++) {
                String symbol = s.current.board[reel][row];
                boolean winning = active != null && reel < active.count && active.rows[reel] == row;
                int seed = reel * 43 + row * 17 + symbol.hashCode();
                float cx = left + REEL_W * .5f;
                float cy = top + row * CELL_H + CELL_H * .5f + landing * 5.2f;
                float pulse = JewelArtMath.winPulse(elapsed, seed, winning);
                float scale = s.reducedMotion ? 1f
                        : JewelArtMath.breathe(now, seed, winning ? 680L : 3900L,
                        winning ? .055f : .012f);
                c.save();
                c.scale(scale, scale, cx, cy);
                drawSymbol(c, symbol, cx, cy, left, top + row * CELL_H,
                        seed, pulse, winning, now, s);
                c.restore();
            }
        }
    }

    private void drawPremiumSpinBlur(Canvas c, float top, long now, Snapshot s) {
        for (int reel = 0; reel < StakeSlotEngine.REEL_COUNT; reel++) {
            float left = REEL_LEFT + reel * (REEL_W + REEL_GAP);
            float speedPhase = JewelArtMath.shimmer(now, reel * 11, 520L + reel * 55L);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(left, top, left, top + CELL_H * 3f,
                    new int[]{0xFF070A14, 0xFF1A0E2D, 0xFF061322, 0xFF170A24, 0xFF050711},
                    null, Shader.TileMode.MIRROR));
            rect.set(left + 2f, top + 2f, left + REEL_W - 2f, top + CELL_H * 3f - 2f);
            c.drawRoundRect(rect, 7, 7, p);
            p.setShader(null);

            int streaks = JewelArtMath.particleCount(s.qualityTier, s.reducedMotion,
                    performanceSuppressed, 8);
            for (int i = 0; i < streaks; i++) {
                float y = top - 40f + Math.floorMod((int) ((speedPhase + i / (float) streaks)
                        * (CELL_H * 3f + 80f)), (int) (CELL_H * 3f + 80f));
                int color = i % 4 == 0 ? RUBY : i % 4 == 1 ? SAPPHIRE
                        : i % 4 == 2 ? GOLD : AMETHYST;
                p.setShader(new LinearGradient(left + 9f, y - 18f, left + REEL_W - 9f, y + 18f,
                        new int[]{0x00FFFFFF, withAlpha(color, 125), 0x00FFFFFF},
                        null, Shader.TileMode.CLAMP));
                rect.set(left + 7f, y - 18f, left + REEL_W - 7f, y + 18f);
                c.drawRoundRect(rect, 9, 9, p);
                p.setShader(null);
            }
        }

        if (s.anticipation) drawAnticipation(c, top, now, s);
    }

    private void drawSymbol(Canvas c, String symbol, float cx, float cy, float left, float top,
                            int seed, float pulse, boolean winning, long now, Snapshot s) {
        if (StakeSlotEngine.WILD.equals(symbol)) {
            drawWild(c, cx, cy, seed, pulse, winning, now, s);
        } else if (StakeSlotEngine.DIAMOND.equals(symbol)) {
            drawDiamond(c, cx, cy, seed, pulse, winning, now, s);
        } else if (StakeSlotEngine.BELL.equals(symbol)) {
            drawBell(c, cx, cy, seed, pulse, winning, now, s);
        } else if (StakeSlotEngine.BAR.equals(symbol)) {
            drawBar(c, cx, cy, seed, pulse, winning, now, s);
        } else if (StakeSlotEngine.SEVEN.equals(symbol)) {
            drawSeven(c, cx, cy, seed, pulse, winning, now, s);
        } else {
            drawJewelGlyph(c, symbol, cx, cy, seed, pulse, winning, now, s);
        }
    }

    private void drawJewelGlyph(Canvas c, String glyph, float cx, float cy, int seed,
                                float pulse, boolean winning, long now, Snapshot s) {
        int enamel = cardColor(glyph);
        Path glyphPath = centeredTextPath(glyph, 53f, cx, cy + 2f, jewelTypeface);

        // Extruded body.
        Path extrusion = new Path(glyphPath);
        extrusion.offset(0f, 3.2f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF321A08);
        p.setShadowLayer(winning ? 12f : 5f, 0, 3, withAlpha(enamel, winning ? 210 : 100));
        c.drawPath(extrusion, p);
        p.clearShadowLayer();

        // Thick jewellery setting.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6.2f);
        p.setShader(new LinearGradient(cx - 25f, cy - 30f, cx + 28f, cy + 31f,
                new int[]{0xFF6F2D02, GOLD, GOLD_LIGHT, 0xFFC06A0B, 0xFF6B2B02},
                null, Shader.TileMode.MIRROR));
        c.drawPath(glyphPath, p);
        p.setShader(null);

        // Polished enamel core.
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(cx - 20f, cy - 28f, cx + 20f, cy + 28f,
                new int[]{lighten(enamel, .58f), enamel, darken(enamel, .52f), enamel},
                new float[]{0f, .28f, .72f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(glyphPath, p);
        p.setShader(null);

        // Inner silver/gold highlight line.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        p.setColor(withAlpha(GOLD_LIGHT, winning ? 255 : 205));
        c.drawPath(glyphPath, p);
        p.setStyle(Paint.Style.FILL);

        // Moving optical reflection clipped to the glyph shape.
        if (!s.reducedMotion) {
            float sweep = JewelArtMath.shimmer(now, seed, winning ? 850L : 3100L);
            float x = cx - 38f + sweep * 76f;
            c.save();
            c.clipPath(glyphPath);
            p.setShader(new LinearGradient(x - 8f, cy - 35f, x + 10f, cy + 35f,
                    new int[]{0x00FFFFFF, withAlpha(Color.WHITE, winning ? 205 : 115), 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
            c.drawRect(cx - 40, cy - 38, cx + 40, cy + 38, p);
            p.setShader(null);
            c.restore();
        }

        // Gem settings make each character an object rather than plain text.
        int gemCount = performanceSuppressed ? 2 : s.qualityTier == 2 ? 5 : 3;
        for (int i = 0; i < gemCount; i++) {
            float angle = (float) (Math.PI * 2d * i / gemCount - Math.PI * .5d);
            float gx = cx + (float) Math.cos(angle) * 22f;
            float gy = cy + (float) Math.sin(angle) * 27f;
            float spark = JewelArtMath.jewelSpark(now, seed + i * 7);
            drawJewel(c, gx, gy, winning ? 3.2f : 2.2f,
                    i % 2 == 0 ? lighten(enamel, .72f) : 0xFFFF6FCF,
                    GOLD_LIGHT, now, seed + i, s);
            if (spark > .08f) drawStar(c, gx, gy, 3f + spark * 7f,
                    withAlpha(Color.WHITE, Math.round(spark * 240f)));
        }

        if (winning) drawWinHalo(c, cx, cy, enamel, pulse, now, seed, s);
    }

    private void drawDiamond(Canvas c, float cx, float cy, int seed, float pulse,
                             boolean winning, long now, Snapshot s) {
        c.save();
        if (!s.reducedMotion) c.rotate(JewelArtMath.sine(now, seed, 5400L) * 2.4f, cx, cy);
        float w = 27f;
        float h = 31f;
        path.reset();
        path.moveTo(cx - w, cy - 9f);
        path.lineTo(cx - 14f, cy - h);
        path.lineTo(cx + 14f, cy - h);
        path.lineTo(cx + w, cy - 9f);
        path.lineTo(cx, cy + h);
        path.close();

        p.setShadowLayer(winning ? 18f : 8f, 0, 2, withAlpha(SAPPHIRE, winning ? 240 : 130));
        p.setShader(new LinearGradient(cx - w, cy - h, cx + w, cy + h,
                new int[]{0xFFB9F7FF, 0xFF2CCBFF, 0xFF0063E6, 0xFF2B27A8, 0xFF7CF4FF},
                null, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.clearShadowLayer();
        p.setShader(null);

        int facets = JewelArtMath.facetCount(s.qualityTier, s.reducedMotion, performanceSuppressed);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        for (int i = 0; i < facets; i++) {
            float x = cx - w + i * (2f * w / Math.max(1, facets - 1));
            p.setColor(withAlpha(i % 2 == 0 ? Color.WHITE : 0xFF85B7FF, 150));
            c.drawLine(cx, cy + h, x, cy - 9f, p);
        }
        c.drawLine(cx - w, cy - 9f, cx + w, cy - 9f, p);
        c.drawLine(cx - 14f, cy - h, cx, cy - 9f, p);
        c.drawLine(cx + 14f, cy - h, cx, cy - 9f, p);
        p.setStyle(Paint.Style.FILL);

        float sweep = JewelArtMath.shimmer(now, seed, winning ? 780L : 2500L);
        float sx = cx - 34f + sweep * 68f;
        c.save();
        c.clipPath(path);
        p.setShader(new LinearGradient(sx - 7, cy - 35, sx + 9, cy + 35,
                new int[]{0x00FFFFFF, 0xC8FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRect(cx - 34, cy - 36, cx + 34, cy + 36, p);
        p.setShader(null);
        c.restore();
        c.restore();

        drawJewel(c, cx, cy - 13f, 4.1f, 0xFFE6FFFF, SAPPHIRE, now, seed, s);
        if (winning) drawWinHalo(c, cx, cy, SAPPHIRE, pulse, now, seed, s);
    }

    private void drawBell(Canvas c, float cx, float cy, int seed, float pulse,
                          boolean winning, long now, Snapshot s) {
        float swing = s.reducedMotion ? 0f : JewelArtMath.sine(now, seed, winning ? 820L : 3100L)
                * (winning ? 8f : 2.2f);
        c.save();
        c.rotate(swing, cx, cy - 21f);

        path.reset();
        path.moveTo(cx - 24f, cy + 18f);
        path.quadTo(cx - 17f, cy + 8f, cx - 16f, cy - 8f);
        path.quadTo(cx - 14f, cy - 27f, cx, cy - 30f);
        path.quadTo(cx + 14f, cy - 27f, cx + 16f, cy - 8f);
        path.quadTo(cx + 17f, cy + 8f, cx + 24f, cy + 18f);
        path.quadTo(cx, cy + 27f, cx - 24f, cy + 18f);
        path.close();

        p.setShadowLayer(winning ? 16f : 7f, 0, 3, withAlpha(GOLD, winning ? 230 : 120));
        p.setShader(new LinearGradient(cx - 25, cy - 31, cx + 25, cy + 24,
                new int[]{0xFF7B3403, 0xFFFFF1A0, 0xFFFFB31F, 0xFF9B4706, 0xFFFFD66A},
                null, Shader.TileMode.MIRROR));
        c.drawPath(path, p);
        p.clearShadowLayer();
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.3f);
        p.setColor(GOLD_LIGHT);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);

        // Ruby jewellery band.
        p.setColor(0xFF7B1530);
        rect.set(cx - 19f, cy + 4f, cx + 19f, cy + 12f);
        c.drawRoundRect(rect, 4, 4, p);
        for (int i = -2; i <= 2; i++) {
            drawJewel(c, cx + i * 7f, cy + 8f, 2.3f,
                    i % 2 == 0 ? 0xFFFF4A72 : AMETHYST, GOLD_LIGHT, now, seed + i, s);
        }
        p.setColor(0xFF7A3506);
        c.drawCircle(cx, cy + 25f, 6f, p);
        p.setColor(GOLD_LIGHT);
        c.drawCircle(cx, cy + 24f, 3.4f, p);
        c.restore();

        if (winning) {
            int arcs = performanceSuppressed ? 1 : 3;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.4f);
            for (int i = 0; i < arcs; i++) {
                p.setColor(withAlpha(GOLD, 190 - i * 45));
                rect.set(cx - 29f - i * 7f, cy - 31f - i * 7f,
                        cx + 29f + i * 7f, cy + 31f + i * 7f);
                c.drawArc(rect, 205, 130, false, p);
            }
            p.setStyle(Paint.Style.FILL);
            drawWinHalo(c, cx, cy, GOLD, pulse, now, seed, s);
        }
    }

    private void drawBar(Canvas c, float cx, float cy, int seed, float pulse,
                         boolean winning, long now, Snapshot s) {
        float kick = winning && !s.reducedMotion
                ? JewelArtMath.landing(now % 700L, 0, false) * 2f : 0f;
        rect.set(cx - 28f - kick, cy - 20f, cx + 28f + kick, cy + 20f);
        p.setShadowLayer(winning ? 15f : 6f, 0, 3, withAlpha(GOLD, winning ? 220 : 100));
        p.setShader(new LinearGradient(cx - 30, cy - 20, cx + 30, cy + 20,
                new int[]{0xFF4D2202, 0xFFFFE79A, 0xFF9D570D, 0xFFFFD569, 0xFF351600},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(rect, 8, 8, p);
        p.clearShadowLayer();
        p.setShader(null);

        rect.inset(4f, 4f);
        p.setShader(new LinearGradient(cx - 23, cy - 15, cx + 23, cy + 15,
                new int[]{0xFF0A0D14, 0xFF2B3038, 0xFF090B11, 0xFF363B42},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(rect, 5, 5, p);
        p.setShader(null);

        drawJewelText(c, "BAR", cx, cy + 4f, 25f, GOLD, 0xFF17191E,
                seed, now, false, s);

        // Mechanical side locks.
        for (int side = -1; side <= 1; side += 2) {
            float x = cx + side * 27f;
            p.setColor(0xFF332005);
            c.drawCircle(x, cy, 4.6f, p);
            p.setColor(GOLD_LIGHT);
            c.drawCircle(x, cy, 2.2f, p);
            p.setColor(0xFF713B06);
            c.drawRect(x - .7f, cy - 4f, x + .7f, cy + 4f, p);
        }

        float scan = JewelArtMath.shimmer(now, seed, winning ? 900L : 2900L);
        float sx = cx - 31f + scan * 62f;
        p.setShader(new LinearGradient(sx - 8, cy - 20, sx + 8, cy + 20,
                new int[]{0x00FFFFFF, 0xAAFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(cx - 28, cy - 20, cx + 28, cy + 20), 8, 8, p);
        p.setShader(null);
        if (winning) drawWinHalo(c, cx, cy, GOLD, pulse, now, seed, s);
    }

    private void drawSeven(Canvas c, float cx, float cy, int seed, float pulse,
                           boolean winning, long now, Snapshot s) {
        Path seven = centeredTextPath("7", 58f, cx, cy + 1f, jewelTypeface);
        Path shadow = new Path(seven);
        shadow.offset(0, 3.2f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF3B0710);
        p.setShadowLayer(winning ? 18f : 8f, 0, 2, withAlpha(RUBY, winning ? 240 : 120));
        c.drawPath(shadow, p);
        p.clearShadowLayer();

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6.5f);
        p.setShader(new LinearGradient(cx - 25, cy - 32, cx + 25, cy + 32,
                new int[]{0xFF6C2E03, GOLD_LIGHT, GOLD, 0xFF7A3103}, null, Shader.TileMode.MIRROR));
        c.drawPath(seven, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(cx - 20, cy - 30, cx + 21, cy + 31,
                new int[]{0xFFFF7185, RUBY, 0xFF760018, 0xFFFF2E51},
                null, Shader.TileMode.CLAMP));
        c.drawPath(seven, p);
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.15f);
        p.setColor(0xFFFFCDD4);
        c.drawPath(seven, p);
        p.setStyle(Paint.Style.FILL);

        // Energy cut across the ruby body.
        float drift = s.reducedMotion ? 0f : JewelArtMath.sine(now, seed, 1100L) * 3f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(winning ? 3.4f : 1.7f);
        p.setColor(withAlpha(GOLD_LIGHT, winning ? 240 : 150));
        c.drawLine(cx - 26f + drift, cy + 28f, cx + 26f - drift, cy - 27f, p);
        p.setStrokeWidth(winning ? 1.8f : .8f);
        p.setColor(withAlpha(Color.WHITE, winning ? 230 : 100));
        c.drawLine(cx - 23f - drift, cy + 29f, cx + 29f + drift, cy - 23f, p);
        p.setStyle(Paint.Style.FILL);

        int sparks = JewelArtMath.particleCount(s.qualityTier, s.reducedMotion,
                performanceSuppressed, winning ? 10 : 4);
        for (int i = 0; i < sparks; i++) {
            float t = JewelArtMath.shimmer(now, seed + i * 11, 980L + i * 90L);
            float x = cx - 25f + 50f * t;
            float y = cy + 28f - 55f * t;
            drawStar(c, x, y, winning ? 3.2f : 1.8f,
                    withAlpha(i % 2 == 0 ? RUBY : GOLD_LIGHT, 190));
        }
        if (winning) drawWinHalo(c, cx, cy, RUBY, pulse, now, seed, s);
    }

    private void drawWild(Canvas c, float cx, float cy, int seed, float pulse,
                          boolean winning, long now, Snapshot s) {
        float lift = s.reducedMotion ? 0f : JewelArtMath.sine(now, seed, 2800L) * 1.6f;
        cy += lift;
        p.setShadowLayer(winning ? 20f : 10f, 0, 3, withAlpha(AMETHYST, winning ? 240 : 140));
        p.setShader(new RadialGradient(cx, cy, 34f,
                new int[]{0x997E3DD8, 0x4452229E, 0x00120A29}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, winning ? 34f : 29f, p);
        p.clearShadowLayer();
        p.setShader(null);

        // Velvet crown cushion.
        path.reset();
        path.moveTo(cx - 22f, cy + 4f);
        path.quadTo(cx - 20f, cy - 16f, cx, cy - 20f);
        path.quadTo(cx + 20f, cy - 16f, cx + 22f, cy + 4f);
        path.quadTo(cx, cy + 18f, cx - 22f, cy + 4f);
        path.close();
        p.setShader(new LinearGradient(cx - 22, cy - 20, cx + 22, cy + 18,
                new int[]{0xFF3B075F, 0xFF9B36D7, 0xFF2B0348, 0xFF6F1BB0},
                null, Shader.TileMode.MIRROR));
        c.drawPath(path, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.2f);
        p.setColor(GOLD);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);

        drawCrown(c, cx, cy - 18f, 19f, now, s);

        // WILD plaque and bespoke letters.
        rect.set(cx - 27f, cy + 11f, cx + 27f, cy + 32f);
        p.setShader(new LinearGradient(cx - 27, cy + 11, cx + 27, cy + 32,
                new int[]{0xFF5B2602, GOLD_LIGHT, 0xFFC57713, GOLD_LIGHT, 0xFF562301},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(rect, 7, 7, p);
        p.setShader(null);
        rect.inset(3f, 3f);
        p.setColor(0xFF371054);
        c.drawRoundRect(rect, 5, 5, p);
        drawJewelText(c, "WILD", cx, cy + 27f, 15f, GOLD_LIGHT, AMETHYST,
                seed, now, false, s);

        int orbit = JewelArtMath.particleCount(s.qualityTier, s.reducedMotion,
                performanceSuppressed, winning ? 10 : 6);
        for (int i = 0; i < orbit; i++) {
            double angle = now * .0012d + i * Math.PI * 2d / orbit;
            float radius = winning ? 31f : 27f;
            float x = cx + (float) Math.cos(angle) * radius;
            float y = cy + (float) Math.sin(angle) * radius * .67f;
            drawJewel(c, x, y, winning ? 3f : 2f,
                    i % 2 == 0 ? 0xFFFF5CC8 : SAPPHIRE, GOLD_LIGHT, now, seed + i, s);
        }
        if (winning) drawWinHalo(c, cx, cy, AMETHYST, pulse, now, seed, s);
    }

    private void drawJewelText(Canvas c, String text, float cx, float cy, float size,
                               int mainColor, int innerColor, int seed, long now,
                               boolean logo, Snapshot s) {
        Path textPath = centeredTextPath(text, size, cx, cy, logo ? jewelTypeface : plaqueTypeface);
        Path extrusion = new Path(textPath);
        extrusion.offset(0, logo ? 3.3f : 2.2f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(logo ? 0xFF4C2104 : darken(innerColor, .6f));
        p.setShadowLayer(logo ? 14f : 6f, 0, 3, withAlpha(mainColor, logo ? 190 : 110));
        c.drawPath(extrusion, p);
        p.clearShadowLayer();

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(logo ? 4.4f : 2.5f);
        p.setShader(new LinearGradient(cx - size * 2.8f, cy - size,
                cx + size * 2.8f, cy + size,
                new int[]{GOLD_DARK, GOLD_LIGHT, GOLD, 0xFFFFF3B2, GOLD_DARK},
                null, Shader.TileMode.MIRROR));
        c.drawPath(textPath, p);
        p.setShader(null);

        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(cx, cy - size, cx, cy + size,
                new int[]{GOLD_LIGHT, mainColor, 0xFF9A5008, GOLD},
                null, Shader.TileMode.CLAMP));
        c.drawPath(textPath, p);
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(logo ? .9f : .65f);
        p.setColor(0xFFFFF4C4);
        c.drawPath(textPath, p);
        p.setStyle(Paint.Style.FILL);

        if (!s.reducedMotion) {
            float sweep = JewelArtMath.shimmer(now, seed, logo ? 2800L : 2100L);
            float x = cx - size * 4f + sweep * size * 8f;
            c.save();
            c.clipPath(textPath);
            p.setShader(new LinearGradient(x - 12, cy - size * 1.3f, x + 12, cy + size * 1.3f,
                    new int[]{0x00FFFFFF, 0xCFFFFFFF, 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
            c.drawRect(cx - size * 5, cy - size * 1.5f,
                    cx + size * 5, cy + size * 1.5f, p);
            p.setShader(null);
            c.restore();
        }
    }

    private Path centeredTextPath(String text, float size, float cx, float cy, Typeface typeface) {
        p.setTypeface(typeface);
        p.setTextSize(size);
        p.setStyle(Paint.Style.FILL);
        path2.reset();
        p.getTextPath(text, 0, text.length(), 0f, 0f, path2);
        path2.computeBounds(rect, true);
        matrix.reset();
        matrix.setTranslate(cx - rect.centerX(), cy - rect.centerY());
        path2.transform(matrix);
        return new Path(path2);
    }

    private void drawCrown(Canvas c, float cx, float cy, float size, long now, Snapshot s) {
        path.reset();
        path.moveTo(cx - size, cy + size * .48f);
        path.lineTo(cx - size * .78f, cy - size * .55f);
        path.lineTo(cx - size * .28f, cy + size * .02f);
        path.lineTo(cx, cy - size);
        path.lineTo(cx + size * .28f, cy + size * .02f);
        path.lineTo(cx + size * .78f, cy - size * .55f);
        path.lineTo(cx + size, cy + size * .48f);
        path.close();
        p.setShadowLayer(8f, 0, 2, 0xAAFFB21C);
        p.setShader(new LinearGradient(cx - size, cy - size, cx + size, cy + size,
                new int[]{0xFF7C3503, GOLD_LIGHT, 0xFFFFB516, GOLD_LIGHT, 0xFF713003},
                null, Shader.TileMode.MIRROR));
        c.drawPath(path, p);
        p.clearShadowLayer();
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(GOLD_LIGHT);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);

        drawJewel(c, cx, cy + size * .18f, size * .23f, 0xFFFF4FA7,
                GOLD_LIGHT, now, 41, s);
        drawJewel(c, cx - size * .72f, cy - size * .48f, size * .15f,
                RUBY, GOLD_LIGHT, now, 42, s);
        drawJewel(c, cx + size * .72f, cy - size * .48f, size * .15f,
                SAPPHIRE, GOLD_LIGHT, now, 43, s);
        drawStar(c, cx, cy - size, size * .38f,
                withAlpha(Color.WHITE, Math.round(JewelArtMath.jewelSpark(now, 44) * 245f)));
    }

    private void drawJewel(Canvas c, float cx, float cy, float size, int fill, int edge,
                           long now, int seed, Snapshot s) {
        path.reset();
        path.moveTo(cx, cy - size);
        path.lineTo(cx + size, cy);
        path.lineTo(cx, cy + size);
        path.lineTo(cx - size, cy);
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(cx - size, cy - size, cx + size, cy + size,
                new int[]{lighten(fill, .75f), fill, darken(fill, .55f), lighten(fill, .45f)},
                null, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(.65f);
        p.setColor(edge);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        if (!s.reducedMotion && !performanceSuppressed) {
            float spark = JewelArtMath.jewelSpark(now, seed);
            if (spark > .18f) drawStar(c, cx - size * .25f, cy - size * .3f,
                    size * (.6f + spark), withAlpha(Color.WHITE, Math.round(spark * 230f)));
        }
    }

    private void drawWinHalo(Canvas c, float cx, float cy, int color, float pulse,
                             long now, int seed, Snapshot s) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f + pulse * 2.3f);
        p.setColor(withAlpha(color, Math.round(80f + pulse * 155f)));
        float radius = 26f + pulse * 7f;
        c.drawCircle(cx, cy, radius, p);
        if (!s.reducedMotion && !performanceSuppressed) {
            p.setStrokeWidth(.7f);
            p.setColor(withAlpha(GOLD_LIGHT, Math.round(pulse * 155f)));
            c.drawCircle(cx, cy, radius + 6f + JewelArtMath.sine(now, seed, 800L) * 2f, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawAnticipation(Canvas c, float top, long now, Snapshot s) {
        long elapsed = Math.max(0L, now - s.phaseStart);
        long start = s.stopTimes.length > 3 ? s.stopTimes[3] : 900L;
        long end = s.stopTimes.length > 4 ? s.stopTimes[4] : start + 900L;
        float progress = LivingSymbolMath.anticipation(elapsed, start, end);
        if (progress <= 0f) return;
        float left = REEL_LEFT + 4f * (REEL_W + REEL_GAP);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f + progress * 4f);
        p.setColor(withAlpha(GOLD_LIGHT, Math.round(110f + progress * 135f)));
        rect.set(left - 6f, top - 7f, left + REEL_W + 6f, top + CELL_H * 3f + 7f);
        c.drawRoundRect(rect, 14, 14, p);
        p.setStyle(Paint.Style.FILL);
        int sparks = JewelArtMath.particleCount(s.qualityTier, s.reducedMotion,
                performanceSuppressed, 12);
        for (int i = 0; i < sparks; i++) {
            float y = top + JewelArtMath.shimmer(now, 80 + i, 720L + i * 60L) * CELL_H * 3f;
            float x = i % 2 == 0 ? left - 4f : left + REEL_W + 4f;
            drawStar(c, x, y, 2f + progress * 2f,
                    withAlpha(i % 3 == 0 ? SAPPHIRE : GOLD_LIGHT,
                            Math.round(progress * 210f)));
        }
    }

    private void drawStar(Canvas c, float cx, float cy, float radius, int color) {
        if ((color >>> 24) == 0 || radius <= 0f) return;
        path.reset();
        for (int i = 0; i < 8; i++) {
            double angle = -Math.PI * .5d + i * Math.PI / 4d;
            float r = i % 2 == 0 ? radius : radius * .18f;
            float x = cx + (float) Math.cos(angle) * r;
            float y = cy + (float) Math.sin(angle) * r;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawPath(path, p);
    }

    private static int cardColor(String glyph) {
        if ("A".equals(glyph)) return RUBY;
        if ("K".equals(glyph)) return EMERALD;
        if ("Q".equals(glyph)) return 0xFFE33B9E;
        return SAPPHIRE;
    }

    private static int lighten(int color, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return Color.rgb(Math.round(r + (255 - r) * a),
                Math.round(g + (255 - g) * a),
                Math.round(b + (255 - b) * a));
    }

    private static int darken(int color, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(Math.round(Color.red(color) * a),
                Math.round(Color.green(color) * a),
                Math.round(Color.blue(color) * a));
    }

    private static int withAlpha(int color, int alpha) {
        int safe = Math.max(0, Math.min(255, alpha));
        return (safe << 24) | (color & 0x00FFFFFF);
    }

    private static StakeSlotEngine.LineWin activeWin(StakeSlotEngine.SpinResult result,
                                                       String phase, long elapsed) {
        if (result == null || result.lineWins == null || result.lineWins.isEmpty()) return null;
        if (!phase.endsWith("REVEALING")) return null;
        int index = (int) ((Math.max(0L, elapsed) / 760L) % result.lineWins.size());
        return result.lineWins.get(index);
    }

    private Snapshot snapshot() {
        Snapshot s = new Snapshot();
        try {
            s.phase = readString(phaseField, "IDLE");
            s.current = (StakeSlotEngine.SpinResult) readObject(currentField);
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

    private static final class Snapshot {
        String phase = "IDLE";
        StakeSlotEngine.SpinResult current;
        boolean featureActive;
        boolean reducedMotion;
        boolean anticipation;
        int qualityTier = 1;
        long phaseStart;
        long[] stopTimes = new long[0];
    }
}
