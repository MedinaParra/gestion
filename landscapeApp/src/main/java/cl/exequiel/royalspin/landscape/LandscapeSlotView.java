package cl.exequiel.royalspin.landscape;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Full-screen landscape renderer. The math result is fixed before the reels animate. */
public final class LandscapeSlotView extends View implements Choreographer.FrameCallback {
    private static final float DESIGN_W = 1280f;
    private static final float DESIGN_H = 720f;
    private static final float REEL_X = 205f;
    private static final float REEL_Y = 145f;
    private static final float REEL_W = 790f;
    private static final float REEL_H = 450f;
    private static final float CELL_W = REEL_W / 5f;
    private static final float CELL_H = REEL_H / 3f;

    private static final int IDLE = 0;
    private static final int SPINNING = 1;
    private static final int REVEALING = 2;
    private static final int FEATURE_INTRO = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final Random random = new Random(0x524f59414cL);
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(new Locale("es", "CL"));
    private final Map<Integer, Bitmap> symbols;
    private final Bitmap logo;
    private final LandscapeAudioEngine audio = new LandscapeAudioEngine();
    private final List<Particle> particles = new ArrayList<>();

    private LandscapeSlotEngine.SpinResult current;
    private LandscapeSlotEngine.SpinResult pending;
    private int phase = IDLE;
    private long phaseStart;
    private long lastFrame;
    private int credits = 5000;
    private int betPerLine = 5;
    private int rounds;
    private int freeSpins;
    private int shownWin;
    private int targetWin;
    private boolean reducedMotion;
    private boolean attached;
    private boolean pressedSpin;
    private float scale = 1f;
    private float offsetX;
    private float offsetY;
    private final String demo;

    private final long[] stopTimes = {900, 1160, 1430, 1730, 2110};
    private final long revealDuration = 3600L;

