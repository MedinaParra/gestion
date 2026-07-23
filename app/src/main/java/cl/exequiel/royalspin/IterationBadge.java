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
    public static final int LEVEL = 7;
    public static final String LABEL = "v0.7 · STAGE 2.5D";
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
        canvas.drawRoundRect(new RectF(108, 68, 252, 87), 9, 9, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.1f);
        p.setColor(0xCC5FCBFF);
        canvas.drawRoundRect(new RectF(108, 68, 252, 87), 9, 9, p);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create("sans", Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(7.5f);
        p.setColor(0xFFDFF7FF);
        canvas.drawText(LABEL, 180, 80.5f, p);
        float t = SystemClock.uptimeMillis() * .004f;
        for (int i = 0; i < 4; i++) {
            float dx = (float)Math.sin(t + i * 1.4f) * 4f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1f);
            p.setColor(i % 2 == 0 ? 0x995FCBFF : 0x99F6C453);
            canvas.drawOval(new RectF(117 + i * 6 + dx, 72 + i,
                    126 + i * 6 + dx, 84 - i), p);
            canvas.drawOval(new RectF(234 - i * 6 - dx, 72 + i,
                    243 - i * 6 - dx, 84 - i), p);
        }
        canvas.restore();
    }
}
