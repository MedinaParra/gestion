package cl.exequiel.royalspin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** Replaces internal diagnostics with a stable, fully opaque release footer. */
public final class PremiumReleaseFooterOverlay extends View {
    private static final float W = 360f;
    private static final float H = 800f;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public PremiumReleaseFooterOverlay(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onDraw(Canvas canvas) {
        float scale = Math.min(getWidth() / W, getHeight() / H);
        float offsetX = (getWidth() - W * scale) * .5f;
        float offsetY = (getHeight() - H * scale) * .5f;
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        rect.set(0f, 777f, 360f, 800f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF020308);
        canvas.drawRect(rect, p);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(6.4f);
        p.setColor(0xFF8E96A6);
        canvas.drawText("CALIDAD ADAPTATIVA · CRÉDITOS FICTICIOS", 180f, 792.5f, p);
        canvas.restore();
    }
}