    public LandscapeSlotView(Context context, String demo) {
        super(context);
        this.demo = demo == null ? "" : demo;
        setFocusable(true);
        setClickable(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        symbols = IconAtlas.loadSymbols();
        logo = IconAtlas.loadLogo();
        current = LandscapeSlotEngine.demo("idle", betPerLine);

        if (!this.demo.isEmpty()) {
            if ("feature".equals(this.demo)) {
                pending = LandscapeSlotEngine.demo("feature", betPerLine);
                current = pending;
                freeSpins = 30;
                phase = FEATURE_INTRO;
                phaseStart = SystemClock.uptimeMillis() - 900L;
                targetWin = pending.payout;
            } else {
                current = LandscapeSlotEngine.demo(this.demo, betPerLine);
                targetWin = current.payout;
                shownWin = targetWin;
                phase = REVEALING;
                phaseStart = SystemClock.uptimeMillis() - 720L;
            }
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    public void onHostResume() {
        if (!attached) return;
        Choreographer.getInstance().removeFrameCallback(this);
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void onHostPause() { pressedSpin = false; }

    public void release() {
        attached = false;
        Choreographer.getInstance().removeFrameCallback(this);
        audio.release();
        for (Bitmap bitmap : symbols.values()) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        if (logo != null && !logo.isRecycled()) logo.recycle();
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!attached) return;
        long now = frameTimeNanos / 1_000_000L;
        if (lastFrame == 0L) lastFrame = now;
        float dt = Math.min(0.04f, Math.max(0f, (now - lastFrame) / 1000f));
        lastFrame = now;
        update(now, dt);
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void update(long now, float dt) {
        long elapsed = now - phaseStart;
        if (phase == SPINNING) {
            for (int reel = 0; reel < stopTimes.length; reel++) {
                long crossing = stopTimes[reel];
                long previous = now - Math.round(dt * 1000f) - phaseStart;
                if (previous < crossing && elapsed >= crossing) {
                    audio.play(LandscapeAudioEngine.Cue.values()[LandscapeAudioEngine.Cue.STOP_1.ordinal() + reel]);
                    spawnStopBurst(reel);
                }
            }
            if (elapsed >= stopTimes[4] + 420L) finishSpin(now);
        } else if (phase == REVEALING) {
            if (targetWin > 0) {
                float t = clamp(elapsed / 1150f, 0f, 1f);
                shownWin = Math.round(targetWin * easeOutCubic(t));
            }
            if (elapsed >= revealDuration && demo.isEmpty()) {
                phase = IDLE;
                shownWin = targetWin;
                phaseStart = now;
            }
        } else if (phase == FEATURE_INTRO) {
            float t = clamp(elapsed / 1500f, 0f, 1f);
            shownWin = Math.round(targetWin * easeOutCubic(t));
            if (elapsed > 3100L && demo.isEmpty()) {
                phase = REVEALING;
                phaseStart = now;
            }
        }

        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle p = iterator.next();
            p.life -= dt;
            if (p.life <= 0f) {
                iterator.remove();
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vy += p.gravity * dt;
            p.rotation += p.spin * dt;
        }
    }

    private void startSpin(long now) {
        if (phase != IDLE) return;
        boolean free = freeSpins > 0;
        int totalBet = betPerLine * LandscapeSlotEngine.LINES;
        if (!free && credits < totalBet) return;
        if (!free) credits -= totalBet;
        else freeSpins--;

        pending = LandscapeSlotEngine.spin(random, betPerLine);
        phase = SPINNING;
        phaseStart = now;
        shownWin = 0;
        targetWin = 0;
        rounds++;
        particles.clear();
        audio.play(LandscapeAudioEngine.Cue.SPIN);
    }

    private void finishSpin(long now) {
        current = pending;
        pending = null;
        targetWin = current.payout;
        credits += current.payout;

        if (current.freeSpinsTriggered) {
            freeSpins = Math.min(90, freeSpins + 30);
            phase = FEATURE_INTRO;
            audio.play(LandscapeAudioEngine.Cue.FEATURE);
            spawnFeatureBurst();
        } else {
            phase = REVEALING;
            playWinCue();
            spawnWinBurst();
        }
        phaseStart = now;
    }

    private void playWinCue() {
        if (current == null || current.wins.isEmpty()) return;
        int symbol = current.wins.get(0).symbol;
        switch (symbol) {
            case LandscapeSlotEngine.BELL: audio.play(LandscapeAudioEngine.Cue.BELL); break;
            case LandscapeSlotEngine.BAR: audio.play(LandscapeAudioEngine.Cue.BAR); break;
            case LandscapeSlotEngine.SEVEN: audio.play(LandscapeAudioEngine.Cue.SEVEN); break;
            case LandscapeSlotEngine.DIAMOND: audio.play(LandscapeAudioEngine.Cue.DIAMOND); break;
            case LandscapeSlotEngine.WILD: audio.play(LandscapeAudioEngine.Cue.WILD); break;
            default: audio.play(LandscapeAudioEngine.Cue.SMALL_WIN); break;
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        scale = Math.min(getWidth() / DESIGN_W, getHeight() / DESIGN_H);
        offsetX = (getWidth() - DESIGN_W * scale) * 0.5f;
        offsetY = (getHeight() - DESIGN_H * scale) * 0.5f;

        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        drawBackground(canvas, now);
        drawHeader(canvas, now);
        drawLeftPanel(canvas, now);
        drawReelCabinet(canvas, now);
        drawRightPanel(canvas, now);
        drawParticles(canvas);
        drawWinOverlay(canvas, now);
        drawFooter(canvas);
        canvas.restore();
    }

    private void drawBackground(Canvas c, long now) {
        paint.setShader(new RadialGradient(640, 320, 760,
                new int[]{0xFF24102E, 0xFF080A12, 0xFF020307},
                new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, DESIGN_W, DESIGN_H, paint);
        paint.setShader(null);
        int stars = reducedMotion ? 22 : 54;
        for (int i = 0; i < stars; i++) {
            float cycle = (now * 0.00007f + fract(i * 0.618f));
            float x = fract(i * 0.731f + cycle * .11f) * DESIGN_W;
            float y = fract(i * 0.417f + cycle * .05f) * DESIGN_H;
            float pulse = .25f + .75f * (float) Math.pow(Math.sin(cycle * Math.PI * 2), 2);
            paint.setColor(withAlpha(i % 5 == 0 ? 0xFF67DFFF : 0xFFFFCE66,
                    Math.round(25 + pulse * 100)));
            c.drawCircle(x, y, i % 7 == 0 ? 2f : 1f, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x99D59634);
        rect.set(8, 8, DESIGN_W - 8, DESIGN_H - 8);
        c.drawRoundRect(rect, 25, 25, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawHeader(Canvas c, long now) {
        float breathe = reducedMotion ? 1f : 1f + .018f * (float) Math.sin(now * .0022);
        c.save();
        c.scale(breathe, breathe, 640, 62);
        rect.set(447, 14, 833, 106);
        paint.setAlpha(255);
        c.drawBitmap(logo, null, rect, paint);
        c.restore();
        float sweep = fract(now / 4200f);
        float x = 390 + sweep * 500;
        paint.setShader(new LinearGradient(x - 70, 35, x + 70, 95,
                new int[]{0x00FFFFFF, 0x88FFFFFF, 0x00FFFFFF},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(380, 24, 900, 100), 18, 18, paint);
        paint.setShader(null);
        drawEqualizer(c, 250, 62, now);
        drawEqualizer(c, 1030, 62, now);
    }

    private void drawEqualizer(Canvas c, float centerX, float centerY, long now) {
        for (int i = 0; i < 21; i++) {
            float wave = .25f + .75f * (float) Math.abs(Math.sin(now * .004 + i * .48));
            float h = 4 + wave * (i % 3 == 0 ? 26 : 16);
            float x = centerX + (i - 10) * 5.2f;
            paint.setColor(withAlpha(i % 4 == 0 ? 0xFF5ADFFF : 0xFFFFB73D, 150));
            c.drawRoundRect(new RectF(x - 1.4f, centerY - h / 2, x + 1.4f, centerY + h / 2), 2, 2, paint);
        }
    }

    private void drawLeftPanel(Canvas c, long now) {
        drawPanel(c, 22, 126, 183, 614, 22);
        drawMedallion(c, 102, 178, now);
        label(c, freeSpins > 0 ? "FREE SPINS" : "ESTADO", 102, 232, 12, 0xFFB8C2D6);
        value(c, freeSpins > 0 ? String.valueOf(freeSpins) : "PREMIUM", 102, 265,
                freeSpins > 0 ? 33 : 21, freeSpins > 0 ? 0xFFFFD86A : 0xFFFFFFFF);
        label(c, "SALDO", 102, 328, 11, 0xFF9DA7B8);
        value(c, numberFormat.format(credits) + " CR", 102, 363, 24, Color.WHITE);
        label(c, "RTP TEÓRICO", 102, 425, 11, 0xFF9DA7B8);
        value(c, "95,48%", 102, 458, 22, 0xFFFFCF57);
        label(c, "RONDA", 102, 520, 11, 0xFF9DA7B8);
        value(c, String.valueOf(rounds), 102, 554, 22, Color.WHITE);
        drawPill(c, 45, 572, 159, 606, reducedMotion ? "MOVIMIENTO RED." : "CINEMÁTICO",
                reducedMotion ? 0xFF78D7FF : 0xFFFFD161);
    }

    private void drawMedallion(Canvas c, float cx, float cy, long now) {
        float pulse = reducedMotion ? 1f : 1f + .035f * (float) Math.sin(now * .003);
        c.save();
        c.scale(pulse, pulse, cx, cy);
        paint.setShader(new RadialGradient(cx - 9, cy - 12, 62,
                new int[]{0xFFFFE68D, 0xFFB87912, 0xFF201006}, null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, 46, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(0xFFFFE29B);
        c.drawCircle(cx, cy, 46, paint);
        paint.setStyle(Paint.Style.FILL);
        value(c, freeSpins > 0 ? String.valueOf(freeSpins) : "1", cx, cy + 12, 40, 0xFF201006);
        c.restore();
    }

    private void drawReelCabinet(Canvas c, long now) {
        paint.setShader(new LinearGradient(REEL_X - 16, REEL_Y - 20, REEL_X + REEL_W + 16,
                REEL_Y + REEL_H + 20,
                new int[]{0xFFFFE08A, 0xFF6F3D0B, 0xFFFFCE55, 0xFF3B1E06},
                null, Shader.TileMode.MIRROR));
        rect.set(REEL_X - 17, REEL_Y - 18, REEL_X + REEL_W + 17, REEL_Y + REEL_H + 18);
        c.drawRoundRect(rect, 22, 22, paint);
        paint.setShader(null);
        paint.setColor(0xFF05070C);
        rect.inset(5, 5);
        c.drawRoundRect(rect, 18, 18, paint);
        drawReels(c, now);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.2f);
        paint.setColor(0xFFFFCC59);
        rect.set(REEL_X - 3, REEL_Y - 3, REEL_X + REEL_W + 3, REEL_Y + REEL_H + 3);
        c.drawRoundRect(rect, 14, 14, paint);
        paint.setStyle(Paint.Style.FILL);
        float sweep = fract(now / 3500f);
        float x = REEL_X - 80 + sweep * (REEL_W + 160);
        paint.setShader(new LinearGradient(x - 70, REEL_Y, x + 70, REEL_Y + REEL_H,
                new int[]{0x00FFFFFF, 0x2AFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRect(REEL_X, REEL_Y, REEL_X + REEL_W, REEL_Y + REEL_H, paint);
        paint.setShader(null);
    }

    private void drawReels(Canvas c, long now) {
        c.save();
        c.clipRect(REEL_X, REEL_Y, REEL_X + REEL_W, REEL_Y + REEL_H);
        int[][] board = pending != null ? pending.board : current.board;
        for (int reel = 0; reel < 5; reel++) {
            float reelLeft = REEL_X + reel * CELL_W;
            for (int row = 0; row < 3; row++) drawCellBackground(c, reelLeft, REEL_Y + row * CELL_H, reel, row);
            if (phase == SPINNING && now - phaseStart < stopTimes[reel]) drawSpinningReel(c, reel, reelLeft, now);
            else drawStoppedReel(c, board, reel, reelLeft, now);
            paint.setColor(0x886A3B10);
            c.drawRect(reelLeft + CELL_W - 1, REEL_Y, reelLeft + CELL_W + 1, REEL_Y + REEL_H, paint);
        }
        c.restore();
    }

    private void drawCellBackground(Canvas c, float left, float top, int reel, int row) {
        int base = ((reel + row) & 1) == 0 ? 0xFF090B13 : 0xFF0D101A;
        paint.setShader(new LinearGradient(left, top, left + CELL_W, top + CELL_H,
                new int[]{base, 0xFF03050A, base}, null, Shader.TileMode.CLAMP));
        rect.set(left + 2, top + 2, left + CELL_W - 2, top + CELL_H - 2);
        c.drawRoundRect(rect, 8, 8, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(0x44764A1A);
        rect.inset(5, 5);
        c.drawRoundRect(rect, 8, 8, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSpinningReel(Canvas c, int reel, float left, long now) {
        long elapsed = now - phaseStart;
        float speed = 1f + reel * .08f;
        float scrollOffset = (elapsed * .62f * speed) % CELL_H;
        int cycle = (int) (elapsed / 105 + reel * 3);
        for (int slot = -1; slot <= 3; slot++) {
            int symbol = Math.floorMod(cycle + slot + reel, 9);
            float cy = REEL_Y + slot * CELL_H + CELL_H / 2 + scrollOffset;
            drawSymbol(c, symbol, left + CELL_W / 2, cy, reel, slot, now, .82f, 0f, false, true);
            if (!reducedMotion) {
                paint.setAlpha(52);
                drawSymbol(c, symbol, left + CELL_W / 2, cy - 28, reel, slot, now, .78f, 0f, false, false);
                paint.setAlpha(255);
            }
        }
    }

    private void drawStoppedReel(Canvas c, int[][] board, int reel, float left, long now) {
        long elapsed = now - phaseStart;
        float bounce = 0f;
        if (phase == SPINNING || phase == REVEALING || phase == FEATURE_INTRO) {
            long local = elapsed - stopTimes[reel];
            if (local >= 0 && local < 520 && !reducedMotion) bounce = damped(local, 14f);
        }
        for (int row = 0; row < 3; row++) {
            int symbol = board[reel][row];
            float cx = left + CELL_W / 2;
            float cy = REEL_Y + row * CELL_H + CELL_H / 2 + bounce;
            boolean winning = isWinningCell(reel, row, now);
            float idleScale = 1f;
            float rotation = 0f;
            if (phase == IDLE || phase == REVEALING) {
                float wave = (float) Math.sin(now * .0016 + reel * .71 + row * 1.19);
                idleScale += reducedMotion ? 0f : wave * .018f;
                rotation = reducedMotion ? 0f : symbolRotation(symbol, wave, winning);
            }
            float winScale = winning ? 1.08f + .06f * (float) Math.sin(now * .012) : 1f;
            drawSymbol(c, symbol, cx, cy, reel, row, now, idleScale * winScale, rotation, winning, false);
        }
    }

    private void drawSymbol(Canvas c, int symbol, float cx, float cy, int reel, int row,
                            long now, float symbolScale, float rotation, boolean winning, boolean streak) {
        Bitmap bitmap = symbols.get(symbol);
        if (bitmap == null) return;
        float size = symbol == LandscapeSlotEngine.BAR ? 132f : 118f;
        if (symbol == LandscapeSlotEngine.WILD) size = 128f;
        if (streak) size *= .92f;
        c.save();
        c.translate(cx, cy);
        c.rotate(rotation);
        c.scale(symbolScale, symbolScale);
        if (winning) {
            int accent = accent(symbol);
            float pulse = .55f + .45f * (float) Math.sin(now * .010 + reel);
            glowPaint.setColor(withAlpha(accent, Math.round(90 + pulse * 110)));
            glowPaint.setMaskFilter(new BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL));
            c.drawCircle(0, 0, size * .48f, glowPaint);
            glowPaint.setMaskFilter(null);
            drawSymbolEffect(c, symbol, now);
        }
        rect.set(-size / 2, -size / 2, size / 2, size / 2);
        paint.setAlpha(streak ? 150 : 255);
        c.drawBitmap(bitmap, null, rect, paint);
        paint.setAlpha(255);
        if (!streak) {
            float sweep = fract(now / (2600f + reel * 130f) + row * .17f);
            float x = -size * .75f + sweep * size * 1.5f;
            c.save();
            c.clipRect(rect);
            paint.setShader(new LinearGradient(x - 16, -size / 2, x + 16, size / 2,
                    new int[]{0x00FFFFFF, 0x78FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.drawRect(rect, paint);
            paint.setShader(null);
            c.restore();
        }
        c.restore();
    }

    private void drawSymbolEffect(Canvas c, int symbol, long now) {
        int accent = accent(symbol);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(symbol == LandscapeSlotEngine.BAR ? 5f : 3f);
        paint.setColor(withAlpha(accent, 185));
        if (symbol == LandscapeSlotEngine.BELL) {
            float ring = 52 + 16 * fract(now / 850f);
            c.drawCircle(0, 0, ring, paint);
            c.drawCircle(0, 0, ring + 14, paint);
        } else if (symbol == LandscapeSlotEngine.BAR) {
            float kick = 12 * (float) Math.exp(-3 * fract(now / 620f));
            c.drawLine(-68 - kick, -35, -68 + kick, 35, paint);
            c.drawLine(68 + kick, -35, 68 - kick, 35, paint);
        } else if (symbol == LandscapeSlotEngine.SEVEN) {
            c.drawLine(-62, 58, 62, -58, paint);
            paint.setStrokeWidth(1.4f);
            paint.setColor(0xFFFFE58A);
            c.drawLine(-52, 62, 68, -48, paint);
        } else if (symbol == LandscapeSlotEngine.DIAMOND) {
            for (int i = 0; i < 8; i++) {
                double angle = now * .001 + i * Math.PI / 4;
                c.drawLine((float) Math.cos(angle) * 42, (float) Math.sin(angle) * 42,
                        (float) Math.cos(angle) * 69, (float) Math.sin(angle) * 69, paint);
            }
        } else if (symbol == LandscapeSlotEngine.WILD) {
            for (int i = 0; i < 7; i++) {
                double angle = now * .0015 + i * Math.PI * 2 / 7;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(i % 2 == 0 ? 0xFFFFE67B : 0xFF65DFFF);
                drawDiamondShape(c, (float) Math.cos(angle) * 67, (float) Math.sin(angle) * 48, 4f);
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private boolean isWinningCell(int reel, int row, long now) {
        if (current == null || current.wins.isEmpty()) return false;
        if (phase != REVEALING && phase != FEATURE_INTRO) return false;
        int index = (int) (((now - phaseStart) / 950L) % current.wins.size());
        LandscapeSlotEngine.LineWin win = current.wins.get(index);
        return reel < win.count && win.rows[reel] == row;
    }

    private float symbolRotation(int symbol, float wave, boolean winning) {
        if (winning && symbol == LandscapeSlotEngine.BELL) return wave * 8f;
        if (winning && symbol == LandscapeSlotEngine.SEVEN) return wave * 2.5f;
        if (symbol == LandscapeSlotEngine.DIAMOND) return wave * 1.7f;
        if (symbol == LandscapeSlotEngine.WILD) return wave * 1.2f;
        return wave * .5f;
    }

    private void drawRightPanel(Canvas c, long now) {
        drawPanel(c, 1015, 126, 1258, 614, 22);
        label(c, "APUESTA POR LÍNEA", 1136, 170, 11, 0xFFAEB6C5);
        drawCircleButton(c, 1055, 216, 27, "−");
        drawCircleButton(c, 1218, 216, 27, "+");
        value(c, betPerLine + " CR", 1136, 225, 25, 0xFFFFD36A);
        label(c, "APUESTA TOTAL", 1065, 286, 10, 0xFF9BA4B4);
        value(c, numberFormat.format(betPerLine * LandscapeSlotEngine.LINES) + " CR", 1065, 318, 19, Color.WHITE);
        label(c, "ÚLTIMO PREMIO", 1200, 286, 10, 0xFF9BA4B4);
        value(c, numberFormat.format(shownWin) + " CR", 1200, 318, 19, 0xFFFFC952);
        drawSpinButton(c, now);
        drawPill(c, 1045, 558, 1228, 595, audio.isEnabled() ? "SFX ON" : "SFX OFF",
                audio.isEnabled() ? 0xFFFFD161 : 0xFF9BA4B4);
    }

    private void drawSpinButton(Canvas c, long now) {
        float cx = 1136;
        float cy = 440;
        boolean ready = phase == IDLE;
        float pulse = ready && !reducedMotion ? 1f + .025f * (float) Math.sin(now * .0042) : 1f;
        float press = pressedSpin ? .94f : 1f;
        c.save();
        c.scale(pulse * press, pulse * press, cx, cy);
        paint.setShader(new RadialGradient(cx - 18, cy - 24, 104,
                ready ? new int[]{0xFFFFF0A2, 0xFFFFB52A, 0xFF9E5206}
                        : new int[]{0xFFC7B0F4, 0xFF7650AE, 0xFF35204E},
                null, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, 92, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(7);
        paint.setColor(ready ? 0xFFFFE598 : 0xFFD3C2F2);
        c.drawCircle(cx, cy, 92, paint);
        paint.setStrokeWidth(2);
        paint.setColor(0xFF5D2A02);
        c.drawCircle(cx, cy, 76, paint);
        paint.setStyle(Paint.Style.FILL);
        value(c, freeSpins > 0 ? "GIRO GRATIS" : "GIRAR", cx, cy + 11, freeSpins > 0 ? 20 : 30, 0xFF2D1604);
        c.restore();
    }

    private void drawWinOverlay(Canvas c, long now) {
        if (phase == FEATURE_INTRO) {
            float elapsed = now - phaseStart;
            float t = clamp(elapsed / 900f, 0f, 1f);
            float overshoot = 1f + .16f * (float) Math.sin(Math.PI * t) * (1 - t);
            c.save();
            c.scale(overshoot, overshoot, 640, 610);
            paint.setShader(new LinearGradient(360, 560, 920, 660,
                    new int[]{0xFF6B0814, 0xFFE3272F, 0xFF7A0710}, null, Shader.TileMode.CLAMP));
            rect.set(360, 558, 920, 654);
            c.drawRoundRect(rect, 26, 26, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            paint.setColor(0xFFFFD456);
            c.drawRoundRect(rect, 26, 26, paint);
            paint.setStyle(Paint.Style.FILL);
            value(c, "30 FREE SPINS", 640, 615, 48, 0xFFFFE47B);
            label(c, "RETRIGGER +10 · CRÉDITOS FICTICIOS", 640, 642, 12, 0xFFFFFFFF);
            c.restore();
            paint.setColor(withAlpha(0xFFFF233D, 75));
            c.drawRect(0, 0, DESIGN_W, DESIGN_H, paint);
        } else if (phase == REVEALING && targetWin > 0) {
            float ratio = targetWin / (float) Math.max(1, betPerLine * LandscapeSlotEngine.LINES);
            String tier = ratio >= 20 ? "ROYAL WIN" : ratio >= 5 ? "BIG WIN" : "PREMIO";
            paint.setShader(new LinearGradient(408, 575, 872, 650,
                    new int[]{0xFF4C2104, 0xFFE0A128, 0xFF4C2104}, null, Shader.TileMode.MIRROR));
            rect.set(408, 572, 872, 650);
            c.drawRoundRect(rect, 24, 24, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setColor(0xFFFFE495);
            c.drawRoundRect(rect, 24, 24, paint);
            paint.setStyle(Paint.Style.FILL);
            label(c, tier, 640, 597, 12, 0xFF4B2806);
            value(c, numberFormat.format(shownWin) + " CR", 640, 635, 34, 0xFF2A1402);
        }
    }

    private void drawParticles(Canvas c) {
        for (Particle p : particles) {
            float alpha = clamp(p.life / p.maxLife, 0f, 1f);
            c.save();
            c.translate(p.x, p.y);
            c.rotate(p.rotation);
            paint.setColor(withAlpha(p.color, Math.round(alpha * 255)));
            if (p.shape == 0) c.drawCircle(0, 0, p.size, paint);
            else drawDiamondShape(c, 0, 0, p.size);
            c.restore();
        }
    }

    private void drawFooter(Canvas c) {
        label(c, "20 LÍNEAS · RESULTADO PRECALCULADO · SIN DINERO REAL", 640, 694, 10, 0xFFB8C1D0);
    }

    private void spawnStopBurst(int reel) {
        float x = REEL_X + reel * CELL_W + CELL_W / 2;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8 + random.nextDouble() * .25;
            particles.add(new Particle(x, REEL_Y + REEL_H / 2,
                    (float) Math.cos(angle) * (50 + random.nextFloat() * 70),
                    (float) Math.sin(angle) * (40 + random.nextFloat() * 60),
                    0, 1.1f, 1.1f, i % 2 == 0 ? 0xFFFFD35C : 0xFF6DE8FF,
                    2.5f, i % 2));
        }
    }

    private void spawnWinBurst() {
        if (current == null || current.wins.isEmpty()) return;
        int color = accent(current.wins.get(0).symbol);
        for (int i = 0; i < 70; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            float speed = 80 + random.nextFloat() * 250;
            particles.add(new Particle(REEL_X + REEL_W / 2, REEL_Y + REEL_H / 2,
                    (float) Math.cos(angle) * speed, (float) Math.sin(angle) * speed - 50,
                    130, 1.2f + random.nextFloat() * 1.2f, 2.4f,
                    i % 4 == 0 ? 0xFFFFFFFF : color, 2f + random.nextFloat() * 5f, i % 2));
        }
    }

    private void spawnFeatureBurst() {
        for (int i = 0; i < 140; i++) {
            float x = REEL_X + random.nextFloat() * REEL_W;
            float y = REEL_Y + random.nextFloat() * REEL_H;
            double angle = random.nextDouble() * Math.PI * 2;
            float speed = 90 + random.nextFloat() * 310;
            int color = i % 3 == 0 ? 0xFFFF334C : i % 3 == 1 ? 0xFFFFD756 : 0xFF6EE6FF;
            particles.add(new Particle(x, y, (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed - 90, 150,
                    1.6f + random.nextFloat() * 1.6f, 2.5f, color,
                    3f + random.nextFloat() * 7f, 1));
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = (event.getX() - offsetX) / scale;
        float y = (event.getY() - offsetY) / scale;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pressedSpin = distance(x, y, 1136, 440) < 105;
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            boolean wasPressed = pressedSpin;
            pressedSpin = false;
            if (wasPressed && distance(x, y, 1136, 440) < 112) {
                startSpin(SystemClock.uptimeMillis());
            } else if (x >= 1045 && x <= 1228 && y >= 558 && y <= 595) {
                audio.setEnabled(!audio.isEnabled());
            } else if (x >= 45 && x <= 159 && y >= 572 && y <= 606) {
                reducedMotion = !reducedMotion;
            } else if (phase == IDLE && y >= 185 && y <= 245) {
                if (x < 1090) betPerLine = Math.max(1, betPerLine - 1);
                else if (x > 1180) betPerLine = Math.min(10, betPerLine + 1);
            }
            invalidate();
            performClick();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            pressedSpin = false;
            invalidate();
            return true;
        }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private void drawPanel(Canvas c, float left, float top, float right, float bottom, float radius) {
        paint.setShader(new LinearGradient(left, top, right, bottom,
                new int[]{0xF20A0D15, 0xF2180F20, 0xF204060B}, null, Shader.TileMode.CLAMP));
        rect.set(left, top, right, bottom);
        c.drawRoundRect(rect, radius, radius, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(0xFFD09634);
        c.drawRoundRect(rect, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawPill(Canvas c, float left, float top, float right, float bottom, String text, int color) {
        paint.setColor(0xE8080A10);
        rect.set(left, top, right, bottom);
        c.drawRoundRect(rect, 18, 18, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(color);
        c.drawRoundRect(rect, 18, 18, paint);
        paint.setStyle(Paint.Style.FILL);
        value(c, text, (left + right) / 2, (top + bottom) / 2 + 5, 12, color);
    }

    private void drawCircleButton(Canvas c, float cx, float cy, float radius, String text) {
        paint.setColor(0xFF11151E);
        c.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(0xFF986B2B);
        c.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        value(c, text, cx, cy + 8, 25, Color.WHITE);
    }

    private void label(Canvas c, String text, float x, float y, float size, int color) {
        paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(size);
        paint.setColor(color);
        paint.clearShadowLayer();
        c.drawText(text, x, y, paint);
    }

    private void value(Canvas c, String text, float x, float y, float size, int color) {
        paint.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setShadowLayer(4f, 0, 2, 0x99000000);
        c.drawText(text, x, y, paint);
        paint.clearShadowLayer();
    }

    private void drawDiamondShape(Canvas c, float cx, float cy, float size) {
        path.reset();
        path.moveTo(cx, cy - size);
        path.lineTo(cx + size, cy);
        path.lineTo(cx, cy + size);
        path.lineTo(cx - size, cy);
        path.close();
        c.drawPath(path, paint);
    }

    private static int accent(int symbol) {
        switch (symbol) {
            case LandscapeSlotEngine.SEVEN: return 0xFFFF304A;
            case LandscapeSlotEngine.BAR: return 0xFFFFD475;
            case LandscapeSlotEngine.BELL: return 0xFFFFB12F;
            case LandscapeSlotEngine.DIAMOND: return 0xFF54DFFF;
            case LandscapeSlotEngine.WILD: return 0xFFFFDF61;
            case LandscapeSlotEngine.K: return 0xFF59E86A;
            case LandscapeSlotEngine.Q: return 0xFFEF5DFF;
            case LandscapeSlotEngine.J: return 0xFF4B8DFF;
            default: return 0xFFFFB05A;
        }
    }

    private static float damped(long millis, float amplitude) {
        float t = millis / 1000f;
        return (float) (-Math.exp(-7.2f * t) * Math.cos(25f * t) * amplitude);
    }

    private static float easeOutCubic(float t) {
        float x = 1f - clamp(t, 0f, 1f);
        return 1f - x * x * x;
    }

    private static float fract(float value) { return value - (float) Math.floor(value); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static int withAlpha(int color, int alpha) { return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF); }
    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static final class Particle {
        float x, y, vx, vy, gravity, life, maxLife, size, rotation, spin;
        final int color;
        final int shape;

        Particle(float x, float y, float vx, float vy, float gravity,
                 float life, float maxLife, int color, float size, int shape) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.gravity = gravity;
            this.life = life;
            this.maxLife = maxLife;
            this.color = color;
            this.size = size;
            this.shape = shape;
            this.rotation = (float) (Math.random() * 360);
            this.spin = (float) (Math.random() * 300 - 150);
        }
    }
}
