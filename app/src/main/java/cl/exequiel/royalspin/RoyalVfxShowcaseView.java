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

import java.util.Locale;

/**
 * Internal deterministic QA surface. It renders the exact RoyalVfxDirector used by the game
 * without RNG, reel timing or persisted sessions, allowing reproducible visual regression tests.
 */
public final class RoyalVfxShowcaseView extends View implements Choreographer.FrameCallback {
    private static final float W = 360f;
    private static final float H = 800f;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final String scene;
    private final long startedAt;
    private boolean frameLoop;
    private float scale = 1f;
    private float offsetX;
    private float offsetY;

    public RoyalVfxShowcaseView(Context context, String requestedScene) {
        super(context);
        scene = requestedScene == null ? "wild" : requestedScene.trim().toLowerCase(Locale.US);
        startedAt = SystemClock.uptimeMillis();
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        setContentDescription("Royal Spin audiovisual quality assurance showcase");
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

    @Override protected void onDraw(Canvas canvas) {
        long elapsed = SystemClock.uptimeMillis() - startedAt;
        scale = Math.min(getWidth() / W, getHeight() / H);
        offsetX = (getWidth() - W * scale) * .5f;
        offsetY = (getHeight() - H * scale) * .5f;
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        drawBackground(canvas, elapsed);
        drawHeader(canvas);
        if (isFeatureScene()) {
            drawFeatureBackdrop(canvas, elapsed);
            drawFeatureScene(canvas, elapsed);
        } else {
            drawSymbolMachine(canvas, elapsed);
        }
        drawFooter(canvas);
        canvas.restore();
    }

    private boolean isFeatureScene() {
        return scene.contains("feature") || scene.contains("retrigger") || scene.contains("summary");
    }

    private void drawBackground(Canvas c, long elapsed) {
        boolean feature = isFeatureScene();
        p.setShader(new LinearGradient(0, 0, 0, H,
                feature
                        ? new int[]{0xFF01050E, 0xFF062848, 0xFF1B0D3A, 0xFF020308}
                        : new int[]{0xFF010205, 0xFF10051B, 0xFF250D22, 0xFF020205},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
        for (int i = 0; i < 70; i++) {
            float x = (i * 79.13f + (float) Math.sin(elapsed * .0003f + i) * 26f + 500f) % W;
            float y = (i * 137.41f + (float) Math.cos(elapsed * .00023f + i) * 31f + 900f) % H;
            int rgb = i % 3 == 0 ? 0x58DFFF : i % 3 == 1 ? 0xFFD76A : 0x9A68FF;
            p.setColor(((20 + i % 6 * 7) << 24) | rgb);
            c.drawCircle(x, y, i % 9 == 0 ? 1.8f : .75f, p);
        }
        p.setShader(new RadialGradient(180, 350, 310,
                new int[]{feature ? 0x4540CFFF : 0x3A7628FF, 0x00000000},
                null, Shader.TileMode.CLAMP));
        c.drawCircle(180, 350, 310, p);
        p.setShader(null);
    }

    private void drawHeader(Canvas c) {
        crown(c, 180, 35, 21);
        goldText(c, "ROYAL SPIN", 180, 72, 26);
        text(c, "V2.0 · CINEMATIC QA", 180, 92, 7.5f, 0xFFFFE8A5, true);
        panel(c, 32, 108, 328, 143, 17, 0xE90A0D15, 0xFFB68028);
        text(c, sceneTitle(), 180, 131, 12, Color.WHITE, true);
    }

    private void drawSymbolMachine(Canvas c, long elapsed) {
        String symbol = requestedSymbol();
        int accent = StakeSlotEngine.symbolColor(symbol);
        float top = 190;
        p.setShadowLayer(28, 0, 12, 0xDD000000);
        p.setShader(new LinearGradient(8, top - 26, 352, 540,
                new int[]{0xFF231203, 0xFFFFEA9F, 0xFF8B5412, 0xFFFFD46C, 0xFF211104},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(8, top - 26, 352, 535), 31, 31, p);
        p.clearShadowLayer();
        p.setShader(null);
        panel(c, 14, top - 20, 346, 529, 25, 0xFF05070C, 0xFFFFD76A);

        float cellW = 58f;
        float gap = 5f;
        for (int reel = 0; reel < 5; reel++) {
            float left = 21 + reel * (cellW + gap);
            drawCell(c, left, top, cellW, 245, symbol, accent, elapsed, reel);
        }
        drawPayline(c, top + 122, accent);
        text(c, "5 SÍMBOLOS · COREOGRAFÍA DE PREMIO MÁXIMO", 180, 558,
                7.3f, 0xFFC4C8D1, true);

        long loop = elapsed % 3800L;
        for (int reel = 0; reel < 5; reel++) {
            float cx = 21 + reel * (cellW + gap) + cellW * .5f;
            float cy = top + 122;
            RoyalVfxDirector.drawSymbol(c, p, path, symbol, cx, cy,
                    5, 60d, loop + reel * 35L, false);
        }
        p.setShadowLayer(18, 0, 4, accent);
        goldText(c, winTitle(symbol), 180, 178, 24);
        p.clearShadowLayer();
    }

    private void drawCell(Canvas c, float left, float top, float width, float height,
                          String symbol, int accent, long elapsed, int reel) {
        p.setShader(new LinearGradient(left, top, left, top + height,
                new int[]{0xFF5E574C, 0xFF17191E, 0xFF07080A, 0xFF443A2D},
                new float[]{0f, .18f, .72f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(left, top, left + width, top + height), 10, 10, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(reel == 2 ? 2.5f : 1.2f);
        p.setColor(reel == 2 ? 0xFFFFE28B : 0xFF8C8778);
        c.drawRoundRect(new RectF(left, top, left + width, top + height), 10, 10, p);
        p.setStyle(Paint.Style.FILL);

        float glint = (elapsed * .08f + reel * 41f) % (height + 60) - 30;
        p.setShader(new LinearGradient(left, top + glint - 14, left + width, top + glint + 14,
                new int[]{0x00FFFFFF, 0x42FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(left + 2, top + glint - 8, left + width - 2,
                top + glint + 8), 5, 5, p);
        p.setShader(null);

        float cx = left + width * .5f;
        float cy = top + height * .5f;
        p.setShadowLayer(13, 0, 3, accent);
        drawBaseSymbol(c, symbol, cx, cy, accent);
        p.clearShadowLayer();
    }

    private void drawPayline(Canvas c, float y, int accent) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(10);
        p.setColor(0x44000000 | (accent & 0x00FFFFFF));
        c.drawLine(28, y, 332, y, p);
        p.setStrokeWidth(2.7f);
        p.setColor(accent);
        c.drawLine(28, y, 332, y, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawFeatureBackdrop(Canvas c, long elapsed) {
        panel(c, 18, 160, 342, 675, 28, 0xD9070A13, 0xFF58DFFF);
        float pulse = .5f + .5f * (float) Math.sin(elapsed * .004f);
        p.setShader(new RadialGradient(180, 400, 240,
                new int[]{0x5542CFFF, 0x202A60A0, 0x00000000}, null, Shader.TileMode.CLAMP));
        c.drawCircle(180, 400, 240 + pulse * 15, p);
        p.setShader(null);
        text(c, "MODO BONUS · MISMO DIRECTOR VFX DEL JUEGO", 180, 705,
                7.2f, 0xFFBDEEFF, true);
    }

    private void drawFeatureScene(Canvas c, long elapsed) {
        RoyalVfxDirector.Scene type;
        int counter;
        int total;
        if (scene.contains("retrigger")) {
            type = RoyalVfxDirector.Scene.RETRIGGER;
            counter = 10;
            total = 0;
        } else if (scene.contains("summary")) {
            type = RoyalVfxDirector.Scene.FEATURE_SUMMARY;
            counter = 0;
            total = 18750;
        } else {
            type = RoyalVfxDirector.Scene.FEATURE_INTRO;
            counter = Math.min(30, Math.round(30 * Math.min(1f, elapsed / 1800f)));
            total = 0;
        }
        RoyalVfxDirector.drawScene(c, p, path, type,
                850L + elapsed % 2400L, false, counter, total, 5);
    }

    private void drawFooter(Canvas c) {
        panel(c, 45, 735, 315, 777, 21, 0xFF111521, 0xFF76572A);
        text(c, "QA INTERNO · NO ALTERA RNG NI PAGOS", 180, 761,
                7.4f, 0xFFB9C0CC, true);
    }

    private String requestedSymbol() {
        if (scene.contains("bell")) return StakeSlotEngine.BELL;
        if (scene.contains("bar")) return StakeSlotEngine.BAR;
        if (scene.contains("seven") || scene.contains("7")) return StakeSlotEngine.SEVEN;
        if (scene.contains("diamond") || scene.contains("gema")) return StakeSlotEngine.DIAMOND;
        return StakeSlotEngine.WILD;
    }

    private String sceneTitle() {
        if (scene.contains("feature")) return "ROYAL FEATURE · 30 JUEGOS GRATIS";
        if (scene.contains("retrigger")) return "WILD RETRIGGER · +10";
        if (scene.contains("summary")) return "BONUS SUMMARY";
        String symbol = requestedSymbol();
        if (StakeSlotEngine.BELL.equals(symbol)) return "CAMPANA · RING CELEBRATION";
        if (StakeSlotEngine.BAR.equals(symbol)) return "BAR · MECHANICAL LOCK";
        if (StakeSlotEngine.SEVEN.equals(symbol)) return "7 · LUCKY ENERGY SLASH";
        if (StakeSlotEngine.DIAMOND.equals(symbol)) return "GEMA · PRISM EXPLOSION";
        return "WILD · ROYAL CROWN POWER";
    }

    private String winTitle(String symbol) {
        if (StakeSlotEngine.BELL.equals(symbol)) return "RING MEGA WIN";
        if (StakeSlotEngine.BAR.equals(symbol)) return "VAULT MEGA WIN";
        if (StakeSlotEngine.SEVEN.equals(symbol)) return "LUCKY ROYAL WIN";
        if (StakeSlotEngine.DIAMOND.equals(symbol)) return "PRISM ROYAL WIN";
        return "WILD ROYAL WIN";
    }

    private void drawBaseSymbol(Canvas c, String symbol, float cx, float cy, int color) {
        if (StakeSlotEngine.WILD.equals(symbol)) {
            crown(c, cx, cy - 8, 25);
            text(c, "WILD", cx, cy + 34, 8, 0xFFFFE7A0, true);
        } else if (StakeSlotEngine.DIAMOND.equals(symbol)) {
            path.reset();
            path.moveTo(cx, cy - 31);
            path.lineTo(cx + 24, cy - 6);
            path.lineTo(cx + 15, cy + 31);
            path.lineTo(cx - 15, cy + 31);
            path.lineTo(cx - 24, cy - 6);
            path.close();
            p.setShader(new LinearGradient(cx - 25, cy - 31, cx + 25, cy + 31,
                    new int[]{0xFFFFFFFF, color, 0xFF3977FF}, null, Shader.TileMode.CLAMP));
            c.drawPath(path, p);
            p.setShader(null);
        } else if (StakeSlotEngine.BELL.equals(symbol)) {
            p.setColor(color);
            c.drawArc(new RectF(cx - 24, cy - 27, cx + 24, cy + 20), 180, 180, true, p);
            c.drawRoundRect(new RectF(cx - 28, cy + 13, cx + 28, cy + 22), 3, 3, p);
            p.setColor(0xFF8D5510);
            c.drawCircle(cx, cy + 29, 6, p);
        } else if (StakeSlotEngine.BAR.equals(symbol)) {
            panel(c, cx - 24, cy - 16, cx + 24, cy + 16, 5, 0xFFE7E1D2, 0xFFFFD76A);
            text(c, "BAR", cx, cy + 6, 15, 0xFF151515, true);
        } else {
            text(c, "7", cx, cy + 18, 45, color, true);
        }
    }

    private void crown(Canvas c, float cx, float cy, float size) {
        path.reset();
        path.moveTo(cx - size * .58f, cy + size * .30f);
        path.lineTo(cx - size * .50f, cy - size * .28f);
        path.lineTo(cx - size * .15f, cy + size * .02f);
        path.lineTo(cx, cy - size * .55f);
        path.lineTo(cx + size * .16f, cy + size * .02f);
        path.lineTo(cx + size * .51f, cy - size * .28f);
        path.lineTo(cx + size * .58f, cy + size * .30f);
        path.close();
        p.setShader(new LinearGradient(cx - size, cy - size, cx + size, cy + size,
                new int[]{0xFFC78320, 0xFFFFF0AA, 0xFFF6B83F, 0xFFFFD76A},
                null, Shader.TileMode.MIRROR));
        p.setShadowLayer(16, 0, 4, 0xCCF6B83F);
        c.drawPath(path, p);
        c.drawRoundRect(new RectF(cx - size * .60f, cy + size * .35f,
                cx + size * .60f, cy + size * .50f), 4, 4, p);
        p.clearShadowLayer();
        p.setShader(null);
    }

    private void panel(Canvas c, float l, float t, float r, float b, float radius,
                       int fill, int stroke) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(fill);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f);
        p.setColor(stroke);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void goldText(Canvas c, String value, float x, float y, float size) {
        p.setShader(new LinearGradient(x - size * 2, y - size, x + size * 2, y,
                new int[]{0xFFC78320, 0xFFFFF0AA, 0xFFF6B83F, 0xFFFFE58B},
                null, Shader.TileMode.MIRROR));
        text(c, value, x, y, size, Color.WHITE, true);
        p.setShader(null);
    }

    private void text(Canvas c, String value, float x, float y, float size,
                      int color, boolean bold) {
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF,
                bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(value, x, y, p);
    }

    public void release() {
        frameLoop = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }
}
