package cl.exequiel.royalspin;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieDrawable;

import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Presentation-only layer that keeps Royal Spin visually alive while idle.
 * It never changes RNG, credits, payouts or the free-spin state.
 */
public final class PremiumTypographyOverlay extends View implements Choreographer.FrameCallback {
    private static final float W = 360f;
    private static final float H = 800f;

    private final RoyalSpinV2View gameView;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(new Locale("es", "CL"));
    private final Map<String, CounterChannel> counters = new HashMap<>();
    private final CounterChannel buttonScale;
    private final LottieDrawable ambient = new LottieDrawable();

    private final Field phaseField;
    private final Field creditsField;
    private final Field betField;
    private final Field roundsField;
    private final Field shownWinField;
    private final Field messageField;
    private final Field featureField;
    private final Field soundField;
    private final Field reducedField;
    private final Field qualityField;
    private final Field buttonPressedField;

    private boolean frameLoop;
    private boolean ambientReady;
    private boolean lastPressed;
    private float scale = 1f;
    private float offsetX;
    private float offsetY;

    public PremiumTypographyOverlay(Context context, RoyalSpinV2View gameView) {
        super(context);
        this.gameView = gameView;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);

        phaseField = field("phase");
        creditsField = field("credits");
        betField = field("betPerLine");
        roundsField = field("rounds");
        shownWinField = field("shownWin");
        messageField = field("message");
        featureField = field("featureController");
        soundField = field("soundEnabled");
        reducedField = field("reducedMotion");
        qualityField = field("qualityTier");
        buttonPressedField = field("buttonPressed");

        buttonScale = new CounterChannel(this::invalidate, 1f, 560f, .70f);

        ambient.setCallback(this);
        ambient.setRepeatCount(ValueAnimator.INFINITE);
        ambient.setRepeatMode(ValueAnimator.RESTART);
        ambient.setSpeed(.58f);
        LottieCompositionFactory.fromRawRes(context, R.raw.royal_idle_sparkles)
                .addListener(composition -> {
                    ambient.setComposition(composition);
                    ambientReady = true;
                    if (isAttachedToWindow()) ambient.start();
                    invalidate();
                });
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
        if (ambientReady) ambient.start();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        frameLoop = false;
        ambient.cancelAnimation();
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
        ambient.cancelAnimation();
        Choreographer.getInstance().removeFrameCallback(this);
        for (CounterChannel channel : counters.values()) channel.cancel();
        buttonScale.cancel();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        Snapshot s = snapshot();
        scale = Math.min(getWidth() / W, getHeight() / H);
        offsetX = (getWidth() - W * scale) / 2f;
        offsetY = (getHeight() - H * scale) / 2f;

        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        if (ambientReady && !s.reducedMotion && s.qualityTier > 0) {
            ambient.setBounds(0, 0, (int) W, (int) H);
            ambient.setAlpha(s.qualityTier == 2 ? 145 : 80);
            ambient.draw(canvas);
        }

