package cl.exequiel.royalspin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
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

/**
 * Final opaque Jewel Art renderer.
 *
 * It replaces the old logo and all 15 reel cells rather than decorating text underneath. During a
 * spin it observes the already-calculated pending board; before the first round it uses a visual-only
 * showcase board. No method in this class can change RNG, stops, credits, payouts, RTP or features.
 */
public final class JewelArtFinalOverlay extends View implements Choreographer.FrameCallback {
    private static final float W = 360f;
    private static final float H = 800f;
    private static final float REEL_LEFT = 20f;
    private static final float REEL_TOP = 202f;
    private static final float REEL_W = 60f;
    private static final float REEL_GAP = 4f;
    private static final float CELL_H = 78f;

    private static final int NIGHT = 0xFF030611;
    private static final int GOLD_DARK = 0xFF642903;
    private static final int GOLD = 0xFFFFBE32;
    private static final int GOLD_LIGHT = 0xFFFFF0A3;
    private static final int RUBY = 0xFFE7274F;
    private static final int SAPPHIRE = 0xFF168DFF;
    private static final int EMERALD = 0xFF26C872;
    private static final int AMETHYST = 0xFFA63BE2;

    /** Visual-only board shown before the first persisted result exists. */
    private static final String[][] SHOWCASE = new String[][]{
            {"Q", "J", StakeSlotEngine.SEVEN},
            {"Q", "Q", StakeSlotEngine.DIAMOND},
            {StakeSlotEngine.WILD, "J", StakeSlotEngine.BAR},
            {"Q", "Q", "K"},
            {"J", StakeSlotEngine.DIAMOND, StakeSlotEngine.BELL}
    };

