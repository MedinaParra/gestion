package cl.exequiel.royalspin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Choreographer;
import android.view.View;

/** Measures frame cadence and adapts only decorative overlays, never game math. */
public final class AdaptivePerformanceOverlay extends View implements Choreographer.FrameCallback {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final View heavyOverlay;
    private boolean running;
    private long previousNanos;
    private long windowStart;
    private int frames;
    private int slowFrames;
    private int fps = 60;
    private int slowPercent;
    private int qualityTier = 2; // 2 high, 1 balanced, 0 reduced
    private float scale = 1f, offsetX, offsetY;

    public AdaptivePerformanceOverlay(Context context, View heavyOverlay) {
        super(context);
        this.heavyOverlay = heavyOverlay;
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
        if (previousNanos != 0L) {
            long delta = frameTimeNanos - previousNanos;
            frames++;
            if (delta > 22_000_000L) slowFrames++;
        }
        previousNanos = frameTimeNanos;
        if (windowStart == 0L) windowStart = frameTimeNanos;
        long window = frameTimeNanos - windowStart;
        if (window >= 1_000_000_000L) {
            fps = Math.max(1, Math.round(frames * 1_000_000_000f / window));
            slowPercent = frames == 0 ? 0 : Math.round(slowFrames * 100f / frames);
            if (fps < 42 || slowPercent > 24) qualityTier = 0;
            else if (fps < 54 || slowPercent > 12) qualityTier = 1;
            else qualityTier = 2;
            applyBudget();
            frames = slowFrames = 0;
            windowStart = frameTimeNanos;
            invalidate();
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void applyBudget() {
        if (heavyOverlay == null) return;
        heavyOverlay.animate().cancel();
        heavyOverlay.setAlpha(qualityTier == 2 ? 1f : qualityTier == 1 ? .62f : .28f);
    }

    @Override protected void onDraw(Canvas canvas) {
        scale = Math.min(getWidth() / 360f, getHeight() / 800f);
        offsetX = (getWidth() - 360f * scale) / 2f;
        offsetY = (getHeight() - 800f * scale) / 2f;
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xD90B0F18);
        canvas.drawRoundRect(new RectF(245, 94, 344, 109), 7, 7, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(.8f);
        p.setColor(qualityTier == 2 ? 0xAA67E6A4 : qualityTier == 1 ? 0xAAF6C453 : 0xAAFF7A88);
        canvas.drawRoundRect(new RectF(245, 94, 344, 109), 7, 7, p);
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans", Typeface.BOLD));
        p.setTextSize(6.5f);
        p.setColor(0xFFDCE1EA);
        String tier = qualityTier == 2 ? "ALTA" : qualityTier == 1 ? "EQUILIBRADA" : "REDUCIDA";
        canvas.drawText("AUTO " + tier + " · " + fps + " FPS · " + slowPercent + "%", 294.5f, 104.5f, p);
        canvas.restore();
    }
}
