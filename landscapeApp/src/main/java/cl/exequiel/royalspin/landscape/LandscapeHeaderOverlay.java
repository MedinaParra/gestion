package cl.exequiel.royalspin.landscape;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

/** Clean animated header that replaces the broad logo sweep with a narrow optical sparkle. */
public final class LandscapeHeaderOverlay extends View implements Choreographer.FrameCallback {
    private static final float W = 1280f;
    private static final float H = 720f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap logo = IconAtlas.loadLogo();
    private boolean running;

    public LandscapeHeaderOverlay(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
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
        if (!logo.isRecycled()) logo.recycle();
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float scale = Math.min(getWidth() / W, getHeight() / H);
        float offsetX = (getWidth() - W * scale) * .5f;
        float offsetY = (getHeight() - H * scale) * .5f;
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        paint.setShader(new RadialGradient(640, 34, 560,
                new int[]{0xFF24102E, 0xFF080A12, 0xFF020307},
                new float[]{0f, .68f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(10, 10, 1270, 118, paint);
        paint.setShader(null);

        float breathe = 1f + .014f * (float) Math.sin(now * .0022f);
        canvas.save();
        canvas.scale(breathe, breathe, 640, 60);
        RectF logoRect = new RectF(447, 12, 833, 105);
        canvas.drawBitmap(logo, null, logoRect, paint);
        canvas.restore();

        float sweep = (now % 4600L) / 4600f;
        float x = 466 + sweep * 348;
        paint.setShader(new LinearGradient(x - 13, 24, x + 13, 104,
                new int[]{0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(x - 13, 22, x + 13, 106, paint);
        paint.setShader(null);
        paint.setColor(0xDDFFFFFF);
        canvas.drawCircle(x, 58, 2.4f, paint);
        canvas.drawRect(x - 9, 57.2f, x + 9, 58.8f, paint);
        canvas.drawRect(x - .8f, 49, x + .8f, 67, paint);

        drawEqualizer(canvas, 250, 62, now);
        drawEqualizer(canvas, 1030, 62, now);
        paint.setColor(0x55D49533);
        canvas.drawRect(180, 111, 1100, 113, paint);
        canvas.restore();
    }

    private void drawEqualizer(Canvas canvas, float centerX, float centerY, long now) {
        for (int i = 0; i < 21; i++) {
            float wave = .25f + .75f * (float) Math.abs(Math.sin(now * .004 + i * .48));
            float h = 4 + wave * (i % 3 == 0 ? 26 : 16);
            float x = centerX + (i - 10) * 5.2f;
            paint.setColor(withAlpha(i % 4 == 0 ? 0xFF5ADFFF : 0xFFFFB73D, 150));
            canvas.drawRoundRect(new RectF(x - 1.4f, centerY - h / 2, x + 1.4f, centerY + h / 2), 2, 2, paint);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }
}
