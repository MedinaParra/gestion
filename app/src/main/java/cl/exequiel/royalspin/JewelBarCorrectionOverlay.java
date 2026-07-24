package cl.exequiel.royalspin;

import android.content.Context;
import android.graphics.Canvas;
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

/** Final containment pass for the BAR vault symbol. */
public final class JewelBarCorrectionOverlay extends View implements Choreographer.FrameCallback {
    private static final float W = 360f;
    private static final float H = 800f;
    private static final float REEL_LEFT = 20f;
    private static final float REEL_TOP = 202f;
    private static final float REEL_W = 60f;
    private static final float REEL_GAP = 4f;
    private static final float CELL_H = 78f;

    private static final String[][] SHOWCASE = new String[][]{
            {"Q", "J", StakeSlotEngine.SEVEN},
            {"Q", "Q", StakeSlotEngine.DIAMOND},
            {StakeSlotEngine.WILD, "J", StakeSlotEngine.BAR},
            {"Q", "Q", "K"},
            {"J", StakeSlotEngine.DIAMOND, StakeSlotEngine.BELL}
    };

    private final RoyalSpinV2View gameView;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path textPath = new Path();
    private final RectF bounds = new RectF();
    private final Matrix matrix = new Matrix();
    private final Typeface face = Typeface.create(Typeface.SERIF, Typeface.BOLD);

    private final Field phaseField;
    private final Field currentField;
    private final Field pendingField;
    private final Field featureField;
    private final Field reducedField;
    private boolean running;

