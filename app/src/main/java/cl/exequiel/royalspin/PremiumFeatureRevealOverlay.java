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

/** Repairs presentation hierarchy during a Free Spins win reveal. */
public final class PremiumFeatureRevealOverlay extends View implements Choreographer.FrameCallback {
    private static final float W = 360f;
    private static final float H = 800f;

    private final RoyalSpinV2View gameView;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(new Locale("es", "CL"));
    private final Field phaseField;
    private final Field shownWinField;
    private final Field featureField;
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
        if (!"FEATURE_REVEALING".equals(snapshot.phase) || !snapshot.featureActive) return;

        float scale = Math.min(getWidth() / W, getHeight() / H);
        float offsetX = (getWidth() - W * scale) * .5f;
        float offsetY = (getHeight() - H * scale) * .5f;
        long now = SystemClock.uptimeMillis();

        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        // Restore a clean, legible bonus HUD over the generic event title.
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

        // Dedicated premium win ribbon below the reel cabinet.
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
        canvas.restore();
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
        int spinsRemaining;
        int totalFeatureWin;
    }
}