        drawPremiumHeader(canvas, s, now);
        drawPremiumStats(canvas, s, now);
        drawPremiumControls(canvas, s, now);
        drawEventTypography(canvas, s, now);
        canvas.restore();
    }

    private void drawPremiumHeader(Canvas c, Snapshot s, long now) {
        p.setShader(new LinearGradient(75, 5, 285, 90,
                new int[]{0xF0020308, 0xEC0A0717, 0xF0020308}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(76, 5, 284, 88), 23, 23, p);
        p.setShader(null);

        float crownScale = s.reducedMotion ? 1f : LivingTypographyMath.breathe(now, 3100L, .045f);
        c.save();
        c.scale(crownScale, crownScale, 180, 25);
        drawCrown(c, 180, 25, 18, 0xFFFFD76A);
        c.restore();
        drawCrownSparkles(c, now, s.reducedMotion);

        drawLivingWord(c, "ROYAL SPIN", 180, 61, 25, now,
                s.reducedMotion ? 0f : 2.1f, s.reducedMotion ? .2f : .85f);
        drawMicroLabel(c, "v2.1 · LIVING TYPOGRAPHY", 180, 80, 7.2f,
                0xFFFFE7A0, now, 0, Paint.Align.CENTER, s.reducedMotion);
    }

    private void drawPremiumStats(Canvas c, Snapshot s, long now) {
        mask(c, 20, 103, 340, 151, 0xF2080B12, 10);
        drawMicroLabel(c, "SALDO", 30, 115, 8, 0xFFAFB7C8,
                now, 1, Paint.Align.LEFT, s.reducedMotion);
        drawCounter(c, "credits", numbers.format(s.credits) + " CR", s.credits,
                30, 143, 21, Color.WHITE, Paint.Align.LEFT, now);

        drawMicroLabel(c, "RTP TOTAL", 231, 115, 8, 0xFFAFB7C8,
                now, 2, Paint.Align.CENTER, s.reducedMotion);
        drawCounter(c, "rtp", "95,48%", 9548, 231, 142, 15,
                0xFFFFCF67, Paint.Align.CENTER, now);
        drawMicroLabel(c, "RONDA " + s.rounds, 330, 143, 8, 0xFFAFB7C8,
                now, 3, Paint.Align.RIGHT, s.reducedMotion);

        if (s.featureActive) {
            mask(c, 25, 172, 335, 195, 0xF20A1626, 8);
            drawMicroLabel(c, "FREE SPINS", 38, 188, 8, 0xFF9FEFFF,
                    now, 4, Paint.Align.LEFT, s.reducedMotion);
            drawCounter(c, "spins", String.valueOf(s.spinsRemaining), s.spinsRemaining,
                    126, 190, 18, 0xFFFFDF75, Paint.Align.CENTER, now);
            drawMicroLabel(c, "BONUS WIN", 180, 188, 8, 0xFF9FEFFF,
                    now, 5, Paint.Align.LEFT, s.reducedMotion);
            drawCounter(c, "bonus", numbers.format(s.totalFeatureWin) + " CR",
                    s.totalFeatureWin, 326, 190, 15, Color.WHITE, Paint.Align.RIGHT, now);
        }
    }

    private void drawPremiumControls(Canvas c, Snapshot s, long now) {
        if (!s.featureActive) {
            mask(c, 85, 520, 275, 545, 0xF0080B12, 8);
            drawMicroLabel(c, "APUESTA POR LÍNEA", 180, 537, 9, 0xFFB8BECA,
                    now, 6, Paint.Align.CENTER, s.reducedMotion);
            mask(c, 108, 555, 252, 594, 0xF0090B10, 10);
            drawCounter(c, "bet", s.betPerLine + " CR", s.betPerLine,
                    180, 584, 22, 0xFFFFD76A, Paint.Align.CENTER, now);
            mask(c, 28, 617, 156, 661, 0xF0080B12, 8);
            drawMicroLabel(c, "APUESTA TOTAL", 35, 630, 8, 0xFFAFB7C8,
                    now, 7, Paint.Align.LEFT, s.reducedMotion);
            drawCounter(c, "totalBet", numbers.format(s.betPerLine * 20) + " CR",
                    s.betPerLine * 20, 35, 654, 16, Color.WHITE, Paint.Align.LEFT, now);
        } else {
            mask(c, 25, 523, 335, 665, 0xF0080B12, 16);
            drawLivingWord(c, "ROYAL FREE SPINS", 180, 544, 14, now,
                    s.reducedMotion ? 0f : 1.0f, .35f);
            drawMicroLabel(c, "TIRADAS JUGADAS", 35, 581, 8, 0xFF9FEFFF,
                    now, 8, Paint.Align.LEFT, s.reducedMotion);
            drawCounter(c, "played", String.valueOf(s.spinsPlayed), s.spinsPlayed,
                    35, 608, 18, Color.WHITE, Paint.Align.LEFT, now);
            drawMicroLabel(c, "RETRIGGERS", 325, 581, 8, 0xFF9FEFFF,
                    now, 9, Paint.Align.RIGHT, s.reducedMotion);
            drawCounter(c, "retrigger", String.valueOf(s.retriggers), s.retriggers,
                    325, 608, 18, Color.WHITE, Paint.Align.RIGHT, now);
            drawMicroLabel(c, "APUESTA BLOQUEADA", 180, 635, 8, 0xFF9FEFFF,
                    now, 10, Paint.Align.CENTER, s.reducedMotion);
            drawCounter(c, "locked", numbers.format(s.lockedBet * 20) + " CR",
                    s.lockedBet * 20, 180, 660, 15, 0xFFFFDF75, Paint.Align.CENTER, now);
        }

        mask(c, 24, 681, 336, 705, 0xE8070910, 8);
        drawMicroLabel(c, s.message, 180, 699, 9.2f, 0xFFF1F2F7,
                now, 11, Paint.Align.CENTER, s.reducedMotion);
        drawPremiumActionButton(c, s, now);
    }

    private void drawPremiumActionButton(Canvas c, Snapshot s, long now) {
        boolean ready = "IDLE".equals(s.phase) || "FEATURE_READY".equals(s.phase);
        boolean pressed = s.buttonPressed;
        if (pressed != lastPressed) {
            lastPressed = pressed;
            buttonScale.animateTo(pressed ? .94f : 1f,
                    pressed ? 900f : 520f, pressed ? .72f : .62f);
        }
        float pulse = ready && !s.reducedMotion
                ? LivingTypographyMath.breathe(now, 2300L, .018f) : 1f;
        float totalScale = buttonScale.value() * pulse;
        c.save();
        c.scale(totalScale, totalScale, 180, 742);

        float glow = ready ? LivingTypographyMath.glow(now, 2500L, 12f, 25f) : 10f;
        p.setShadowLayer(glow, 0, 6, ready ? 0xDDF6C453 : 0xAA6850B8);
        p.setShader(new LinearGradient(34, 713, 326, 768,
                ready ? new int[]{0xFFFFE998, 0xFFF2A82C, 0xFFFFD970, 0xFFC67C18}
                        : new int[]{0xFF8D6ADF, 0xFF4C2A79, 0xFF7650BE},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(34, 713, 326, 768), 29, 29, p);
        p.clearShadowLayer();
        p.setShader(null);

        float sweep = LivingTypographyMath.shimmer(now, 3600L);
        float x = 20f + sweep * 360f;
        p.setShader(new LinearGradient(x - 38, 713, x + 38, 768,
                new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(35, 714, 325, 767), 28, 28, p);
        p.setShader(null);

        String label = "IDLE".equals(s.phase) ? "GIRAR"
                : "FEATURE_READY".equals(s.phase) ? "GIRO GRATIS" : "OMITIR ANIMACIÓN";
        drawActionWord(c, label, 180, 750, ready ? 18 : 13.3f,
                ready ? 0xFF1B1003 : Color.WHITE, now, s.reducedMotion);
        c.restore();
    }

    private void drawEventTypography(Canvas c, Snapshot s, long now) {
        String title = null;
        String subtitle = null;
        if ("FEATURE_INTRO".equals(s.phase)) {
            title = "ROYAL FEATURE";
            subtitle = "30 JUEGOS GRATIS";
        } else if ("RETRIGGER".equals(s.phase)) {
            title = "+10 JUEGOS GRATIS";
            subtitle = "ROYAL RETRIGGER";
        } else if ("FEATURE_SUMMARY".equals(s.phase)) {
            title = "BONUS TOTAL";
            subtitle = numbers.format(s.totalFeatureWin) + " CR";
        } else if (("BASE_REVEALING".equals(s.phase) || "FEATURE_REVEALING".equals(s.phase))
                && s.shownWin > 0) {
            title = "PREMIO";
            subtitle = numbers.format(s.shownWin) + " CR";
        }
        if (title == null) return;

        long elapsed = now % 6000L;
        if (!s.reducedMotion) {
            p.setShader(new RadialGradient(180, 170, 155,
                    new int[]{0x3300DFFF, 0x22FFD76A, 0x00000000}, null, Shader.TileMode.CLAMP));
            c.drawCircle(180, 170, 155, p);
            p.setShader(null);
        }
        drawStaggeredEvent(c, title, 180, 166, title.length() > 14 ? 20 : 25,
                elapsed, s.reducedMotion);
        drawMicroLabel(c, subtitle, 180, 188, 10, 0xFFFFE8A2,
                now, 12, Paint.Align.CENTER, s.reducedMotion);
    }

    private void drawLivingWord(Canvas c, String value, float centerX, float baseline,
                                float size, long now, float waveAmplitude, float rotationAmplitude) {
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextSize(size);
        float tracking = LivingTypographyMath.tracking(now, 5200L, .65f, 1.15f);
        float width = measuredWidth(value, tracking);
        float left = centerX - width / 2f;
        float shimmer = LivingTypographyMath.shimmer(now, 4300L);
        float sweepX = left - width * .6f + shimmer * width * 2.2f;
        Shader gold = new LinearGradient(sweepX, baseline - size, sweepX + width * .52f, baseline + 4,
                new int[]{0xFFC17A16, 0xFFFFF7C0, 0xFFFFD25A, 0xFF9D5910},
                new float[]{0f, .43f, .61f, 1f}, Shader.TileMode.CLAMP);

        float x = left;
        for (int i = 0; i < value.length(); i++) {
            String letter = String.valueOf(value.charAt(i));
            float charWidth = p.measureText(letter);
            float y = baseline + LivingTypographyMath.letterWave(now, i, 3100L, waveAmplitude);
            float letterScale = 1f + .018f * (float) Math.sin(now * .0027f + i * .7f);
            float rotation = rotationAmplitude * (float) Math.sin(now * .0019f + i * .63f);
            float cx = x + charWidth / 2f;
            c.save();
            c.rotate(rotation, cx, y - size * .35f);
            c.scale(letterScale, letterScale, cx, y - size * .35f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, size * .065f));
            p.setShader(null);
            p.setColor(0xFF6C3C08);
            p.setShadowLayer(10, 0, 3, 0xCCF6C453);
            c.drawText(letter, x, y, p);
            p.setStyle(Paint.Style.FILL);
            p.setShader(gold);
            p.setColor(Color.WHITE);
            c.drawText(letter, x, y, p);
            p.clearShadowLayer();
            p.setShader(null);
            c.restore();
            x += charWidth + tracking;
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawActionWord(Canvas c, String value, float centerX, float baseline,
                                float size, int color, long now, boolean reduced) {
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextSize(size);
        float tracking = reduced ? .8f : LivingTypographyMath.tracking(now, 2200L, .8f, 1.7f);
        float width = measuredWidth(value, tracking);
        float x = centerX - width / 2f;
        for (int i = 0; i < value.length(); i++) {
            String letter = String.valueOf(value.charAt(i));
            float cw = p.measureText(letter);
            float lift = reduced ? 0f : LivingTypographyMath.letterWave(now, i, 1800L, 1.2f);
            p.setColor(color);
            p.setShadowLayer(readyColor(color) ? 5 : 3, 0, 2,
                    readyColor(color) ? 0x88FFF2AD : 0x66000000);
            c.drawText(letter, x, baseline + lift, p);
            p.clearShadowLayer();
            x += cw + tracking;
        }
    }

    private static boolean readyColor(int color) {
        return color != Color.WHITE;
    }

    private void drawMicroLabel(Canvas c, String value, float x, float y, float size, int color,
                                long now, int index, Paint.Align align, boolean reduced) {
        if (value == null) value = "";
        float floatY = reduced ? 0f : LivingTypographyMath.letterWave(now, index, 4200L, .75f);
        float alpha = reduced ? 1f : LivingTypographyMath.glow(now + index * 170L,
                3600L, .78f, 1f);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextSize(size);
        p.setTextAlign(align);
        p.setColor((Math.round(alpha * 255f) << 24) | (color & 0x00FFFFFF));
        p.setShadowLayer(3.2f, 0, 1.2f, 0x66000000);
        c.drawText(value, x, y + floatY, p);
        p.clearShadowLayer();
    }

    private void drawCounter(Canvas c, String key, String label, int value, float x, float y,
                             float size, int color, Paint.Align align, long now) {
        CounterChannel channel = counters.get(key);
        if (channel == null) {
            channel = new CounterChannel(this::invalidate, 1f, 470f, .55f);
            counters.put(key, channel);
        }
        channel.observe(value);
        float flash = channel.flash(now);
        c.save();
        c.scale(channel.value(), channel.value(), x, y - size * .35f);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextSize(size);
        p.setTextAlign(align);
        p.setColor(color);
        p.setShadowLayer(4f + flash * 13f, 0, 2,
                flash > 0f ? 0xCCFFD76A : 0x77000000);
        c.drawText(label, x, y, p);
        p.clearShadowLayer();
        c.restore();
    }

    private void drawStaggeredEvent(Canvas c, String value, float centerX, float baseline,
                                    float size, long elapsed, boolean reduced) {
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextSize(size);
        float tracking = 1.2f;
        float width = measuredWidth(value, tracking);
        float x = centerX - width / 2f;
        for (int i = 0; i < value.length(); i++) {
            String letter = String.valueOf(value.charAt(i));
            float cw = p.measureText(letter);
            float progress = reduced ? 1f : LivingTypographyMath.stagger(elapsed, i, 42L, 480L);
            float lift = (1f - progress) * 20f;
            c.save();
            c.scale(.55f + .45f * progress, .55f + .45f * progress,
                    x + cw / 2f, baseline - size * .3f);
            p.setAlpha(Math.round(255f * LivingTypographyMath.clamp(progress)));
            p.setShadowLayer(18, 0, 4, 0xDDF6C453);
            p.setShader(new LinearGradient(x, baseline - size, x + cw, baseline,
                    new int[]{0xFFFFF5B5, 0xFFFFBD3D, 0xFFFFE98B},
                    null, Shader.TileMode.CLAMP));
            c.drawText(letter, x, baseline + lift, p);
            p.setShader(null);
            p.clearShadowLayer();
            p.setAlpha(255);
            c.restore();
            x += cw + tracking;
        }
    }

    private float measuredWidth(String value, float tracking) {
        float width = 0f;
        for (int i = 0; i < value.length(); i++) width += p.measureText(String.valueOf(value.charAt(i)));
        return width + Math.max(0, value.length() - 1) * tracking;
    }

    private void drawCrownSparkles(Canvas c, long now, boolean reduced) {
        int count = reduced ? 2 : 5;
        for (int i = 0; i < count; i++) {
            double angle = now * .0012d + i * Math.PI * 2d / count;
            float radius = 22f + 4f * (float) Math.sin(now * .002d + i);
            float x = 180f + (float) Math.cos(angle) * radius;
            float y = 25f + (float) Math.sin(angle) * radius * .55f;
            float alpha = .3f + .7f * (.5f + .5f * (float) Math.sin(now * .004f + i));
            p.setColor((Math.round(alpha * 210f) << 24) | 0x00FFE898);
            c.drawCircle(x, y, i % 2 == 0 ? 1.8f : 1.1f, p);
        }
    }

    private void drawCrown(Canvas c, float cx, float cy, float size, int color) {
        path.reset();
        path.moveTo(cx - size * .55f, cy + size * .32f);
        path.lineTo(cx - size * .48f, cy - size * .28f);
        path.lineTo(cx - size * .14f, cy + size * .02f);
        path.lineTo(cx, cy - size * .5f);
        path.lineTo(cx + size * .16f, cy + size * .02f);
        path.lineTo(cx + size * .5f, cy - size * .28f);
        path.lineTo(cx + size * .55f, cy + size * .32f);
        path.close();
        p.setShader(new LinearGradient(cx - size, cy - size, cx + size, cy + size,
                new int[]{0xFFC17B17, 0xFFFFF4B0, color, 0xFFB46911},
                null, Shader.TileMode.CLAMP));
        p.setShadowLayer(11, 0, 3, 0xCCF6C453);
        c.drawPath(path, p);
        c.drawRoundRect(new RectF(cx - size * .57f, cy + size * .36f,
                cx + size * .57f, cy + size * .49f), size * .05f, size * .05f, p);
        p.clearShadowLayer();
        p.setShader(null);
    }

    private void mask(Canvas c, float l, float t, float r, float b, int color, float radius) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, p);
    }

    private Snapshot snapshot() {
        Snapshot s = new Snapshot();
        try {
            s.phase = String.valueOf(phaseField.get(gameView));
            s.credits = creditsField.getInt(gameView);
            s.betPerLine = betField.getInt(gameView);
            s.rounds = roundsField.getInt(gameView);
            s.shownWin = shownWinField.getInt(gameView);
            s.message = String.valueOf(messageField.get(gameView));
            s.soundEnabled = soundField.getBoolean(gameView);
            s.reducedMotion = reducedField.getBoolean(gameView);
            s.qualityTier = qualityField.getInt(gameView);
            s.buttonPressed = buttonPressedField.getBoolean(gameView);
            FeatureSessionController feature = (FeatureSessionController) featureField.get(gameView);
            if (feature != null) {
                s.featureActive = feature.isActive();
                s.spinsRemaining = feature.spinsRemaining();
                s.totalFeatureWin = feature.totalFeatureWin();
                s.spinsPlayed = feature.spinsPlayed();
                FeatureState state = feature.snapshot();
                s.retriggers = state.retriggerCount;
                s.lockedBet = feature.lockedBetPerLine();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            s.phase = "IDLE";
            s.credits = 5000;
            s.betPerLine = 1;
            s.message = "3 WILD en línea activan 30 juegos gratis";
            s.qualityTier = 1;
        }
        return s;
    }

    private static final class Snapshot {
        String phase = "IDLE";
        int credits;
        int betPerLine = 1;
        int rounds;
        int shownWin;
        String message = "";
        boolean soundEnabled;
        boolean reducedMotion;
        boolean buttonPressed;
        int qualityTier = 2;
        boolean featureActive;
        int spinsRemaining;
        int totalFeatureWin;
        int spinsPlayed;
        int retriggers;
        int lockedBet = 1;
    }

    private static final class CounterChannel {
        private final Runnable invalidator;
        private final FloatValueHolder holder;
        private final SpringAnimation spring;
        private int observed = Integer.MIN_VALUE;
        private long changedAt;

        CounterChannel(Runnable invalidator, float initial, float stiffness, float damping) {
            this.invalidator = invalidator;
            holder = new FloatValueHolder(initial);
            spring = new SpringAnimation(holder);
            spring.setSpring(new SpringForce(initial)
                    .setStiffness(stiffness)
                    .setDampingRatio(damping));
            spring.addUpdateListener((animation, value, velocity) -> invalidator.run());
        }

        void observe(int value) {
            if (observed == Integer.MIN_VALUE) {
                observed = value;
                return;
            }
            if (observed != value) {
                observed = value;
                changedAt = SystemClock.uptimeMillis();
                spring.cancel();
                spring.setStartValue(1.18f);
                spring.getSpring().setFinalPosition(1f);
                spring.start();
            }
        }

        void animateTo(float target, float stiffness, float damping) {
            spring.getSpring().setStiffness(stiffness).setDampingRatio(damping);
            spring.animateToFinalPosition(target);
        }

        float value() {
            return holder.getValue();
        }

        float flash(long now) {
            return LivingTypographyMath.flash(now - changedAt, 620L);
        }

        void cancel() {
            spring.cancel();
        }
    }
}
