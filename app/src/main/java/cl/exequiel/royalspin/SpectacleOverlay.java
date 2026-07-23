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
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

/** Transparent vector overlay used by v0.5+ without touching Stake math. */
public final class SpectacleOverlay extends View implements Choreographer.FrameCallback {
    public static final int LEVEL = 5;
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
        drawLivingReels(canvas, now);
        drawHolographicCrown(canvas, now);
        drawEnergyRail(canvas, now);
        canvas.restore();
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
        for (int ring = 0; ring < 3; ring++) {
            float radius = 25 + ring * 7 + pulse * 2;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f);
            paint.setColor(((26 - ring * 6) << 24) | 0xF6C453);
            c.drawCircle(cx, cy, radius, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 7; i++) {
            double a = now * .0012 + i * Math.PI * 2 / 7;
            float x = cx + (float)Math.cos(a) * (29 + pulse * 3);
            float y = cy + (float)Math.sin(a) * (16 + pulse * 2);
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
}
