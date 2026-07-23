package cl.exequiel.royalspin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

/** Small top overlay that makes every captured iteration traceable. */
public final class IterationBadge extends View implements Choreographer.FrameCallback {
    public static final int LEVEL = 9;
    public static final String LABEL = "v0.9 · ROYAL ART SYSTEM";
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean running;

    public IterationBadge(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
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
        float scale = Math.min(getWidth() / 360f, getHeight() / 800f);
        float ox = (getWidth() - 360f * scale) / 2f;
        float oy = (getHeight() - 800f * scale) / 2f;
        canvas.save();
        canvas.translate(ox, oy);
        canvas.scale(scale, scale);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xF0100B05);
        canvas.drawRoundRect(new RectF(87, 68, 273, 87), 9, 9, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        p.setShader(new LinearGradient(87, 68, 273, 87,
                new int[]{0xFFF6B83F, 0xFFFFF0A0, 0xFF67E6A4, 0xFFF6B83F},
                null, Shader.TileMode.MIRROR));
        canvas.drawRoundRect(new RectF(87, 68, 273, 87), 9, 9, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create("serif", Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(7.2f);
        p.setColor(0xFFFFEDB2);
        canvas.drawText(LABEL, 180, 80.5f, p);
        float t = SystemClock.uptimeMillis() * .005f;
        for (int i = 0; i < 8; i++) {
            float h = 2 + (float)Math.abs(Math.sin(t + i * .58f)) * 7;
            p.setColor(i < 5 ? 0xAAF6B83F : 0xAA67E6A4);
            canvas.drawRoundRect(new RectF(97 + i * 4.5f, 83 - h, 100 + i * 4.5f, 83), 2, 2, p);
            canvas.drawRoundRect(new RectF(260 - i * 4.5f, 83 - h, 263 - i * 4.5f, 83), 2, 2, p);
        }
        canvas.restore();
    }
}
