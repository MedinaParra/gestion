package cl.exequiel.royalspin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/** Static opaque release plate that replaces every inherited version label. */
public final class PremiumVersionOverlay extends View {
    private static final float W = 360f;
    private static final float H = 800f;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public PremiumVersionOverlay(Context context) {
        super(context);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
    }

    @Override protected void onDraw(Canvas canvas) {
        float scale = Math.min(getWidth() / W, getHeight() / H);
        float offsetX = (getWidth() - W * scale) * .5f;
        float offsetY = (getHeight() - H * scale) * .5f;
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        rect.set(88f, 67.5f, 272f, 89.5f);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{0xFF020308, 0xFF190D22, 0xFF020308}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, 10f, 10f, p);
        p.setShader(null);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.15f);
        p.setColor(0xFFFFD76A);
        canvas.drawRoundRect(rect, 10f, 10f, p);

        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(6.9f);
        p.setColor(0xFFFFE7A0);
        p.setShadowLayer(3f, 0f, 1f, 0x88000000);
        canvas.drawText("v3.0 · ROADMAP COMPLETO", 180f, 82.3f, p);
        p.clearShadowLayer();
        canvas.restore();
    }
}
