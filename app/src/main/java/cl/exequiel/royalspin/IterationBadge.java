package cl.exequiel.royalspin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

/** Small top overlay that makes every captured iteration traceable. */
public final class IterationBadge extends View implements Choreographer.FrameCallback {
    public static final int LEVEL = 8;
    public static final String LABEL = "v0.8 · ADAPTIVE PERFORMANCE";
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
        p.setColor(0xF0101320);
        canvas.drawRoundRect(new RectF(91, 68, 269, 87), 9, 9, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.1f);
        p.setColor(0xCC67E6A4);
        canvas.drawRoundRect(new RectF(91, 68, 269, 87), 9, 9, p);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create("sans", Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(7.3f);
        p.setColor(0xFFE5FFF1);
        canvas.drawText(LABEL, 180, 80.5f, p);
        float t = SystemClock.uptimeMillis() * .005f;
        for (int i = 0; i < 7; i++) {
            float h = 2 + (float)Math.abs(Math.sin(t + i * .63f)) * 7;
            p.setColor(i < 5 ? 0xAA67E6A4 : 0xAAF6C453);
            canvas.drawRoundRect(new RectF(101 + i * 5, 83 - h, 104 + i * 5, 83), 2, 2, p);
            canvas.drawRoundRect(new RectF(256 - i * 5, 83 - h, 259 - i * 5, 83), 2, 2, p);
        }
        canvas.restore();
    }
}
