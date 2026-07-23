package cl.exequiel.royalspin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.Choreographer;
import android.view.View;

/** Symbol-specific VFX layer. It reads presentation state only and never touches game math. */
public final class PremiumCelebrationOverlay extends View implements Choreographer.FrameCallback {
    private static final float REEL_LEFT = 20f;
    private static final float REEL_WIDTH = 60f;
    private static final float REEL_GAP = 4f;
    private static final float REEL_TOP = 183f;
    private static final float CELL_HEIGHT = 84f;

    private final SlotView source;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private boolean running;

    public PremiumCelebrationOverlay(Context context, SlotView source) {
        super(context);
        this.source = source;
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
        if (source == null) return;
        long now = android.os.SystemClock.uptimeMillis();
        SlotView.CelebrationState state = source.getCelebrationState(now);
        if (state == null || !state.active) return;

        float scale = Math.min(getWidth() / 360f, getHeight() / 800f);
        float ox = (getWidth() - 360f * scale) / 2f;
        float oy = (getHeight() - 800f * scale) / 2f;
        canvas.save();
        canvas.translate(ox, oy);
        canvas.scale(scale, scale);

        SymbolAnimationDirector.Frame frame = SymbolAnimationDirector.frame(
                state.symbol, state.count, state.multiplier, state.elapsedMs);
        drawFlash(canvas, frame, state);
        drawCommonEnergy(canvas, frame, state);
        switch (frame.style) {
            case BELL: drawBell(canvas, frame, state); break;
            case BAR: drawBar(canvas, frame, state); break;
            case SEVEN: drawSeven(canvas, frame, state); break;
            case DIAMOND: drawDiamond(canvas, frame, state); break;
            case WILD: drawWild(canvas, frame, state); break;
            case CARD: drawCard(canvas, frame, state); break;
            default: break;
        }
        drawConfetti(canvas, frame, state);
        drawTierTitle(canvas, frame, state);
        canvas.restore();
    }

    private void drawFlash(Canvas c, SymbolAnimationDirector.Frame f, SlotView.CelebrationState s) {
        if (f.flash <= 0f) return;
        int color = accentFor(f.style);
        int alpha = Math.min(105, Math.round(90f * f.flash * f.intensity));
        paint.setShader(new RadialGradient(180, 310, 270,
                new int[]{(alpha << 24) | (color & 0x00FFFFFF), 0x00000000},
                null, Shader.TileMode.CLAMP));
        c.drawCircle(180, 310, 270, paint);
        paint.setShader(null);
    }

