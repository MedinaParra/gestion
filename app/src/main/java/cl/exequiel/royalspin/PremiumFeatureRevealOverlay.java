package cl.exequiel.royalspin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.util.Locale;

/** Final hierarchy and compact-label pass above the living typography layer. */
public final class PremiumFeatureRevealOverlay extends View implements Choreographer.FrameCallback {
    private static final float W = 360f;
    private static final float H = 800f;

    private final RoyalSpinV2View gameView;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(new Locale("es", "CL"));
    private final Field phaseField;
    private final Field shownWinField;
    private final Field featureField;
    private final Field buttonPressedField;
    private boolean frameLoop;

    public PremiumFeatureRevealOverlay(Context context, RoyalSpinV2View gameView) {
        super(context);
        this.gameView = gameView;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        phaseField = field("phase");
        shownWinField = field("shownWin");
        featureField = field("featureController");
        buttonPressedField = field("buttonPressed");
    }

    private static Field field(String name) {
        try {
            Field field = RoyalSpinV2View.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            return null;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        frameLoop = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        frameLoop = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!frameLoop) return;
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void release() {
        frameLoop = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override protected void onDraw(Canvas canvas) {
        Snapshot snapshot = snapshot();
        float scale = Math.min(getWidth() / W, getHeight() / H);
        float offsetX = (getWidth() - W * scale) * .5f;
        float offsetY = (getHeight() - H * scale) * .5f;
        long now = SystemClock.uptimeMillis();

        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        if (snapshot.featureActive) drawCompactFeatureHeading(canvas, now);
        drawCompactActionButton(canvas, snapshot, now);
        if ("FEATURE_REVEALING".equals(snapshot.phase) && snapshot.featureActive) {
            drawFeatureRevealHierarchy(canvas, snapshot, now);
        }
        canvas.restore();
    }

    private void drawCompactFeatureHeading(Canvas canvas, long now) {
        p.setShader(new LinearGradient(78, 526, 282, 555,
                new int[]{0xF7070910, 0xFB101621, 0xF7070910}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(78, 526, 282, 555), 11, 11, p);
        p.setShader(null);
        drawCompactShimmerText(canvas, "ROYAL FREE SPINS", 180, 547, 14f, now,
                0xFFFFD76A, 0xFFFFF3B3);
    }

    private void drawCompactActionButton(Canvas canvas, Snapshot snapshot, long now) {
        boolean ready = "IDLE".equals(snapshot.phase) || "FEATURE_READY".equals(snapshot.phase);
        String label = "IDLE".equals(snapshot.phase) ? "GIRAR"
                : "FEATURE_READY".equals(snapshot.phase) ? "GIRO GRATIS" : "OMITIR ANIMACIÓN";
        float press = snapshot.buttonPressed ? .955f : 1f;
        float breathe = ready ? LivingTypographyMath.breathe(now, 2300L, .014f) : 1f;
        canvas.save();
        canvas.scale(press * breathe, press * breathe, 180, 742);

        float glow = ready ? LivingTypographyMath.glow(now, 2500L, 12f, 22f) : 13f;
        p.setShadowLayer(glow, 0, 6, ready ? 0xDDF6C453 : 0xBB7D55C8);
        p.setShader(new LinearGradient(34, 713, 326, 768,
                ready ? new int[]{0xFFFFE998, 0xFFF2A82C, 0xFFFFD970, 0xFFC67C18}
                        : new int[]{0xFF9A7AE4, 0xFF5B3590, 0xFF835CCB},
                null, Shader.TileMode.MIRROR));
        canvas.drawRoundRect(new RectF(34, 713, 326, 768), 29, 29, p);
        p.clearShadowLayer();
        p.setShader(null);

        float sweep = LivingTypographyMath.shimmer(now, 3400L);
        float sweepX = 8f + sweep * 400f;
        p.setShader(new LinearGradient(sweepX - 45, 713, sweepX + 45, 768,
                new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(35, 714, 325, 767), 28, 28, p);
        p.setShader(null);

        float size = label.length() > 13 ? 13.2f : label.length() > 8 ? 15f : 18f;
        int dark = ready ? 0xFF1A1003 : Color.WHITE;
        drawCompactShimmerText(canvas, label, 180, 750, size, now, dark,
                ready ? 0xFF5A3505 : 0xFFFFFFFF);
        canvas.restore();
    }

    private void drawFeatureRevealHierarchy(Canvas canvas, Snapshot snapshot, long now) {
        // Clean bonus HUD covers the generic reveal label produced by the lower layer.
        p.setShader(new LinearGradient(22, 165, 338, 199,
                new int[]{0xFA07131F, 0xFA12304A, 0xFA07131F}, null, Shader.TileMode.CLAMP));
        p.setShadowLayer(10, 0, 3, 0xAA42CFFF);
        canvas.drawRoundRect(new RectF(22, 165, 338, 199), 15, 15, p);
        p.clearShadowLayer();
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f);
        p.setColor(0xFF58DFFF);
        canvas.drawRoundRect(new RectF(22, 165, 338, 199), 15, 15, p);
        p.setStyle(Paint.Style.FILL);

        text(canvas, "FREE SPINS", 38, 187, 8, 0xFFB6F3FF, Paint.Align.LEFT);
        text(canvas, String.valueOf(snapshot.spinsRemaining), 126, 190, 18,
                0xFFFFDF75, Paint.Align.CENTER);
        text(canvas, "BONUS WIN", 180, 187, 8, 0xFFB6F3FF, Paint.Align.LEFT);
        text(canvas, numbers.format(snapshot.totalFeatureWin) + " CR", 326, 190, 15,
                Color.WHITE, Paint.Align.RIGHT);

        float pulse = LivingTypographyMath.glow(now, 2100L, .76f, 1f);
        p.setShader(new RadialGradient(180, 488, 158,
                new int[]{0x4427E7FF, 0x22FFD76A, 0x00000000}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(180, 488, 158, p);
        p.setShader(null);
        p.setColor((Math.round(pulse * 235f) << 24) | 0x00070A12);
        canvas.drawRoundRect(new RectF(74, 469, 286, 507), 19, 19, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f);
        p.setColor(0xFFFFD76A);
        canvas.drawRoundRect(new RectF(74, 469, 286, 507), 19, 19, p);
        p.setStyle(Paint.Style.FILL);

        drawLivingLabel(canvas, "PREMIO", 180, 486, 14, now);
        text(canvas, numbers.format(snapshot.shownWin) + " CR", 180, 502, 10,
                0xFFFFEAB0, Paint.Align.CENTER);
    }

    private void drawCompactShimmerText(Canvas canvas, String value, float x, float y,
                                        float size, long now, int baseColor, int highlightColor) {
        float pulse = LivingTypographyMath.breathe(now, 3000L, .012f);
        canvas.save();
        canvas.scale(pulse, pulse, x, y - size * .35f);
        float width = Math.max(30f, measure(value, size));
        float q = LivingTypographyMath.shimmer(now, 3900L);
        float sweepX = x - width * .85f + q * width * 1.7f;
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextSize(size);
        p.setTextAlign(Paint.Align.CENTER);
        p.setShader(new LinearGradient(sweepX - width * .20f, y - size,
                sweepX + width * .20f, y + 2,
                new int[]{baseColor, highlightColor, baseColor},
                null, Shader.TileMode.CLAMP));
        p.setShadowLayer(7, 0, 2, (baseColor & 0x00FFFFFF) | 0x88000000);
        canvas.drawText(value, x, y, p);
        p.clearShadowLayer();
        p.setShader(null);
        canvas.restore();
    }

    private float measure(String value, float size) {
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextSize(size);
        return p.measureText(value);
    }

    private void drawLivingLabel(Canvas canvas, String value, float centerX, float baseline,
                                 float size, long now) {
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextSize(size);
        p.setTextAlign(Paint.Align.LEFT);
        float tracking = .65f;
        float width = 0f;
        for (int i = 0; i < value.length(); i++) width += p.measureText(String.valueOf(value.charAt(i)));
        width += tracking * (value.length() - 1);
        float x = centerX - width * .5f;
        float sweep = LivingTypographyMath.shimmer(now, 2700L);
        float sweepX = x - width * .4f + sweep * width * 1.8f;
        p.setShader(new LinearGradient(sweepX, baseline - size, sweepX + width * .55f, baseline,
                new int[]{0xFFC67C18, 0xFFFFF5B5, 0xFFFFC43E, 0xFFB46911},
                null, Shader.TileMode.CLAMP));
        p.setShadowLayer(9, 0, 2, 0xCCF6C453);
        for (int i = 0; i < value.length(); i++) {
            String letter = String.valueOf(value.charAt(i));
            float y = baseline + LivingTypographyMath.letterWave(now, i, 2400L, .65f);
            canvas.drawText(letter, x, y, p);
            x += p.measureText(letter) + tracking;
        }
        p.clearShadowLayer();
        p.setShader(null);
    }

    private void text(Canvas canvas, String value, float x, float y, float size,
                      int color, Paint.Align align) {
        p.setShader(null);
        p.setTextSize(size);
        p.setTextAlign(align);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setColor(color);
        p.setShadowLayer(3, 0, 1, 0x77000000);
        canvas.drawText(value, x, y, p);
        p.clearShadowLayer();
    }

    private Snapshot snapshot() {
        Snapshot snapshot = new Snapshot();
        try {
            snapshot.phase = String.valueOf(phaseField.get(gameView));
            snapshot.shownWin = shownWinField.getInt(gameView);
            snapshot.buttonPressed = buttonPressedField.getBoolean(gameView);
            FeatureSessionController feature = (FeatureSessionController) featureField.get(gameView);
            if (feature != null) {
                snapshot.featureActive = feature.isActive();
                snapshot.spinsRemaining = feature.spinsRemaining();
                snapshot.totalFeatureWin = feature.totalFeatureWin();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            snapshot.phase = "IDLE";
        }
        return snapshot;
    }

    private static final class Snapshot {
        String phase = "IDLE";
        int shownWin;
        boolean featureActive;
        boolean buttonPressed;
        int spinsRemaining;
        int totalFeatureWin;
    }
}
