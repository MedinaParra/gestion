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
    public static final int LEVEL = 6;
    public static final String LABEL = "v0.6 · CINEMATIC AUDIO";
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
        canvas.drawRoundRect(new RectF(103, 68, 257, 87), 9, 9, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.1f);
        p.setColor(0xCCF6C453);
        canvas.drawRoundRect(new RectF(103, 68, 257, 87), 9, 9, p);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create("sans", Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(7.5f);
        p.setColor(0xFFFFE7A1);
        canvas.drawText(LABEL, 180, 80.5f, p);
        float t = SystemClock.uptimeMillis() * .012f;
        for (int i = 0; i < 5; i++) {
            float h = 2.5f + (float)Math.abs(Math.sin(t + i * .9f)) * 6f;
            p.setColor(i % 2 == 0 ? 0xCCF6C453 : 0xAA8B65FF);
            canvas.drawRoundRect(new RectF(113 + i * 7, 82 - h, 116 + i * 7, 82), 2, 2, p);
            canvas.drawRoundRect(new RectF(244 - i * 7, 82 - h, 247 - i * 7, 82), 2, 2, p);
        }
        canvas.restore();
    }
}