    private void drawCommonEnergy(Canvas c, SymbolAnimationDirector.Frame f,
                                  SlotView.CelebrationState s) {
        int color = accentFor(f.style);
        float centerX = 0f, centerY = 0f;
        path.reset();
        for (int reel = 0; reel < s.count; reel++) {
            float x = cellX(reel);
            float y = cellY(s.rows[reel]);
            centerX += x;
            centerY += y;
            if (reel == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        centerX /= Math.max(1, s.count);
        centerY /= Math.max(1, s.count);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f + 2f * f.energy);
        paint.setShader(new LinearGradient(24, centerY, 336, centerY,
                new int[]{0x00FFFFFF, (150 << 24) | (color & 0x00FFFFFF), 0x00FFFFFF},
                null, Shader.TileMode.CLAMP));
        c.drawPath(path, paint);
        paint.setShader(null);

        float radius = 18f + 110f * f.ring;
        int alpha = Math.max(0, Math.round(125f * (1f - f.ring)));
        paint.setColor((alpha << 24) | (color & 0x00FFFFFF));
        paint.setStrokeWidth(2f + f.intensity);
        c.drawOval(new RectF(centerX - radius * 1.7f, centerY - radius,
                centerX + radius * 1.7f, centerY + radius), paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBell(Canvas c, SymbolAnimationDirector.Frame f, SlotView.CelebrationState s) {
        int gold = 0xFFF6B83F;
        for (int reel = 0; reel < s.count; reel++) {
            float cx = cellX(reel), cy = cellY(s.rows[reel]);
            for (int ring = 0; ring < 3; ring++) {
                float p = (f.ring + ring * .23f) % 1f;
                float r = 18 + p * 42;
                int alpha = Math.round(100f * (1f - p));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.2f + ring * .4f);
                paint.setColor((alpha << 24) | (gold & 0x00FFFFFF));
                c.drawArc(new RectF(cx - r * 1.45f, cy - r, cx + r * 1.45f, cy + r),
                        -44, 88, false, paint);
                c.drawArc(new RectF(cx - r * 1.45f, cy - r, cx + r * 1.45f, cy + r),
                        136, 88, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            float sparkY = cy + 25 + (float)Math.sin(s.elapsedMs * .02f + reel) * 4f;
            paint.setColor(0xCCFFF0A0);
            c.drawCircle(cx, sparkY, 2.2f + 1.5f * f.energy, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawBar(Canvas c, SymbolAnimationDirector.Frame f, SlotView.CelebrationState s) {
        float lock = SymbolAnimationDirector.window(s.elapsedMs, 100L, 420L);
        for (int reel = 0; reel < s.count; reel++) {
            float cx = cellX(reel), cy = cellY(s.rows[reel]);
            float gap = 27f * (1f - lock);
            paint.setShader(new LinearGradient(cx - 35, cy, cx + 35, cy,
                    new int[]{0xFF34220A, 0xFFFFD878, 0xFF34220A}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(cx - 34 - gap, cy - 5, cx - 20 - gap, cy + 5), 3, 3, paint);
            c.drawRoundRect(new RectF(cx + 20 + gap, cy - 5, cx + 34 + gap, cy + 5), 3, 3, paint);
            paint.setShader(null);
            for (int spark = 0; spark < 5; spark++) {
                double a = spark * 1.256 + s.elapsedMs * .012 + reel;
                float r = 20 + (spark % 2) * 8;
                paint.setColor(spark % 2 == 0 ? 0xCCFFF0A0 : 0xAAFF7A37);
                c.drawCircle(cx + (float)Math.cos(a) * r, cy + (float)Math.sin(a) * r,
                        1.2f + f.energy, paint);
            }
        }
    }

    private void drawSeven(Canvas c, SymbolAnimationDirector.Frame f, SlotView.CelebrationState s) {
        float slash = SymbolAnimationDirector.window(s.elapsedMs, 120L, 520L);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(18f);
        paint.setShader(new LinearGradient(55, 390, 305, 180,
                new int[]{0x00FF224D, 0xDDFF224D, 0xFFFFD76A, 0x00FF224D},
                null, Shader.TileMode.CLAMP));
        float startX = 30 + 330 * slash;
        c.drawLine(startX - 150, 420, startX + 80, 170, paint);
        paint.setShader(null);
        paint.setStrokeWidth(3f);
        paint.setColor(0xFFFFD36A);
        c.drawLine(startX - 145, 420, startX + 85, 170, paint);
        paint.setStyle(Paint.Style.FILL);

        for (int reel = 0; reel < s.count; reel++) {
            float cx = cellX(reel), cy = cellY(s.rows[reel]);
            float r = 24 + 8 * f.energy;
            paint.setColor(0x55FF224D);
            c.drawCircle(cx, cy, r, paint);
            paint.setColor(0xCCFF4665);
            for (int i = 0; i < 4; i++) {
                double a = s.elapsedMs * .01 + i * Math.PI / 2 + reel;
                c.drawCircle(cx + (float)Math.cos(a) * r, cy + (float)Math.sin(a) * r,
                        1.6f + f.energy, paint);
            }
        }
    }

    private void drawDiamond(Canvas c, SymbolAnimationDirector.Frame f, SlotView.CelebrationState s) {
        for (int reel = 0; reel < s.count; reel++) {
            float cx = cellX(reel), cy = cellY(s.rows[reel]);
            for (int ray = 0; ray < 8; ray++) {
                double a = ray * Math.PI / 4 + s.elapsedMs * .0012;
                float length = 30 + 18 * f.energy;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(ray % 2 == 0 ? 2f : 1f);
                paint.setColor(ray % 2 == 0 ? 0xAA56D8FF : 0x88FFFFFF);
                c.drawLine(cx, cy, cx + (float)Math.cos(a) * length,
                        cy + (float)Math.sin(a) * length, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            for (int shard = 0; shard < 5; shard++) {
                double a = shard * 1.256 + s.elapsedMs * .0025 + reel;
                float r = 29 + shard * 2;
                path.reset();
                float x = cx + (float)Math.cos(a) * r;
                float y = cy + (float)Math.sin(a) * r;
                path.moveTo(x, y - 4);
                path.lineTo(x + 3, y + 2);
                path.lineTo(x - 2, y + 4);
                path.close();
                paint.setColor(shard % 2 == 0 ? 0xCC63DFFF : 0xAA8B65FF);
                c.drawPath(path, paint);
            }
        }
    }

    private void drawWild(Canvas c, SymbolAnimationDirector.Frame f, SlotView.CelebrationState s) {
        float cx = 180, cy = 35 - 7f * f.enter;
        paint.setStyle(Paint.Style.STROKE);
        for (int ray = 0; ray < 16; ray++) {
            double a = ray * Math.PI * 2 / 16 + s.elapsedMs * .0008;
            float inner = 30, outer = 56 + 10 * f.energy;
            paint.setStrokeWidth(ray % 2 == 0 ? 2f : 1f);
            paint.setColor(ray % 2 == 0 ? 0xAAFFD76A : 0x7771DFFF);
            c.drawLine(cx + (float)Math.cos(a) * inner, cy + (float)Math.sin(a) * inner,
                    cx + (float)Math.cos(a) * outer, cy + (float)Math.sin(a) * outer, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        for (int jewel = 0; jewel < 10; jewel++) {
            double a = jewel * Math.PI * 2 / 10 + s.elapsedMs * .0018;
            float rx = 44 + 6 * f.energy, ry = 21 + 3 * f.energy;
            paint.setColor(jewel % 3 == 0 ? 0xDDFF5CA8 : jewel % 3 == 1 ? 0xDD66DFFF : 0xDDFFD76A);
            c.drawCircle(cx + (float)Math.cos(a) * rx, cy + (float)Math.sin(a) * ry,
                    1.8f + f.energy, paint);
        }
        for (int reel = 0; reel < s.count; reel++) {
            float x = cellX(reel), y = cellY(s.rows[reel]);
            paint.setShader(new LinearGradient(cx, cy, x, y,
                    new int[]{0x00FFD76A, 0x88FFD76A, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(Math.min(cx, x) - 1, Math.min(cy, y),
                    Math.max(cx, x) + 1, Math.max(cy, y)), 2, 2, paint);
            paint.setShader(null);
        }
    }

    private void drawCard(Canvas c, SymbolAnimationDirector.Frame f, SlotView.CelebrationState s) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        for (int reel = 0; reel < s.count; reel++) {
            float cx = cellX(reel), cy = cellY(s.rows[reel]);
            float p = (f.ring + reel * .14f) % 1f;
            float r = 22 + p * 24;
            int alpha = Math.round(90f * (1f - p));
            paint.setColor((alpha << 24) | 0x00D28CFF);
            c.drawRoundRect(new RectF(cx - r, cy - r * 1.2f, cx + r, cy + r * 1.2f), 12, 12, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawConfetti(Canvas c, SymbolAnimationDirector.Frame f, SlotView.CelebrationState s) {
        if (f.tier == SymbolAnimationDirector.Tier.SMALL) return;
        int count = f.tier == SymbolAnimationDirector.Tier.ROYAL ? 52
                : f.tier == SymbolAnimationDirector.Tier.MEGA ? 36 : 22;
        int accent = accentFor(f.style);
        for (int i = 0; i < count; i++) {
            float phase = (s.elapsedMs * (.045f + (i % 7) * .003f) + i * 61.7f) % 520f;
            float x = 18 + ((i * 83.3f + s.elapsedMs * (.018f + (i % 3) * .004f)) % 324f);
            float y = 125 + phase;
            float fade = 1f - Math.min(1f, Math.abs(y - 360f) / 310f);
            int alpha = Math.round(155f * fade);
            paint.setColor((alpha << 24) | ((i % 4 == 0 ? 0xFFFFFF : accent) & 0x00FFFFFF));
            c.save();
            c.rotate((s.elapsedMs * .08f + i * 37f) % 360f, x, y);
            c.drawRoundRect(new RectF(x - 2.5f, y - 1f, x + 2.5f, y + 1f), 1, 1, paint);
            c.restore();
        }
    }

    private void drawTierTitle(Canvas c, SymbolAnimationDirector.Frame f, SlotView.CelebrationState s) {
        if (f.tier == SymbolAnimationDirector.Tier.SMALL || s.elapsedMs < 420L) return;
        String title = f.tier == SymbolAnimationDirector.Tier.ROYAL ? "ROYAL WIN"
                : f.tier == SymbolAnimationDirector.Tier.MEGA ? "MEGA WIN" : "BIG WIN";
        float p = SymbolAnimationDirector.window(s.elapsedMs, 420L, 560L);
        float scale = .58f + .42f * SymbolAnimationDirector.easeOutBack(p);
        int accent = accentFor(f.style);
        c.save();
        c.scale(scale, scale, 180, 461);
        paint.setTypeface(Typeface.create("serif", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(f.tier == SymbolAnimationDirector.Tier.ROYAL ? 27f : 23f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);
        paint.setColor(0xDD080A10);
        c.drawText(title, 180, 468, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(110, 442, 250, 474,
                new int[]{0xFFFFF2A8, accent, 0xFFFFD76A}, null, Shader.TileMode.MIRROR));
        c.drawText(title, 180, 468, paint);
        paint.setShader(null);
        c.restore();
    }

    private static float cellX(int reel) {
        return REEL_LEFT + reel * (REEL_WIDTH + REEL_GAP) + REEL_WIDTH / 2f;
    }

    private static float cellY(int row) {
        return REEL_TOP + row * CELL_HEIGHT + CELL_HEIGHT / 2f;
    }

    private static int accentFor(SymbolAnimationDirector.Style style) {
        switch (style) {
            case BELL: return 0xFFF6B83F;
            case BAR: return 0xFFFFC85E;
            case SEVEN: return 0xFFFF3659;
            case DIAMOND: return 0xFF58D8FF;
            case WILD: return 0xFFFFD76A;
            case CARD: return 0xFFD28CFF;
            default: return 0xFFF6C453;
        }
    }
}
