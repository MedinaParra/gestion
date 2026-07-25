package cl.exequiel.royalspin.landscape;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

/**
 * Non-interactive cinematic effects layer. It never changes RNG, payout or game state.
 */
public final class LandscapeSpectacleOverlay extends View implements Choreographer.FrameCallback {
    private static final float W = 1280f;
    private static final float H = 720f;
    private static final float REEL_X = 205f;
    private static final float REEL_Y = 145f;
    private static final float REEL_W = 790f;
    private static final float REEL_H = 450f;
    private static final float SPIN_X = 1136f;
    private static final float SPIN_Y = 440f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private boolean running;
    private boolean spinPressed;
    private long spinSequenceStart = -1L;

    public LandscapeSpectacleOverlay(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setSpinPressed(boolean pressed) {
        spinPressed = pressed;
        invalidate();
    }

    public void triggerSpinSequence() {
        spinSequenceStart = SystemClock.uptimeMillis();
        invalidate();
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

    public void release() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        float scale = Math.min(getWidth() / W, getHeight() / H);
        float offsetX = (getWidth() - W * scale) * .5f;
        float offsetY = (getHeight() - H * scale) * .5f;

        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        drawAmbientBeams(canvas, now);
        drawReelEnergy(canvas, now);
        drawSpinHalo(canvas, now);
        drawSpinSequence(canvas, now);
        drawScanline(canvas, now);
        canvas.restore();
    }

    private void drawAmbientBeams(Canvas c, long now) {
        for (int i = 0; i < 5; i++) {
            float phase = fract(now / (7600f + i * 630f) + i * .19f);
            float x = -260f + phase * 1800f;
            paint.setShader(new LinearGradient(x - 130, 0, x + 130, H,
                    new int[]{0x0000DFFF, 0x1A55DFFF, 0x00FFD66A},
                    null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, paint);
            paint.setShader(null);
        }

        float breathe = .5f + .5f * (float) Math.sin(now * .0015f);
        paint.setShader(new RadialGradient(640, 360, 620,
                new int[]{withAlpha(0xFF7F35D9, Math.round(8 + 14 * breathe)), 0x00000000},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, paint);
        paint.setShader(null);
    }

    private void drawReelEnergy(Canvas c, long now) {
        paint.setStyle(Paint.Style.STROKE);
        for (int ring = 0; ring < 3; ring++) {
            float pulse = fract(now / (2400f + ring * 310f) + ring * .28f);
            float inset = 8 + ring * 8 + pulse * 10;
            rect.set(REEL_X - inset, REEL_Y - inset,
                    REEL_X + REEL_W + inset, REEL_Y + REEL_H + inset);
            paint.setStrokeWidth(1.2f + ring * .5f);
            paint.setColor(withAlpha(ring == 1 ? 0xFF58E6FF : 0xFFFFD35E,
                    Math.round(34 * (1f - pulse))));
            c.drawRoundRect(rect, 24 + inset * .15f, 24 + inset * .15f, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSpinHalo(Canvas c, long now) {
        float pulse = .5f + .5f * (float) Math.sin(now * .006f);
        float pressScale = spinPressed ? .86f : 1f;
        float radius = (112f + 15f * pulse) * pressScale;
        paint.setShader(new RadialGradient(SPIN_X, SPIN_Y, radius,
                new int[]{withAlpha(0xFFFFF3A2, spinPressed ? 120 : 75),
                        withAlpha(0xFFFFA51F, spinPressed ? 70 : 35), 0x00000000},
                null, Shader.TileMode.CLAMP));
        c.drawCircle(SPIN_X, SPIN_Y, radius, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(spinPressed ? 5f : 2.5f);
        paint.setColor(withAlpha(0xFFFFE08B, spinPressed ? 210 : 90));
        c.drawCircle(SPIN_X, SPIN_Y, 103f + pulse * 8f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSpinSequence(Canvas c, long now) {
        if (spinSequenceStart < 0L) return;
        long elapsed = now - spinSequenceStart;
        if (elapsed > 3900L) {
            spinSequenceStart = -1L;
            return;
        }

        if (elapsed < 420L) {
            float t = clamp(elapsed / 420f, 0f, 1f);
            float radius = 70f + 360f * easeOutCubic(t);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(10f * (1f - t) + 1f);
            paint.setColor(withAlpha(0xFFFFE06E, Math.round(220 * (1f - t))));
            c.drawCircle(SPIN_X, SPIN_Y, radius, paint);
            paint.setStyle(Paint.Style.FILL);

            paint.setColor(withAlpha(Color.WHITE, Math.round(70 * (1f - t))));
            c.drawRect(0, 0, W, H, paint);
        }

        if (elapsed < 2450L) {
            drawVelocityStreaks(c, elapsed);
        }

        long[] stops = {900, 1160, 1430, 1730, 2110};
        for (int reel = 0; reel < stops.length; reel++) {
            long local = elapsed - stops[reel];
            if (local >= 0 && local < 260L) {
                float t = local / 260f;
                float left = REEL_X + reel * (REEL_W / 5f);
                paint.setShader(new LinearGradient(left, REEL_Y, left + REEL_W / 5f, REEL_Y + REEL_H,
                        new int[]{0x00FFFFFF,
                                withAlpha(reel == 4 ? 0xFFFFD35E : 0xFF5CE8FF,
                                        Math.round(145 * (1f - t))),
                                0x00FFFFFF}, null, Shader.TileMode.CLAMP));
                c.drawRect(left, REEL_Y, left + REEL_W / 5f, REEL_Y + REEL_H, paint);
                paint.setShader(null);
            }
        }

        if (elapsed > 2480L) {
            float t = clamp((elapsed - 2480L) / 1120f, 0f, 1f);
            drawCelebrationSparks(c, t, now);
        }
    }

    private void drawVelocityStreaks(Canvas c, long elapsed) {
        float intensity = (float) Math.sin(Math.PI * clamp(elapsed / 2450f, 0f, 1f));
        c.save();
        c.clipRect(REEL_X, REEL_Y, REEL_X + REEL_W, REEL_Y + REEL_H);
        paint.setStrokeWidth(2.2f);
        for (int i = 0; i < 34; i++) {
            float x = REEL_X + fract(i * .618f + elapsed * .00043f) * REEL_W;
            float y = REEL_Y + fract(i * .371f + elapsed * .00125f) * REEL_H;
            float length = 35f + (i % 5) * 18f;
            paint.setColor(withAlpha(i % 3 == 0 ? 0xFFFFD46A : 0xFF65E7FF,
                    Math.round((35 + i % 4 * 13) * intensity)));
            c.drawLine(x, y, x, y + length, paint);
        }
        c.restore();
    }

    private void drawCelebrationSparks(Canvas c, float t, long now) {
        float fade = 1f - t;
        for (int i = 0; i < 48; i++) {
            float angle = i * 2.399963f + now * .00018f;
            float distance = 45f + t * (180f + (i % 7) * 25f);
            float x = 640f + (float) Math.cos(angle) * distance;
            float y = 360f + (float) Math.sin(angle) * distance * .58f - t * 70f;
            float size = 2f + (i % 5);
            paint.setColor(withAlpha(i % 3 == 0 ? 0xFFFFFFFF
                            : i % 3 == 1 ? 0xFFFFD761 : 0xFF62E6FF,
                    Math.round(210 * fade)));
            if ((i & 1) == 0) {
                c.drawCircle(x, y, size, paint);
            } else {
                drawDiamond(c, x, y, size * 1.25f);
            }
        }
    }

    private void drawScanline(Canvas c, long now) {
        float y = fract(now / 5200f) * H;
        paint.setShader(new LinearGradient(0, y - 28, 0, y + 28,
                new int[]{0x00000000, 0x16FFFFFF, 0x00000000},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, paint);
        paint.setShader(null);
    }

    private void drawDiamond(Canvas c, float cx, float cy, float size) {
        path.reset();
        path.moveTo(cx, cy - size);
        path.lineTo(cx + size, cy);
        path.lineTo(cx, cy + size);
        path.lineTo(cx - size, cy);
        path.close();
        c.drawPath(path, paint);
    }

    private static float easeOutCubic(float t) {
        float x = 1f - clamp(t, 0f, 1f);
        return 1f - x * x * x;
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