    private final RoyalSpinV2View gameView;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path path = new Path();
    private final Path reusableTextPath = new Path();
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();
    private final Typeface jewelFace = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC);
    private final Typeface plaqueFace = Typeface.create(Typeface.SERIF, Typeface.BOLD);

    private final Field phaseField;
    private final Field currentField;
    private final Field pendingField;
    private final Field featureField;
    private final Field reducedField;
    private final Field qualityField;
    private final Field phaseStartField;
    private final Field anticipationField;
    private final Field stopTimesField;

    private boolean running;
    private boolean performanceSuppressed;
    private long lastFrameNanos;

    public JewelArtFinalOverlay(Context context, RoyalSpinV2View gameView) {
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
            Field f = RoyalSpinV2View.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public void setPerformanceSuppressed(boolean suppressed) {
        performanceSuppressed = suppressed;
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        lastFrameNanos = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        long interval = performanceSuppressed ? 33_000_000L : 16_000_000L;
        if (lastFrameNanos == 0L || frameTimeNanos - lastFrameNanos >= interval) {
            lastFrameNanos = frameTimeNanos;
            invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void release() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override protected void onDraw(Canvas canvas) {
        Snapshot s = snapshot();
        long now = SystemClock.uptimeMillis();
        float scale = Math.min(getWidth() / W, getHeight() / H);
        float ox = (getWidth() - W * scale) * .5f;
        float oy = (getHeight() - H * scale) * .5f;

        canvas.save();
        canvas.translate(ox, oy);
        canvas.scale(scale, scale);

        drawOpaqueHeader(canvas, now, s);
        float top = s.featureActive ? REEL_TOP : REEL_TOP - 25f;
        drawOpaqueCabinet(canvas, top, now, s);

        String[][] board = chooseBoard(s);
        drawBoard(canvas, board, top, now, s);
        if (isSpinning(s.phase)) drawSpinMotion(canvas, top, now, s);
        if (s.anticipation && isSpinning(s.phase)) drawAnticipation(canvas, top, now, s);

        canvas.restore();
    }

    private static String[][] chooseBoard(Snapshot s) {
        if (isSpinning(s.phase) && s.pending != null && s.pending.board != null) return s.pending.board;
        if (s.current != null && s.current.board != null) return s.current.board;
        return SHOWCASE;
    }

    private void drawOpaqueHeader(Canvas c, long now, Snapshot s) {
        // Wide and fully opaque so no inherited WordArt-style title can ghost through.
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(64, 0, 296, 105,
                new int[]{0xFF02040A, 0xFF170A25, 0xFF050714}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(66, 0, 294, 104), 26, 26, p);
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.7f);
        p.setShader(new LinearGradient(66, 0, 294, 104,
                new int[]{GOLD_DARK, GOLD_LIGHT, GOLD, GOLD_DARK}, null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(68, 2, 292, 102), 24, 24, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);

        float breathe = s.reducedMotion ? 1f : JewelArtMath.breathe(now, 7, 4300L, .018f);
        c.save();
        c.scale(breathe, breathe, 180, 58);
        drawCrown(c, 180, 23, 20f, now, s);
        drawMetalWord(c, "ROYAL SPIN", 180, 68, 31f, now, 19, s, true);
        c.restore();

        float sweep = JewelArtMath.shimmer(now, 33, 3000L);
        float x = 94f + sweep * 172f;
        p.setShader(new LinearGradient(x - 36f, 82, x + 36f, 88,
                new int[]{0x00FFFFFF, 0xB8FFF4BC, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(91, 82, 269, 88), 3, 3, p);
        p.setShader(null);
    }

    private void drawOpaqueCabinet(Canvas c, float top, long now, Snapshot s) {
        float bottom = top + CELL_H * 3f;
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(8, top - 18, 352, bottom + 18,
                new int[]{0xFF1A0925, 0xFF040711, 0xFF0A0818, 0xFF200A30},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(10, top - 17, 350, bottom + 17), 25, 25, p);
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4.4f);
        p.setShader(new LinearGradient(10, top, 350, bottom,
                new int[]{0xFF652802, GOLD_LIGHT, GOLD, 0xFFFFE789, 0xFF5B2201},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(13, top - 13, 347, bottom + 13), 22, 22, p);
        p.setShader(null);
        p.setStrokeWidth(1.1f);
        p.setColor(s.featureActive ? 0xFF69E9FF : 0xFFFFD76A);
        c.drawRoundRect(new RectF(17, top - 8, 343, bottom + 8), 18, 18, p);
        p.setStyle(Paint.Style.FILL);

        drawGem(c, 180, top - 12, 7f, AMETHYST, now, 81, s);

        for (int reel = 0; reel < StakeSlotEngine.REEL_COUNT; reel++) {
            float left = REEL_LEFT + reel * (REEL_W + REEL_GAP);
            for (int row = 0; row < StakeSlotEngine.ROW_COUNT; row++) {
                drawCell(c, left, top + row * CELL_H, reel, row, now, s);
            }
        }
    }

    private void drawCell(Canvas c, float left, float top, int reel, int row, long now, Snapshot s) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(left + 30f, top + 34f, 54f,
                s.featureActive
                        ? new int[]{0xFF122B42, 0xFF050A15, 0xFF02040A}
                        : new int[]{0xFF12182A, 0xFF050711, 0xFF020309},
                new float[]{0f, .62f, 1f}, Shader.TileMode.CLAMP));
        RectF cell = new RectF(left + 1.5f, top + 1.5f,
                left + REEL_W - 1.5f, top + CELL_H - 1.5f);
        c.drawRoundRect(cell, 7, 7, p);
        p.setShader(null);

        if (!performanceSuppressed && !s.reducedMotion && s.qualityTier > 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(.42f);
            p.setColor(0x183D4E79);
            float drift = JewelArtMath.shimmer(now, reel * 11 + row, 9500L) * 7f;
            for (float y = top - 8f + drift; y < top + CELL_H + 8f; y += 14f) {
                for (float x = left + 5f; x < left + REEL_W; x += 17f) {
                    path.reset();
                    path.moveTo(x, y - 3f);
                    path.lineTo(x + 4f, y);
                    path.lineTo(x, y + 3f);
                    path.lineTo(x - 4f, y);
                    path.close();
                    c.drawPath(path, p);
                }
            }
        }

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(.7f);
        p.setColor(reel == 4 ? 0x668BCBFF : 0x55E4B64A);
        c.drawRoundRect(cell, 7, 7, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawBoard(Canvas c, String[][] board, float top, long now, Snapshot s) {
        long elapsed = Math.max(0L, now - s.phaseStart);
        StakeSlotEngine.LineWin active = activeWin(s.current, s.phase, elapsed);
        boolean spinning = isSpinning(s.phase);

        for (int reel = 0; reel < StakeSlotEngine.REEL_COUNT; reel++) {
            float left = REEL_LEFT + reel * (REEL_W + REEL_GAP);
            float reelShift = spinning && !s.reducedMotion
                    ? JewelArtMath.sine(now, reel * 13, 530L + reel * 45L) * 7f : 0f;
            float landing = spinning ? 0f : JewelArtMath.landing(elapsed, reel, s.reducedMotion) * 4.5f;
            for (int row = 0; row < StakeSlotEngine.ROW_COUNT; row++) {
                String symbol = safeSymbol(board, reel, row);
                boolean winning = active != null && reel < active.count && active.rows[reel] == row;
                int seed = reel * 43 + row * 17 + symbol.hashCode();
                float cx = left + REEL_W * .5f;
                float cy = top + row * CELL_H + CELL_H * .5f + reelShift + landing;
                float pulse = JewelArtMath.winPulse(elapsed, seed, winning);
                float objectScale = s.reducedMotion ? 1f
                        : JewelArtMath.breathe(now, seed, winning ? 650L : 3900L,
                        winning ? .052f : .011f);

                c.save();
                c.clipRect(left + 2, top + row * CELL_H + 2,
                        left + REEL_W - 2, top + (row + 1) * CELL_H - 2);
                c.scale(objectScale, objectScale, cx, cy);
                drawSymbol(c, symbol, cx, cy, seed, pulse, winning, now, s);
                c.restore();
            }
        }
    }

    private static String safeSymbol(String[][] board, int reel, int row) {
        if (board == null || reel >= board.length || board[reel] == null || row >= board[reel].length
                || board[reel][row] == null) return SHOWCASE[reel][row];
        return board[reel][row];
    }

    private void drawSpinMotion(Canvas c, float top, long now, Snapshot s) {
        int streaks = JewelArtMath.particleCount(s.qualityTier, s.reducedMotion,
                performanceSuppressed, 7);
        for (int reel = 0; reel < StakeSlotEngine.REEL_COUNT; reel++) {
            float left = REEL_LEFT + reel * (REEL_W + REEL_GAP);
            for (int i = 0; i < streaks; i++) {
                float phase = JewelArtMath.shimmer(now, reel * 19 + i,
                        470L + reel * 40L + i * 13L);
                float y = top - 22f + phase * (CELL_H * 3f + 44f);
                int color = i % 4 == 0 ? RUBY : i % 4 == 1 ? SAPPHIRE
                        : i % 4 == 2 ? GOLD : AMETHYST;
                p.setShader(new LinearGradient(left + 8, y - 13, left + REEL_W - 8, y + 13,
                        new int[]{0x00FFFFFF, withAlpha(color, performanceSuppressed ? 55 : 95),
                                0x00FFFFFF}, null, Shader.TileMode.CLAMP));
                c.drawRoundRect(new RectF(left + 7, y - 13, left + REEL_W - 7, y + 13), 7, 7, p);
                p.setShader(null);
            }
        }
    }

    private void drawSymbol(Canvas c, String symbol, float cx, float cy, int seed,
                            float pulse, boolean winning, long now, Snapshot s) {
        if (StakeSlotEngine.WILD.equals(symbol)) drawWild(c, cx, cy, seed, pulse, winning, now, s);
        else if (StakeSlotEngine.DIAMOND.equals(symbol)) drawDiamond(c, cx, cy, seed, pulse, winning, now, s);
        else if (StakeSlotEngine.BELL.equals(symbol)) drawBell(c, cx, cy, seed, pulse, winning, now, s);
        else if (StakeSlotEngine.BAR.equals(symbol)) drawBar(c, cx, cy, seed, pulse, winning, now, s);
        else if (StakeSlotEngine.SEVEN.equals(symbol)) drawSeven(c, cx, cy, seed, pulse, winning, now, s);
        else drawGlyph(c, symbol, cx, cy, seed, pulse, winning, now, s);
    }

    private void drawGlyph(Canvas c, String glyph, float cx, float cy, int seed,
                           float pulse, boolean winning, long now, Snapshot s) {
        int enamel = glyphColor(glyph);
        Path glyphPath = textPath(glyph, 50f, cx, cy + 1f, jewelFace);
        Path extrusion = new Path(glyphPath);
        extrusion.offset(0f, 3.1f);

        drawGlow(c, cx, cy, 29f, enamel, winning ? 150 : 55);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF291006);
        c.drawPath(extrusion, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6.1f);
        p.setShader(new LinearGradient(cx - 24, cy - 30, cx + 25, cy + 31,
                new int[]{GOLD_DARK, GOLD, GOLD_LIGHT, 0xFFB85B08, GOLD_DARK},
                null, Shader.TileMode.MIRROR));
        c.drawPath(glyphPath, p);
        p.setShader(null);

        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(cx - 18, cy - 29, cx + 18, cy + 29,
                new int[]{lighten(enamel, .66f), enamel, darken(enamel, .48f), enamel},
                new float[]{0f, .28f, .72f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(glyphPath, p);
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(GOLD_LIGHT);
        c.drawPath(glyphPath, p);
        p.setStyle(Paint.Style.FILL);

        if (!s.reducedMotion) {
            float sweep = JewelArtMath.shimmer(now, seed, winning ? 850L : 2900L);
            float sx = cx - 34f + sweep * 68f;
            c.save();
            c.clipPath(glyphPath);
            p.setShader(new LinearGradient(sx - 7, cy - 34, sx + 9, cy + 34,
                    new int[]{0x00FFFFFF, withAlpha(Color.WHITE, winning ? 210 : 120),
                            0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.drawRect(cx - 36, cy - 36, cx + 36, cy + 36, p);
            p.setShader(null);
            c.restore();
        }

        int gemCount = performanceSuppressed ? 2 : s.qualityTier == 2 ? 5 : 3;
        for (int i = 0; i < gemCount; i++) {
            double angle = -Math.PI * .5d + i * Math.PI * 2d / gemCount;
            float gx = cx + (float) Math.cos(angle) * 22f;
            float gy = cy + (float) Math.sin(angle) * 25f;
            drawGem(c, gx, gy, winning ? 3f : 2.1f,
                    i % 2 == 0 ? lighten(enamel, .72f) : 0xFFFF66C5,
                    now, seed + i * 7, s);
        }
        if (winning) drawWinRing(c, cx, cy, enamel, pulse, now, seed, s);
    }

    private void drawDiamond(Canvas c, float cx, float cy, int seed, float pulse,
                             boolean winning, long now, Snapshot s) {
        drawGlow(c, cx, cy, 34f, SAPPHIRE, winning ? 180 : 80);
        c.save();
        if (!s.reducedMotion) c.rotate(JewelArtMath.sine(now, seed, 5200L) * 2.3f, cx, cy);
        float w = 26f;
        float h = 30f;
        path.reset();
        path.moveTo(cx - w, cy - 8f);
        path.lineTo(cx - 14f, cy - h);
        path.lineTo(cx + 14f, cy - h);
        path.lineTo(cx + w, cy - 8f);
        path.lineTo(cx, cy + h);
        path.close();
        p.setShader(new LinearGradient(cx - w, cy - h, cx + w, cy + h,
                new int[]{0xFFD5FDFF, 0xFF36D7FF, 0xFF0063E7, 0xFF3424A5, 0xFF7CF4FF},
                null, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        int facets = JewelArtMath.facetCount(s.qualityTier, s.reducedMotion, performanceSuppressed);
        for (int i = 0; i < facets; i++) {
            float x = cx - w + i * (2f * w / Math.max(1, facets - 1));
            p.setColor(withAlpha(i % 2 == 0 ? Color.WHITE : 0xFF86B9FF, 165));
            c.drawLine(cx, cy + h, x, cy - 8f, p);
        }
        c.drawLine(cx - w, cy - 8f, cx + w, cy - 8f, p);
        c.drawLine(cx - 14f, cy - h, cx, cy - 8f, p);
        c.drawLine(cx + 14f, cy - h, cx, cy - 8f, p);
        p.setStyle(Paint.Style.FILL);

        float sweep = JewelArtMath.shimmer(now, seed, winning ? 760L : 2200L);
        float sx = cx - 34f + sweep * 68f;
        c.save();
        c.clipPath(path);
        p.setShader(new LinearGradient(sx - 7, cy - 35, sx + 9, cy + 35,
                new int[]{0x00FFFFFF, 0xD0FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRect(cx - 34, cy - 36, cx + 34, cy + 36, p);
        p.setShader(null);
        c.restore();
        c.restore();
        drawGem(c, cx, cy - 12f, 4f, 0xFFE9FFFF, now, seed, s);
        if (winning) drawWinRing(c, cx, cy, SAPPHIRE, pulse, now, seed, s);
    }

    private void drawBell(Canvas c, float cx, float cy, int seed, float pulse,
                          boolean winning, long now, Snapshot s) {
        drawGlow(c, cx, cy, 33f, GOLD, winning ? 175 : 65);
        float swing = s.reducedMotion ? 0f : JewelArtMath.sine(now, seed,
                winning ? 820L : 3000L) * (winning ? 7f : 2f);
        c.save();
        c.rotate(swing, cx, cy - 20f);
        path.reset();
        path.moveTo(cx - 23f, cy + 17f);
        path.quadTo(cx - 16f, cy + 7f, cx - 15f, cy - 8f);
        path.quadTo(cx - 13f, cy - 26f, cx, cy - 29f);
        path.quadTo(cx + 13f, cy - 26f, cx + 15f, cy - 8f);
        path.quadTo(cx + 16f, cy + 7f, cx + 23f, cy + 17f);
        path.quadTo(cx, cy + 26f, cx - 23f, cy + 17f);
        path.close();
        p.setShader(new LinearGradient(cx - 24, cy - 30, cx + 24, cy + 24,
                new int[]{0xFF703002, GOLD_LIGHT, 0xFFFFAD17, 0xFF904005, 0xFFFFD56A},
                null, Shader.TileMode.MIRROR));
        c.drawPath(path, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        p.setColor(GOLD_LIGHT);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);

        p.setColor(0xFF76122D);
        c.drawRoundRect(new RectF(cx - 18, cy + 4, cx + 18, cy + 12), 4, 4, p);
        for (int i = -2; i <= 2; i++) {
            drawGem(c, cx + i * 7f, cy + 8f, 2.2f,
                    i % 2 == 0 ? 0xFFFF4B73 : AMETHYST, now, seed + i, s);
        }
        p.setColor(0xFF713005);
        c.drawCircle(cx, cy + 24f, 5.8f, p);
        p.setColor(GOLD_LIGHT);
        c.drawCircle(cx, cy + 23.5f, 3.2f, p);
        c.restore();

        if (winning) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.4f);
            int arcs = performanceSuppressed ? 1 : 3;
            for (int i = 0; i < arcs; i++) {
                p.setColor(withAlpha(GOLD, 190 - i * 45));
                c.drawArc(new RectF(cx - 29 - i * 7, cy - 30 - i * 7,
                        cx + 29 + i * 7, cy + 30 + i * 7), 205, 130, false, p);
            }
            p.setStyle(Paint.Style.FILL);
            drawWinRing(c, cx, cy, GOLD, pulse, now, seed, s);
        }
    }

    private void drawBar(Canvas c, float cx, float cy, int seed, float pulse,
                         boolean winning, long now, Snapshot s) {
        drawGlow(c, cx, cy, 33f, GOLD, winning ? 155 : 48);
        RectF outer = new RectF(cx - 28, cy - 20, cx + 28, cy + 20);
        p.setShader(new LinearGradient(cx - 29, cy - 20, cx + 29, cy + 20,
                new int[]{0xFF4E1D01, GOLD_LIGHT, 0xFFA85A0B, GOLD_LIGHT, 0xFF391400},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(outer, 8, 8, p);
        p.setShader(null);
        RectF inner = new RectF(cx - 24, cy - 16, cx + 24, cy + 16);
        p.setShader(new LinearGradient(cx - 24, cy - 16, cx + 24, cy + 16,
                new int[]{0xFF090C12, 0xFF343942, 0xFF080A10, 0xFF30363E},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(inner, 5, 5, p);
        p.setShader(null);
        drawMetalWord(c, "BAR", cx, cy + 5, 24f, now, seed, s, false);

        for (int side = -1; side <= 1; side += 2) {
            float x = cx + side * 27f;
            p.setColor(0xFF2C1903);
            c.drawCircle(x, cy, 4.5f, p);
            p.setColor(GOLD_LIGHT);
            c.drawCircle(x, cy, 2.1f, p);
            p.setColor(0xFF703705);
            c.drawRect(x - .7f, cy - 4, x + .7f, cy + 4, p);
        }
        if (winning) drawWinRing(c, cx, cy, GOLD, pulse, now, seed, s);
    }

    private void drawSeven(Canvas c, float cx, float cy, int seed, float pulse,
                           boolean winning, long now, Snapshot s) {
        drawGlow(c, cx, cy, 33f, RUBY, winning ? 185 : 75);
        Path seven = textPath("7", 56f, cx, cy + 1f, jewelFace);
        Path extrusion = new Path(seven);
        extrusion.offset(0, 3f);
        p.setColor(0xFF340710);
        c.drawPath(extrusion, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(6.4f);
        p.setShader(new LinearGradient(cx - 24, cy - 31, cx + 24, cy + 31,
                new int[]{GOLD_DARK, GOLD_LIGHT, GOLD, GOLD_DARK}, null, Shader.TileMode.MIRROR));
        c.drawPath(seven, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(cx - 19, cy - 29, cx + 20, cy + 29,
                new int[]{0xFFFF7388, RUBY, 0xFF740015, 0xFFFF3154},
                null, Shader.TileMode.CLAMP));
        c.drawPath(seven, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(0xFFFFD0D8);
        c.drawPath(seven, p);

        float drift = s.reducedMotion ? 0f : JewelArtMath.sine(now, seed, 1100L) * 3f;
        p.setStrokeWidth(winning ? 3.3f : 1.5f);
        p.setColor(withAlpha(GOLD_LIGHT, winning ? 245 : 155));
        c.drawLine(cx - 25 + drift, cy + 27, cx + 25 - drift, cy - 27, p);
        p.setStyle(Paint.Style.FILL);
        int sparks = JewelArtMath.particleCount(s.qualityTier, s.reducedMotion,
                performanceSuppressed, winning ? 9 : 4);
        for (int i = 0; i < sparks; i++) {
            float t = JewelArtMath.shimmer(now, seed + i * 11, 960L + i * 80L);
            drawStar(c, cx - 24 + 48 * t, cy + 27 - 54 * t,
                    winning ? 3f : 1.7f,
                    withAlpha(i % 2 == 0 ? RUBY : GOLD_LIGHT, 195));
        }
        if (winning) drawWinRing(c, cx, cy, RUBY, pulse, now, seed, s);
    }

    private void drawWild(Canvas c, float cx, float cy, int seed, float pulse,
                          boolean winning, long now, Snapshot s) {
        drawGlow(c, cx, cy, 38f, AMETHYST, winning ? 205 : 105);
        float lift = s.reducedMotion ? 0f : JewelArtMath.sine(now, seed, 2700L) * 1.5f;
        cy += lift;

        path.reset();
        path.moveTo(cx - 21, cy + 3);
        path.quadTo(cx - 19, cy - 15, cx, cy - 19);
        path.quadTo(cx + 19, cy - 15, cx + 21, cy + 3);
        path.quadTo(cx, cy + 17, cx - 21, cy + 3);
        path.close();
        p.setShader(new LinearGradient(cx - 21, cy - 19, cx + 21, cy + 17,
                new int[]{0xFF3A055E, 0xFF9F3ADB, 0xFF290344, 0xFF721DB1},
                null, Shader.TileMode.MIRROR));
        c.drawPath(path, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(GOLD);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);

        drawCrown(c, cx, cy - 18, 18f, now, s);
        RectF plaque = new RectF(cx - 27, cy + 11, cx + 27, cy + 32);
        p.setShader(new LinearGradient(cx - 27, cy + 11, cx + 27, cy + 32,
                new int[]{0xFF542001, GOLD_LIGHT, 0xFFC27210, GOLD_LIGHT, 0xFF4B1B01},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(plaque, 7, 7, p);
        p.setShader(null);
        plaque.inset(3, 3);
        p.setColor(0xFF36104F);
        c.drawRoundRect(plaque, 5, 5, p);
        drawMetalWord(c, "WILD", cx, cy + 27, 14f, now, seed, s, false);

        int orbit = JewelArtMath.particleCount(s.qualityTier, s.reducedMotion,
                performanceSuppressed, winning ? 9 : 5);
        for (int i = 0; i < orbit; i++) {
            double angle = now * .0012d + i * Math.PI * 2d / orbit;
            float radius = winning ? 31f : 27f;
            drawGem(c, cx + (float) Math.cos(angle) * radius,
                    cy + (float) Math.sin(angle) * radius * .66f,
                    winning ? 2.9f : 2f,
                    i % 2 == 0 ? 0xFFFF5BC4 : SAPPHIRE,
                    now, seed + i, s);
        }
        if (winning) drawWinRing(c, cx, cy, AMETHYST, pulse, now, seed, s);
    }

    private void drawMetalWord(Canvas c, String text, float cx, float cy, float size,
                               long now, int seed, Snapshot s, boolean logo) {
        Path word = textPath(text, size, cx, cy, logo ? jewelFace : plaqueFace);
        Path extrusion = new Path(word);
        extrusion.offset(0, logo ? 3.1f : 2f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(logo ? 0xFF431B03 : 0xFF421C03);
        c.drawPath(extrusion, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(logo ? 4.2f : 2.4f);
        p.setShader(new LinearGradient(cx - size * 3, cy - size,
                cx + size * 3, cy + size,
                new int[]{GOLD_DARK, GOLD_LIGHT, GOLD, GOLD_LIGHT, GOLD_DARK},
                null, Shader.TileMode.MIRROR));
        c.drawPath(word, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(cx, cy - size, cx, cy + size,
                new int[]{GOLD_LIGHT, GOLD, 0xFF9B5008, GOLD}, null, Shader.TileMode.CLAMP));
        c.drawPath(word, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(logo ? .9f : .65f);
        p.setColor(0xFFFFF4C5);
        c.drawPath(word, p);
        p.setStyle(Paint.Style.FILL);

        if (!s.reducedMotion) {
            float sweep = JewelArtMath.shimmer(now, seed, logo ? 2700L : 1900L);
            float sx = cx - size * 4f + sweep * size * 8f;
            c.save();
            c.clipPath(word);
            p.setShader(new LinearGradient(sx - 11, cy - size * 1.3f,
                    sx + 11, cy + size * 1.3f,
                    new int[]{0x00FFFFFF, 0xCFFFFFFF, 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
            c.drawRect(cx - size * 5, cy - size * 1.5f,
                    cx + size * 5, cy + size * 1.5f, p);
            p.setShader(null);
            c.restore();
        }
    }

    private Path textPath(String text, float size, float cx, float cy, Typeface face) {
        p.setTypeface(face);
        p.setTextSize(size);
        p.setStyle(Paint.Style.FILL);
        reusableTextPath.reset();
        p.getTextPath(text, 0, text.length(), 0, 0, reusableTextPath);
        reusableTextPath.computeBounds(rect, true);
        matrix.reset();
        matrix.setTranslate(cx - rect.centerX(), cy - rect.centerY());
        reusableTextPath.transform(matrix);
        return new Path(reusableTextPath);
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
        p.setShader(new LinearGradient(cx - size, cy - size, cx + size, cy + size,
                new int[]{0xFF713001, GOLD_LIGHT, 0xFFFFB414, GOLD_LIGHT, 0xFF652801},
                null, Shader.TileMode.MIRROR));
        c.drawPath(path, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(GOLD_LIGHT);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        drawGem(c, cx, cy + size * .18f, size * .22f, 0xFFFF4EA7, now, 41, s);
        drawGem(c, cx - size * .72f, cy - size * .48f, size * .14f, RUBY, now, 42, s);
        drawGem(c, cx + size * .72f, cy - size * .48f, size * .14f, SAPPHIRE, now, 43, s);
        float sparkle = JewelArtMath.jewelSpark(now, 44);
        drawStar(c, cx, cy - size, size * (.15f + sparkle * .35f),
                withAlpha(Color.WHITE, Math.round(sparkle * 245f)));
    }

    private void drawGem(Canvas c, float cx, float cy, float size, int fill,
                         long now, int seed, Snapshot s) {
        path.reset();
        path.moveTo(cx, cy - size);
        path.lineTo(cx + size, cy);
        path.lineTo(cx, cy + size);
        path.lineTo(cx - size, cy);
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(cx - size, cy - size, cx + size, cy + size,
                new int[]{lighten(fill, .76f), fill, darken(fill, .5f), lighten(fill, .42f)},
                null, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(.65f);
        p.setColor(GOLD_LIGHT);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        if (!s.reducedMotion && !performanceSuppressed) {
            float spark = JewelArtMath.jewelSpark(now, seed);
            if (spark > .18f) drawStar(c, cx - size * .24f, cy - size * .28f,
                    size * (.5f + spark), withAlpha(Color.WHITE, Math.round(spark * 230f)));
        }
    }

    private void drawGlow(Canvas c, float cx, float cy, float radius, int color, int alpha) {
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, radius,
                new int[]{withAlpha(color, alpha), withAlpha(color, alpha / 3), 0x00000000},
                new float[]{0f, .48f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, radius, p);
        p.setShader(null);
    }

    private void drawWinRing(Canvas c, float cx, float cy, int color, float pulse,
                             long now, int seed, Snapshot s) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f + pulse * 2.2f);
        p.setColor(withAlpha(color, Math.round(80 + pulse * 160)));
        float r = 26 + pulse * 7;
        c.drawCircle(cx, cy, r, p);
        if (!s.reducedMotion && !performanceSuppressed) {
            p.setStrokeWidth(.7f);
            p.setColor(withAlpha(GOLD_LIGHT, Math.round(pulse * 160)));
            c.drawCircle(cx, cy, r + 6 + JewelArtMath.sine(now, seed, 800L) * 2, p);
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
        p.setStrokeWidth(2 + progress * 4);
        p.setColor(withAlpha(GOLD_LIGHT, Math.round(110 + progress * 140)));
        c.drawRoundRect(new RectF(left - 6, top - 7,
                left + REEL_W + 6, top + CELL_H * 3 + 7), 14, 14, p);
        p.setStyle(Paint.Style.FILL);
        int sparks = JewelArtMath.particleCount(s.qualityTier, s.reducedMotion,
                performanceSuppressed, 12);
        for (int i = 0; i < sparks; i++) {
            float y = top + JewelArtMath.shimmer(now, 80 + i, 720L + i * 60L) * CELL_H * 3;
            float x = i % 2 == 0 ? left - 4 : left + REEL_W + 4;
            drawStar(c, x, y, 2 + progress * 2,
                    withAlpha(i % 3 == 0 ? SAPPHIRE : GOLD_LIGHT,
                            Math.round(progress * 210)));
        }
    }

    private void drawStar(Canvas c, float cx, float cy, float radius, int color) {
        if (radius <= 0 || Color.alpha(color) == 0) return;
        path.reset();
        for (int i = 0; i < 8; i++) {
            double angle = -Math.PI * .5 + i * Math.PI / 4;
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

    private static int glyphColor(String glyph) {
        if ("A".equals(glyph)) return RUBY;
        if ("K".equals(glyph)) return EMERALD;
        if ("Q".equals(glyph)) return 0xFFE13A9B;
        return SAPPHIRE;
    }

    private static int lighten(int color, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(Math.round(Color.red(color) + (255 - Color.red(color)) * a),
                Math.round(Color.green(color) + (255 - Color.green(color)) * a),
                Math.round(Color.blue(color) + (255 - Color.blue(color)) * a));
    }

    private static int darken(int color, float factor) {
        float f = Math.max(0f, Math.min(1f, factor));
        return Color.rgb(Math.round(Color.red(color) * f),
                Math.round(Color.green(color) * f),
                Math.round(Color.blue(color) * f));
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
            s.pending = (StakeSlotEngine.SpinResult) readObject(pendingField);
            s.reducedMotion = readBoolean(reducedField, false);
            s.qualityTier = readInt(qualityField, 1);
            s.phaseStart = readLong(phaseStartField, 0L);
            s.anticipation = readBoolean(anticipationField, false);
            Object times = readObject(stopTimesField);
            if (times instanceof long[]) s.stopTimes = ((long[]) times).clone();
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
        StakeSlotEngine.SpinResult pending;
        boolean featureActive;
        boolean reducedMotion;
        boolean anticipation;
        int qualityTier = 1;
        long phaseStart;
        long[] stopTimes = new long[0];
    }
}