    public JewelBarCorrectionOverlay(Context context, RoyalSpinV2View gameView) {
        super(context);
        this.gameView = gameView;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        phaseField = field("phase");
        currentField = field("current");
        pendingField = field("pending");
        featureField = field("featureController");
        reducedField = field("reducedMotion");
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

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        running = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void release() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override protected void onDraw(Canvas canvas) {
        Snapshot s = snapshot();
        String[][] board = chooseBoard(s);
        long now = SystemClock.uptimeMillis();
        float scale = Math.min(getWidth() / W, getHeight() / H);
        float ox = (getWidth() - W * scale) * .5f;
        float oy = (getHeight() - H * scale) * .5f;
        float top = s.featureActive ? REEL_TOP : REEL_TOP - 25f;

        canvas.save();
        canvas.translate(ox, oy);
        canvas.scale(scale, scale);
        for (int reel = 0; reel < StakeSlotEngine.REEL_COUNT; reel++) {
            float left = REEL_LEFT + reel * (REEL_W + REEL_GAP);
            for (int row = 0; row < StakeSlotEngine.ROW_COUNT; row++) {
                if (!StakeSlotEngine.BAR.equals(safe(board, reel, row))) continue;
                drawContainedBar(canvas, left, top + row * CELL_H, now,
                        reel * 31 + row * 13, s);
            }
        }
        canvas.restore();
    }

    private void drawContainedBar(Canvas c, float left, float top, long now, int seed, Snapshot s) {
        float cx = left + REEL_W * .5f;
        float cy = top + CELL_H * .5f;
        c.save();
        c.clipRect(left + 2, top + 2, left + REEL_W - 2, top + CELL_H - 2);

        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(cx, cy, 48f,
                new int[]{0xFF151A25, 0xFF050711, 0xFF020309}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(left + 2, top + 2,
                left + REEL_W - 2, top + CELL_H - 2), 7, 7, p);
        p.setShader(null);

        RectF plaque = new RectF(cx - 27, cy - 18, cx + 27, cy + 18);
        p.setShader(new LinearGradient(plaque.left, plaque.top, plaque.right, plaque.bottom,
                new int[]{0xFF4C1C01, 0xFFFFEBA1, 0xFFA75A0B, 0xFFFFD66B, 0xFF391300},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(plaque, 8, 8, p);
        p.setShader(null);
        plaque.inset(4, 4);
        p.setShader(new LinearGradient(plaque.left, plaque.top, plaque.right, plaque.bottom,
                new int[]{0xFF080B11, 0xFF343A43, 0xFF080A10, 0xFF30363E},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(plaque, 5, 5, p);
        p.setShader(null);

        Path word = centeredWord("BAR", 18.5f, cx, cy + 1f);
        Path extrusion = new Path(word);
        extrusion.offset(0, 1.8f);
        p.setColor(0xFF401A02);
        c.drawPath(extrusion, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setShader(new LinearGradient(cx - 22, cy - 14, cx + 22, cy + 14,
                new int[]{0xFF6A2A02, 0xFFFFF0AE, 0xFFFFB92C, 0xFF632502},
                null, Shader.TileMode.MIRROR));
        c.drawPath(word, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(cx, cy - 13, cx, cy + 13,
                new int[]{0xFFFFF1B0, 0xFFFFC342, 0xFF9B4D06, 0xFFFFD56B},
                null, Shader.TileMode.CLAMP));
        c.drawPath(word, p);
        p.setShader(null);

        if (!s.reducedMotion) {
            float sweep = JewelArtMath.shimmer(now, seed, 1700L);
            float sx = cx - 29 + sweep * 58;
            c.save();
            c.clipPath(word);
            p.setShader(new LinearGradient(sx - 6, cy - 16, sx + 7, cy + 16,
                    new int[]{0x00FFFFFF, 0xCFFFFFFF, 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
            c.drawRect(cx - 30, cy - 18, cx + 30, cy + 18, p);
            p.setShader(null);
            c.restore();
        }

        for (int side = -1; side <= 1; side += 2) {
            float x = cx + side * 26f;
            p.setColor(0xFF2D1903);
            c.drawCircle(x, cy, 4.2f, p);
            p.setColor(0xFFFFE89A);
            c.drawCircle(x, cy, 1.9f, p);
        }
        c.restore();
    }

    private Path centeredWord(String text, float size, float cx, float cy) {
        p.setTypeface(face);
        p.setTextSize(size);
        p.setStyle(Paint.Style.FILL);
        textPath.reset();
        p.getTextPath(text, 0, text.length(), 0, 0, textPath);
        textPath.computeBounds(bounds, true);
        matrix.reset();
        matrix.setTranslate(cx - bounds.centerX(), cy - bounds.centerY());
        textPath.transform(matrix);
        return new Path(textPath);
    }

    private static String safe(String[][] board, int reel, int row) {
        if (board == null || reel >= board.length || board[reel] == null
                || row >= board[reel].length || board[reel][row] == null) {
            return SHOWCASE[reel][row];
        }
        return board[reel][row];
    }

    private static String[][] chooseBoard(Snapshot s) {
        if (isSpinning(s.phase) && s.pending != null && s.pending.board != null) return s.pending.board;
        if (s.current != null && s.current.board != null) return s.current.board;
        return SHOWCASE;
    }

    private Snapshot snapshot() {
        Snapshot s = new Snapshot();
        s.phase = readString(phaseField, "IDLE");
        s.current = (StakeSlotEngine.SpinResult) readObject(currentField);
        s.pending = (StakeSlotEngine.SpinResult) readObject(pendingField);
        s.reducedMotion = readBoolean(reducedField, false);
        Object controller = readObject(featureField);
        if (controller instanceof FeatureSessionController) {
            s.featureActive = ((FeatureSessionController) controller).isActive();
        }
        return s;
    }

    private Object readObject(Field f) {
        if (f == null) return null;
        try { return f.get(gameView); }
        catch (IllegalAccessException ignored) { return null; }
    }

    private String readString(Field f, String fallback) {
        Object value = readObject(f);
        return value == null ? fallback : String.valueOf(value);
    }

    private boolean readBoolean(Field f, boolean fallback) {
        if (f == null) return fallback;
        try { return f.getBoolean(gameView); }
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
    }
}
