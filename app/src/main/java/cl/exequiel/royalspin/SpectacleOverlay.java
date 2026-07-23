package cl.exequiel.royalspin;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
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

/** Vector 2.5D overlay: living symbols, light volume and perspective stage. */
public final class SpectacleOverlay extends View implements Choreographer.FrameCallback {
    public static final int LEVEL = 7;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private boolean running;
    private float scale = 1f, offsetX, offsetY;

    public SpectacleOverlay(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
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

    @Override protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        scale = Math.min(getWidth() / 360f, getHeight() / 800f);
        offsetX = (getWidth() - 360f * scale) / 2f;
        offsetY = (getHeight() - 800f * scale) / 2f;
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        drawVolumetricLights(canvas, now);
        drawPerspectiveStage(canvas, now);
        drawSideColumns(canvas, now);
        drawLivingReels(canvas, now);
        drawHolographicCrown(canvas, now);
        drawEnergyRail(canvas, now);
        drawDepthOrbits(canvas, now);
        canvas.restore();
    }

    private void drawVolumetricLights(Canvas c, long now) {
        float sweep = (float)Math.sin(now * .0008f) * 24f;
        paint.setStyle(Paint.Style.FILL);
        path.reset();
        path.moveTo(82 + sweep, 0);
        path.lineTo(136 + sweep, 0);
        path.lineTo(242, 510);
        path.lineTo(154, 510);
        path.close();
        paint.setShader(new LinearGradient(110 + sweep, 0, 190, 510,
                new int[]{0x005FCBFF, 0x185FCBFF, 0x005FCBFF}, null, Shader.TileMode.CLAMP));
        c.drawPath(path, paint);
        paint.setShader(null);

        path.reset();
        path.moveTo(224 - sweep, 0);
        path.lineTo(278 - sweep, 0);
        path.lineTo(206, 510);
        path.lineTo(118, 510);
        path.close();
        paint.setShader(new LinearGradient(250 - sweep, 0, 170, 510,
                new int[]{0x00F6C453, 0x17F6C453, 0x00F6C453}, null, Shader.TileMode.CLAMP));
        c.drawPath(path, paint);
        paint.setShader(null);
    }

    private void drawPerspectiveStage(Canvas c, long now) {
        float pulse = .55f + .45f * (float)Math.sin(now * .0017f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        for (int i = 0; i < 8; i++) {
            float y = 490 + i * i * 4.2f;
            int alpha = 18 + i * 4;
            paint.setColor((alpha << 24) | (i % 2 == 0 ? 0x7C66FF : 0xF6C453));
            c.drawArc(new RectF(180 - 42 - i * 27, y - 8, 180 + 42 + i * 27, y + 18),
                    190, 160, false, paint);
        }
        for (int i = -5; i <= 5; i++) {
            paint.setColor((18 << 24) | (i % 2 == 0 ? 0x5FCBFF : 0x8B65FF));
            c.drawLine(180, 490, 180 + i * 37, 800, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(180, 511, 150,
                new int[]{((int)(35 * pulse) << 24) | 0xF6C453, 0x126A4CFF, 0x00000000},
                null, Shader.TileMode.CLAMP));
        c.drawOval(new RectF(38, 475, 322, 548), paint);
        paint.setShader(null);
    }

    private void drawSideColumns(Canvas c, long now) {
        float glow = .5f + .5f * (float)Math.sin(now * .002f);
        for (int side = 0; side < 2; side++) {
            float left = side == 0 ? 1 : 347;
            paint.setShader(new LinearGradient(left, 140, left + 12, 140,
                    side == 0
                            ? new int[]{0x00F6C453, 0x55F6C453, 0x005FCBFF}
                            : new int[]{0x005FCBFF, 0x55F6C453, 0x00F6C453},
                    null, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(left, 145, left + 12, 690), 6, 6, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f + glow);
            paint.setColor(((35 + (int)(glow * 40)) << 24) | 0xF6C453);
            c.drawRoundRect(new RectF(left + 2, 150, left + 10, 684), 5, 5, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawLivingReels(Canvas c, long now) {
        float t = now * .001f;
        for (int reel = 0; reel < 5; reel++) {
            float cx = 50 + reel * 64f;
            for (int row = 0; row < 3; row++) {
                float cy = 225 + row * 84f;
                float wave = .5f + .5f * (float)Math.sin(t * 1.8f + reel * .8f + row * 1.1f);
                int alpha = 13 + (int)(wave * 19);
                int color = reel == 2 ? 0xF6C453 : (row == 1 ? 0x9B62FF : 0x54CFFF);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.1f + wave * 1.3f);
                paint.setColor((alpha << 24) | color);
                paint.setMaskFilter(new BlurMaskFilter(5 + wave * 5, BlurMaskFilter.Blur.NORMAL));
                c.drawRoundRect(new RectF(cx - 27 - wave * 1.5f, cy - 39 - wave * 1.5f,
                        cx + 27 + wave * 1.5f, cy + 39 + wave * 1.5f), 9, 9, paint);
                paint.setMaskFilter(null);
            }
            float glintY = 183 + ((now * (.055f + reel * .004f) + reel * 43) % 252f);
            paint.setShader(new LinearGradient(cx - 25, glintY, cx + 25, glintY,
                    new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            paint.setStyle(Paint.Style.FILL);
            c.drawRoundRect(new RectF(cx - 24, glintY, cx + 24, glintY + 1.6f), 2, 2, paint);
            paint.setShader(null);
        }
    }

    private void drawHolographicCrown(Canvas c, long now) {
        float pulse = .5f + .5f * (float)Math.sin(now * .0032f);
        float cx = 180, cy = 27;
        for (int ring = 0; ring < 4; ring++) {
            float radius = 25 + ring * 6 + pulse * 2;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f);
            paint.setColor(((28 - ring * 5) << 24) | (ring % 2 == 0 ? 0xF6C453 : 0x5FCBFF));
            c.drawCircle(cx, cy, radius, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 9; i++) {
            double a = now * .0012 + i * Math.PI * 2 / 9;
            float x = cx + (float)Math.cos(a) * (30 + pulse * 4);
            float y = cy + (float)Math.sin(a) * (17 + pulse * 2);
            paint.setColor(i % 2 == 0 ? 0x99F6C453 : 0x885FCBFF);
            c.drawCircle(x, y, 1.2f + pulse, paint);
        }
    }

    private void drawEnergyRail(Canvas c, long now) {
        float x = 18 + (now * .075f % 324f);
        paint.setShader(new RadialGradient(x, 482, 38,
                new int[]{0x99FFF0A0, 0x335F7BFF, 0x00000000}, null, Shader.TileMode.CLAMP));
        c.drawOval(new RectF(x - 38, 477, x + 38, 488), paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(0x557E68FF);
        path.reset();
        path.moveTo(24, 486);
        path.cubicTo(90, 473, 270, 498, 336, 484);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawDepthOrbits(Canvas c, long now) {
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 5; i++) {
            float phase = now * (.00065f + i * .00005f) + i;
            float cx = 180 + (float)Math.sin(phase) * (115 - i * 12);
            float cy = 310 + (float)Math.cos(phase * 1.2f) * (155 - i * 16);
            float radius = 5 + i * 2;
            paint.setStrokeWidth(1f);
            paint.setColor(((20 + i * 6) << 24) | (i % 2 == 0 ? 0x5FCBFF : 0xF6C453));
            c.drawOval(new RectF(cx - radius * 2, cy - radius, cx + radius * 2, cy + radius), paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }
}
