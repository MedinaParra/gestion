package cl.exequiel.royalspin.landscape;

import android.content.Context;
import android.graphics.Bitmap;
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
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Stable single-view landscape renderer with cinematic reel motion and synchronized audio. */
public final class CinematicSlotView extends View implements Choreographer.FrameCallback {
    private static final float DESIGN_W = 1280f;
    private static final float DESIGN_H = 720f;
    private static final float REEL_X = 205f;
    private static final float REEL_Y = 145f;
    private static final float REEL_W = 790f;
    private static final float REEL_H = 450f;
    private static final float CELL_W = REEL_W / LandscapeSlotEngine.REELS;
    private static final float CELL_H = REEL_H / LandscapeSlotEngine.ROWS;
    private static final float SPIN_X = 1136f;
    private static final float SPIN_Y = 440f;

    private static final int IDLE = 0;
    private static final int SPINNING = 1;
    private static final int REVEALING = 2;
    private static final int FEATURE = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Random random = new Random(System.nanoTime() ^ 0x524f59414cL);
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(new Locale("es", "CL"));
    private final Map<Integer, Bitmap> symbols = IconAtlas.loadSymbols();
    private final Bitmap logo = IconAtlas.loadLogo();
    private final CinematicAudioEngine audio = new CinematicAudioEngine();
    private final List<Particle> particles = new ArrayList<>();
    private final int[][] spinStrip = new int[LandscapeSlotEngine.REELS][48];
    private final boolean[] stopCuePlayed = new boolean[LandscapeSlotEngine.REELS];

    private LandscapeSlotEngine.SpinResult current;
    private LandscapeSlotEngine.SpinResult pending;
    private int phase = IDLE;
    private int credits = 5000;
    private int betPerLine = 5;
    private int freeSpins;
    private int shownWin;
    private int targetWin;
    private int rounds;
    private boolean attached;
    private boolean pressedSpin;
    private boolean anticipation;
    private boolean anticipationCuePlayed;
    private boolean released;
    private long spinStarted;
    private long revealStarted;
    private long featureStarted;
    private long pressStarted;
    private long lastFrame;
    private long[] stopTimes = AnimationProfile.stopTimes(false);
    private float scale = 1f;
    private float offsetX;
    private float offsetY;
    private final String demo;

    public CinematicSlotView(Context context, String demoScene) {
        super(context);
        demo = demoScene == null ? "" : demoScene;
        setFocusable(true);
        setClickable(true);
        setLayerType(View.LAYER_TYPE_NONE, null);
        audio.setMasterVolume(0.78f);
        current = LandscapeSlotEngine.demo("idle", betPerLine);
        seedSpinStrips();

        if (!demo.isEmpty()) {
            current = LandscapeSlotEngine.demo(demo, betPerLine);
            targetWin = current.payout;
            shownWin = targetWin;
            if ("feature".equals(demo)) {
                freeSpins = 30;
                phase = FEATURE;
                featureStarted = SystemClock.uptimeMillis() - 800L;
            } else {
                phase = REVEALING;
                revealStarted = SystemClock.uptimeMillis() - 900L;
            }
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        released = false;
        lastFrame = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    public void onHostResume() {
        if (!attached || released) return;
        Choreographer.getInstance().removeFrameCallback(this);
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void onHostPause() {
        pressedSpin = false;
    }

    public void release() {
        if (released) return;
        released = true;
        attached = false;
        Choreographer.getInstance().removeFrameCallback(this);
        audio.release();
        for (Bitmap bitmap : symbols.values()) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        if (logo != null && !logo.isRecycled()) logo.recycle();
        particles.clear();
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!attached || released) return;
        long now = frameTimeNanos / 1_000_000L;
        if (lastFrame == 0L) lastFrame = now;
        float dt = Math.min(0.045f, Math.max(0f, (now - lastFrame) / 1000f));
        lastFrame = now;
        update(now, dt);
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void update(long now, float dt) {
        if (phase == SPINNING) updateSpin(now);
        else if (phase == REVEALING) updateReveal(now);
        else if (phase == FEATURE) updateFeature(now);

        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            particle.life -= dt;
            if (particle.life <= 0f) {
                iterator.remove();
                continue;
            }
            particle.x += particle.vx * dt;
            particle.y += particle.vy * dt;
            particle.vy += particle.gravity * dt;
            particle.rotation += particle.spin * dt;
        }
    }

    private void updateSpin(long now) {
        long elapsed = now - spinStarted;
        if (anticipation && !anticipationCuePlayed && elapsed >= stopTimes[2] + 120L) {
            anticipationCuePlayed = true;
            audio.play(CinematicAudioEngine.Cue.ANTICIPATION);
            spawnAnticipationDust();
        }

        for (int reel = 0; reel < LandscapeSlotEngine.REELS; reel++) {
            if (!stopCuePlayed[reel] && elapsed >= stopTimes[reel]) {
                stopCuePlayed[reel] = true;
                audio.playStop(reel);
                spawnStopBurst(reel);
                if (reel == 4) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        }

        if (elapsed >= stopTimes[4] + 540L) finishSpin(now);
    }

    private void updateReveal(long now) {
        long elapsed = now - revealStarted;
        shownWin = Math.round(targetWin * AnimationProfile.revealProgress(elapsed, 1250L));
        if (demo.isEmpty() && elapsed > 4800L) {
            shownWin = targetWin;
            phase = IDLE;
        }
    }

    private void updateFeature(long now) {
        long elapsed = now - featureStarted;
        shownWin = Math.round(targetWin * AnimationProfile.revealProgress(elapsed, 1450L));
        if (demo.isEmpty() && elapsed > 4200L) {
            phase = REVEALING;
            revealStarted = now;
        }
    }

    private void startSpin(long now) {
        if (phase != IDLE) return;
        int totalBet = betPerLine * LandscapeSlotEngine.LINES;
        boolean free = freeSpins > 0;
        if (!free && credits < totalBet) return;
        if (free) freeSpins--;
        else credits -= totalBet;

        pending = LandscapeSlotEngine.spin(random, betPerLine);
        anticipation = pending.freeSpinsTriggered || pending.payout >= totalBet * 8;
        stopTimes = AnimationProfile.stopTimes(anticipation);
        for (int i = 0; i < stopCuePlayed.length; i++) stopCuePlayed[i] = false;
        anticipationCuePlayed = false;
        seedSpinStrips();
        particles.clear();
        targetWin = 0;
        shownWin = 0;
        rounds++;
        phase = SPINNING;
        spinStarted = now;
        audio.play(CinematicAudioEngine.Cue.BUTTON);
        audio.play(CinematicAudioEngine.Cue.SPIN_START);
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void finishSpin(long now) {
        current = pending;
        pending = null;
        targetWin = current.payout;
        credits += current.payout;

        if (current.freeSpinsTriggered) {
            freeSpins = Math.min(90, freeSpins + 30);
            phase = FEATURE;
            featureStarted = now;
            audio.play(CinematicAudioEngine.Cue.FEATURE);
            spawnCelebration(110);
        } else {
            phase = REVEALING;
            revealStarted = now;
            playWinAudio();
            if (targetWin > 0) spawnCelebration(targetWin >= betPerLine * LandscapeSlotEngine.LINES * 8 ? 80 : 38);
        }
    }

    private void playWinAudio() {
        if (current == null || current.wins.isEmpty()) return;
        int totalBet = Math.max(1, betPerLine * LandscapeSlotEngine.LINES);
        float ratio = current.payout / (float) totalBet;
        if (ratio >= 20f) audio.play(CinematicAudioEngine.Cue.MEGA_WIN);
        else if (ratio >= 5f) audio.play(CinematicAudioEngine.Cue.BIG_WIN);
        else audio.play(CinematicAudioEngine.Cue.SMALL_WIN);

        int symbol = current.wins.get(0).symbol;
        switch (symbol) {
            case LandscapeSlotEngine.BELL: audio.play(CinematicAudioEngine.Cue.BELL); break;
            case LandscapeSlotEngine.BAR: audio.play(CinematicAudioEngine.Cue.BAR); break;
            case LandscapeSlotEngine.SEVEN: audio.play(CinematicAudioEngine.Cue.SEVEN); break;
            case LandscapeSlotEngine.DIAMOND: audio.play(CinematicAudioEngine.Cue.DIAMOND); break;
            case LandscapeSlotEngine.WILD: audio.play(CinematicAudioEngine.Cue.WILD); break;
            default: break;
        }
    }

    private void seedSpinStrips() {
        for (int reel = 0; reel < spinStrip.length; reel++) {
            for (int index = 0; index < spinStrip[reel].length; index++) {
                spinStrip[reel][index] = random.nextInt(LandscapeSlotEngine.WILD + 1);
            }
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        scale = Math.min(getWidth() / DESIGN_W, getHeight() / DESIGN_H);
        offsetX = (getWidth() - DESIGN_W * scale) * 0.5f;
        offsetY = (getHeight() - DESIGN_H * scale) * 0.5f;

        canvas.drawColor(Color.BLACK);
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        float shakeX = 0f;
        float shakeY = 0f;
        if (phase == SPINNING) {
            long elapsed = now - spinStarted;
            for (int reel = 0; reel < stopTimes.length; reel++) {
                long local = elapsed - stopTimes[reel];
                if (local >= 0L && local < 170L) {
                    float fade = 1f - local / 170f;
                    float strength = (reel == 4 ? 4.8f : 2.1f) * fade;
                    shakeX += (float) Math.sin(local * 0.19 + reel) * strength;
                    shakeY += (float) Math.cos(local * 0.23 + reel) * strength * 0.55f;
                }
            }
        }
        canvas.translate(shakeX, shakeY);

        drawBackground(canvas, now);
        drawHeader(canvas, now);
        drawLeftPanel(canvas, now);
        drawCabinet(canvas, now);
        drawRightPanel(canvas, now);
        drawParticles(canvas);
        drawWinPresentation(canvas, now);
        drawFooter(canvas);
        canvas.restore();
    }

    private void drawBackground(Canvas canvas, long now) {
        paint.setShader(new RadialGradient(640, 330, 760,
                new int[]{0xFF251234, 0xFF090B14, 0xFF020307},
                new float[]{0f, 0.58f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, DESIGN_W, DESIGN_H, paint);
        paint.setShader(null);

        for (int i = 0; i < 46; i++) {
            float drift = now * (0.000018f + (i % 5) * 0.000004f);
            float x = fract(i * 0.731f + drift) * DESIGN_W;
            float y = fract(i * 0.417f + drift * 0.37f) * DESIGN_H;
            float pulse = 0.35f + 0.65f * (float) Math.pow(Math.sin(now * 0.0012 + i), 2);
            paint.setColor(withAlpha(i % 6 == 0 ? 0xFF61E6FF : 0xFFFFD56B, Math.round(25 + 90 * pulse)));
            canvas.drawCircle(x, y, i % 9 == 0 ? 2.2f : 1.15f, paint);
        }

        float beam = fract(now / 8200f);
        float bx = -260f + beam * 1800f;
        paint.setShader(new LinearGradient(bx - 150, 0, bx + 150, DESIGN_H,
                new int[]{0x00000000, 0x155FE7FF, 0x08FFD46A, 0x00000000},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, DESIGN_W, DESIGN_H, paint);
        paint.setShader(null);
    }

    private void drawHeader(Canvas canvas, long now) {
        float breathe = 1f + 0.012f * (float) Math.sin(now * 0.0021f);
        canvas.save();
        canvas.scale(breathe, breathe, 640, 60);
        rect.set(447, 12, 833, 105);
        canvas.drawBitmap(logo, null, rect, paint);
        canvas.restore();

        float shine = fract(now / 4400f);
        float x = 458 + shine * 364;
        paint.setShader(new LinearGradient(x - 18, 18, x + 18, 106,
                new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(x - 18, 16, x + 18, 108, paint);
        paint.setShader(null);
        drawEqualizer(canvas, 255, 62, now);
        drawEqualizer(canvas, 1025, 62, now);
    }

    private void drawEqualizer(Canvas canvas, float centerX, float centerY, long now) {
        for (int i = 0; i < 17; i++) {
            float wave = 0.18f + 0.82f * (float) Math.abs(Math.sin(now * 0.0042 + i * 0.52));
            float height = 4 + wave * (i % 4 == 0 ? 28 : 16);
            float x = centerX + (i - 8) * 6f;
            paint.setColor(withAlpha(i % 3 == 0 ? 0xFF5AE8FF : 0xFFFFC654, 145));
            canvas.drawRoundRect(x - 1.5f, centerY - height / 2f, x + 1.5f, centerY + height / 2f, 2, 2, paint);
        }
    }

    private void drawLeftPanel(Canvas canvas, long now) {
        drawPanel(canvas, 22, 126, 183, 614, 22);
        drawMedallion(canvas, 102, 182, now);
        label(canvas, "CRÉDITOS", 102, 274, 12, 0xFFADB5C6);
        value(canvas, numberFormat.format(credits), 102, 312, 26, 0xFFFFD56A);
        label(canvas, "RONDAS", 102, 366, 11, 0xFFADB5C6);
        value(canvas, numberFormat.format(rounds), 102, 397, 20, Color.WHITE);
        label(canvas, "GIROS GRATIS", 102, 452, 11, 0xFFADB5C6);
        value(canvas, numberFormat.format(freeSpins), 102, 487, 24, freeSpins > 0 ? 0xFF64E8FF : 0xFFFFFFFF);
        drawPill(canvas, 42, 540, 162, 581, anticipation && phase == SPINNING ? "ANTICIPACIÓN" : "20 LÍNEAS",
                anticipation && phase == SPINNING ? 0xFFFFCF57 : 0xFF9DA7B8);
    }

    private void drawMedallion(Canvas canvas, float cx, float cy, long now) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(now * 0.003f);
        paint.setShader(new RadialGradient(cx, cy, 58,
                new int[]{0xFFFFE38A, 0xFFB75B08, 0xFF32140A}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, 54 + pulse * 2, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(0xFFFFE79A);
        canvas.drawCircle(cx, cy, 49, paint);
        paint.setStyle(Paint.Style.FILL);
        value(canvas, "RS", cx, cy + 12, 32, 0xFF351604);
    }

    private void drawCabinet(Canvas canvas, long now) {
        paint.setShader(new LinearGradient(REEL_X - 14, REEL_Y - 24, REEL_X + REEL_W + 18, REEL_Y + REEL_H + 28,
                new int[]{0xFF392009, 0xFFFFD66E, 0xFF5C2C07, 0xFFFFE69B, 0xFF2E1607},
                null, Shader.TileMode.MIRROR));
        rect.set(REEL_X - 17, REEL_Y - 22, REEL_X + REEL_W + 17, REEL_Y + REEL_H + 22);
        canvas.drawRoundRect(rect, 28, 28, paint);
        paint.setShader(null);

        paint.setColor(0xFF080A11);
        rect.set(REEL_X - 7, REEL_Y - 11, REEL_X + REEL_W + 7, REEL_Y + REEL_H + 11);
        canvas.drawRoundRect(rect, 20, 20, paint);

        canvas.save();
        canvas.clipRect(REEL_X, REEL_Y, REEL_X + REEL_W, REEL_Y + REEL_H);
        for (int reel = 0; reel < LandscapeSlotEngine.REELS; reel++) drawReel(canvas, reel, now);
        drawReelGlass(canvas, now);
        canvas.restore();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.2f);
        for (int reel = 1; reel < LandscapeSlotEngine.REELS; reel++) {
            float x = REEL_X + reel * CELL_W;
            paint.setColor(0x66FFD66A);
            canvas.drawLine(x, REEL_Y, x, REEL_Y + REEL_H, paint);
        }
        paint.setColor(0x334EA0D8);
        for (int row = 1; row < LandscapeSlotEngine.ROWS; row++) {
            float y = REEL_Y + row * CELL_H;
            canvas.drawLine(REEL_X, y, REEL_X + REEL_W, y, paint);
        }
        paint.setStyle(Paint.Style.FILL);

        if ((phase == REVEALING || phase == FEATURE) && current != null && !current.wins.isEmpty()) {
            drawAnimatedPayline(canvas, now);
        }

        if (phase == SPINNING && anticipation) drawAnticipationFrame(canvas, now);
    }

    private void drawReel(Canvas canvas, int reel, long now) {
        float left = REEL_X + reel * CELL_W;
        if (phase == SPINNING) {
            long elapsed = now - spinStarted;
            if (elapsed < stopTimes[reel]) {
                drawScrollingReel(canvas, reel, left, elapsed);
                return;
            }
            drawStoppedReel(canvas, reel, left, now, elapsed - stopTimes[reel]);
        } else {
            drawStoppedReel(canvas, reel, left, now, Long.MAX_VALUE);
        }
    }

    private void drawScrollingReel(Canvas canvas, int reel, float left, long elapsed) {
        float distance = AnimationProfile.reelDistanceCells(elapsed, stopTimes[reel]);
        int base = (int) Math.floor(distance);
        float fraction = distance - base;
        float velocity = AnimationProfile.reelVelocity(elapsed, stopTimes[reel]);
        float stretch = 1f + Math.min(0.20f, velocity / 95f);
        int alpha = Math.round(255f - Math.min(90f, velocity * 4.0f));

        for (int virtualRow = -2; virtualRow <= 4; virtualRow++) {
            int index = floorMod(base + virtualRow + reel * 7, spinStrip[reel].length);
            int symbol = spinStrip[reel][index];
            float cy = REEL_Y + (virtualRow + 0.5f - fraction) * CELL_H;
            float cx = left + CELL_W / 2f;
            if (cy < REEL_Y - CELL_H || cy > REEL_Y + REEL_H + CELL_H) continue;

            if (velocity > 8f) {
                drawSymbol(canvas, symbol, cx, cy - 24, 0.88f, 0f, false, 32);
                drawSymbol(canvas, symbol, cx, cy - 48, 0.82f, 0f, false, 16);
            }
            canvas.save();
            canvas.scale(1f, stretch, cx, cy);
            drawSymbol(canvas, symbol, cx, cy, 0.96f, 0f, false, alpha);
            canvas.restore();
        }
    }

    private void drawStoppedReel(Canvas canvas, int reel, float left, long now, long localStop) {
        float bounce = localStop == Long.MAX_VALUE ? 0f : AnimationProfile.stopBounce(localStop);
        for (int row = 0; row < LandscapeSlotEngine.ROWS; row++) {
            int symbol = current.board[reel][row];
            float cx = left + CELL_W / 2f;
            float cy = REEL_Y + row * CELL_H + CELL_H / 2f + bounce;
            boolean winning = isWinningCell(reel, row, now);
            float wave = (float) Math.sin(now * 0.0018 + reel * 0.72 + row * 1.17);
            float symbolScale = 1f + wave * 0.012f;
            float rotation = winning ? wave * (symbol == LandscapeSlotEngine.BELL ? 7f : 2.2f) : wave * 0.35f;
            if (localStop != Long.MAX_VALUE && localStop < 190L) {
                float settle = 1f - localStop / 190f;
                symbolScale *= 1f + 0.07f * settle;
            }
            drawSymbol(canvas, symbol, cx, cy, symbolScale * (winning ? 1.08f : 1f), rotation, winning, 255);
        }
    }

    private void drawSymbol(Canvas canvas, int symbol, float cx, float cy, float symbolScale,
                            float rotation, boolean winning, int alpha) {
        Bitmap bitmap = symbols.get(symbol);
        if (bitmap == null || bitmap.isRecycled()) return;
        float size = symbol == LandscapeSlotEngine.BAR ? 132f : 116f;
        if (symbol == LandscapeSlotEngine.WILD) size = 126f;

        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(rotation);
        canvas.scale(symbolScale, symbolScale);
        if (winning) {
            int accent = accent(symbol);
            paint.setShader(new RadialGradient(0, 0, size * 0.62f,
                    new int[]{withAlpha(Color.WHITE, 85), withAlpha(accent, 92), 0x00000000},
                    null, Shader.TileMode.CLAMP));
            canvas.drawCircle(0, 0, size * 0.62f, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3.5f);
            paint.setColor(withAlpha(accent, 210));
            canvas.drawCircle(0, 0, size * 0.52f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        rect.set(-size / 2f, -size / 2f, size / 2f, size / 2f);
        paint.setAlpha(Math.max(0, Math.min(255, alpha)));
        canvas.drawBitmap(bitmap, null, rect, paint);
        paint.setAlpha(255);
        canvas.restore();
    }

    private void drawReelGlass(Canvas canvas, long now) {
        paint.setShader(new LinearGradient(REEL_X, REEL_Y, REEL_X, REEL_Y + REEL_H,
                new int[]{0x28FFFFFF, 0x05000000, 0x13000000, 0x24FFFFFF},
                new float[]{0f, 0.24f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(REEL_X, REEL_Y, REEL_X + REEL_W, REEL_Y + REEL_H, paint);
        paint.setShader(null);

        float sweep = fract(now / 5100f);
        float y = REEL_Y - 70 + sweep * (REEL_H + 140);
        paint.setShader(new LinearGradient(0, y - 34, 0, y + 34,
                new int[]{0x00FFFFFF, 0x18FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(REEL_X, REEL_Y, REEL_X + REEL_W, REEL_Y + REEL_H, paint);
        paint.setShader(null);
    }

    private void drawAnticipationFrame(Canvas canvas, long now) {
        long elapsed = now - spinStarted;
        if (elapsed < stopTimes[2]) return;
        float pulse = 0.5f + 0.5f * (float) Math.sin(now * 0.014f);
        float left = REEL_X + 4 * CELL_W;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f + pulse * 4f);
        paint.setColor(withAlpha(0xFFFFD65A, Math.round(110 + pulse * 120)));
        rect.set(left + 4, REEL_Y + 4, left + CELL_W - 4, REEL_Y + REEL_H - 4);
        canvas.drawRoundRect(rect, 15, 15, paint);
        paint.setStrokeWidth(2f);
        paint.setColor(withAlpha(0xFF5DE9FF, Math.round(70 + pulse * 80)));
        rect.inset(8, 8);
        canvas.drawRoundRect(rect, 12, 12, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawAnimatedPayline(Canvas canvas, long now) {
        long baseTime = phase == FEATURE ? featureStarted : revealStarted;
        int index = (int) (((now - baseTime) / 1450L) % current.wins.size());
        LandscapeSlotEngine.LineWin win = current.wins.get(Math.max(0, index));
        float progress = AnimationProfile.revealProgress((now - baseTime) % 1450L, 680L);
        float segments = progress * 4f;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(11f);
        paint.setColor(0x3329DFFF);
        drawPaylineSegments(canvas, win, 4f);
        paint.setStrokeWidth(5.5f);
        paint.setColor(win.symbol == LandscapeSlotEngine.WILD ? 0xFFFFD65A : 0xFF61E8FF);
        drawPaylineSegments(canvas, win, segments);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawPaylineSegments(Canvas canvas, LandscapeSlotEngine.LineWin win, float segmentProgress) {
        for (int reel = 0; reel < LandscapeSlotEngine.REELS - 1; reel++) {
            float local = AnimationProfile.clamp(segmentProgress - reel, 0f, 1f);
            if (local <= 0f) break;
            float x1 = REEL_X + reel * CELL_W + CELL_W / 2f;
            float y1 = REEL_Y + win.rows[reel] * CELL_H + CELL_H / 2f;
            float x2 = REEL_X + (reel + 1) * CELL_W + CELL_W / 2f;
            float y2 = REEL_Y + win.rows[reel + 1] * CELL_H + CELL_H / 2f;
            canvas.drawLine(x1, y1, x1 + (x2 - x1) * local, y1 + (y2 - y1) * local, paint);
        }
    }

    private boolean isWinningCell(int reel, int row, long now) {
        if ((phase != REVEALING && phase != FEATURE) || current == null || current.wins.isEmpty()) return false;
        long baseTime = phase == FEATURE ? featureStarted : revealStarted;
        int index = (int) (((now - baseTime) / 1450L) % current.wins.size());
        LandscapeSlotEngine.LineWin win = current.wins.get(Math.max(0, index));
        return reel < win.count && win.rows[reel] == row;
    }

    private void drawRightPanel(Canvas canvas, long now) {
        drawPanel(canvas, 1015, 126, 1258, 614, 22);
        label(canvas, "APUESTA POR LÍNEA", 1136, 170, 11, 0xFFAEB6C5);
        drawCircleButton(canvas, 1055, 216, 27, "−");
        drawCircleButton(canvas, 1218, 216, 27, "+");
        value(canvas, betPerLine + " CR", 1136, 225, 25, 0xFFFFD36A);
        label(canvas, "APUESTA TOTAL", 1065, 286, 10, 0xFF9BA4B4);
        value(canvas, numberFormat.format(betPerLine * LandscapeSlotEngine.LINES), 1065, 318, 19, Color.WHITE);
        label(canvas, "ÚLTIMO PREMIO", 1200, 286, 10, 0xFF9BA4B4);
        value(canvas, numberFormat.format(shownWin), 1200, 318, 19, 0xFFFFC952);
        drawSpinButton(canvas, now);
        drawPill(canvas, 1045, 558, 1228, 595, audio.isEnabled() ? "SONIDO ON" : "SONIDO OFF",
                audio.isEnabled() ? 0xFFFFD161 : 0xFF9BA4B4);
    }

    private void drawSpinButton(Canvas canvas, long now) {
        boolean ready = phase == IDLE;
        float idlePulse = ready ? 1f + 0.025f * (float) Math.sin(now * 0.0045f) : 1f;
        float press = pressedSpin ? AnimationProfile.pressScale(now - pressStarted) : 1f;
        float totalScale = idlePulse * press;

        canvas.save();
        canvas.scale(totalScale, totalScale, SPIN_X, SPIN_Y);
        float halo = 105f + 12f * (0.5f + 0.5f * (float) Math.sin(now * 0.006f));
        paint.setShader(new RadialGradient(SPIN_X, SPIN_Y, halo,
                new int[]{ready ? 0x55FFF5B0 : 0x337F62A8, ready ? 0x22FF9C16 : 0x113D245B, 0x00000000},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(SPIN_X, SPIN_Y, halo, paint);
        paint.setShader(null);

        paint.setShader(new RadialGradient(SPIN_X - 22, SPIN_Y - 28, 105,
                ready ? new int[]{0xFFFFF2A0, 0xFFFFB326, 0xFF8D4304}
                        : new int[]{0xFFC9B8E8, 0xFF74539E, 0xFF38224B},
                null, Shader.TileMode.CLAMP));
        canvas.drawCircle(SPIN_X, SPIN_Y, 91, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6);
        paint.setColor(ready ? 0xFFFFE89C : 0xFFD4C5EA);
        canvas.drawCircle(SPIN_X, SPIN_Y, 91, paint);
        paint.setStrokeWidth(2.5f);
        float rotation = (now % 3600L) / 10f;
        rect.set(SPIN_X - 101, SPIN_Y - 101, SPIN_X + 101, SPIN_Y + 101);
        paint.setColor(withAlpha(ready ? 0xFF62E8FF : 0xFFBFA8DF, 150));
        canvas.drawArc(rect, rotation, 70, false, paint);
        canvas.drawArc(rect, rotation + 180, 70, false, paint);
        paint.setStyle(Paint.Style.FILL);
        value(canvas, freeSpins > 0 ? "GIRO GRATIS" : "GIRAR", SPIN_X, SPIN_Y + 11,
                freeSpins > 0 ? 20 : 30, 0xFF2D1604);
        canvas.restore();
    }

    private void drawWinPresentation(Canvas canvas, long now) {
        if (phase == FEATURE) {
            float t = AnimationProfile.revealProgress(now - featureStarted, 850L);
            paint.setColor(withAlpha(0xFF430511, Math.round(100 * t)));
            canvas.drawRect(0, 0, DESIGN_W, DESIGN_H, paint);
            float scaleUp = 0.72f + 0.28f * t + 0.08f * (float) Math.sin(Math.PI * t);
            canvas.save();
            canvas.scale(scaleUp, scaleUp, 640, 610);
            paint.setShader(new LinearGradient(350, 555, 930, 662,
                    new int[]{0xFF650713, 0xFFF02B38, 0xFF7A0710}, null, Shader.TileMode.CLAMP));
            rect.set(350, 552, 930, 660);
            canvas.drawRoundRect(rect, 28, 28, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5);
            paint.setColor(0xFFFFD85C);
            canvas.drawRoundRect(rect, 28, 28, paint);
            paint.setStyle(Paint.Style.FILL);
            value(canvas, "30 GIROS GRATIS", 640, 615, 46, 0xFFFFE477);
            label(canvas, "MODO ROYAL ACTIVADO", 640, 645, 12, Color.WHITE);
            canvas.restore();
            return;
        }

        if (phase != REVEALING || targetWin <= 0) return;
        float ratio = targetWin / (float) Math.max(1, betPerLine * LandscapeSlotEngine.LINES);
        String title = ratio >= 20f ? "ROYAL WIN" : ratio >= 5f ? "BIG WIN" : "PREMIO";
        float t = AnimationProfile.revealProgress(now - revealStarted, 520L);
        float y = 648 - 22 * (1f - t);
        float width = ratio >= 5f ? 500 : 370;
        paint.setShader(new LinearGradient(640 - width / 2, y - 78, 640 + width / 2, y,
                new int[]{0xFF402005, 0xFFFFCF57, 0xFF9C570B, 0xFF402005}, null, Shader.TileMode.MIRROR));
        rect.set(640 - width / 2, y - 76, 640 + width / 2, y);
        canvas.drawRoundRect(rect, 24, 24, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(0xFFFFE79A);
        canvas.drawRoundRect(rect, 24, 24, paint);
        paint.setStyle(Paint.Style.FILL);
        label(canvas, title, 640, y - 49, 12, 0xFF4B2706);
        value(canvas, numberFormat.format(shownWin) + " CR", 640, y - 13, ratio >= 5f ? 34 : 28, 0xFF2A1402);
    }

    private void drawParticles(Canvas canvas) {
        for (Particle particle : particles) {
            float life = AnimationProfile.clamp(particle.life / particle.maxLife, 0f, 1f);
            canvas.save();
            canvas.translate(particle.x, particle.y);
            canvas.rotate(particle.rotation);
            paint.setColor(withAlpha(particle.color, Math.round(255 * life)));
            float size = particle.size * (0.55f + 0.45f * life);
            if (particle.diamond) {
                path.reset();
                path.moveTo(0, -size);
                path.lineTo(size, 0);
                path.lineTo(0, size);
                path.lineTo(-size, 0);
                path.close();
                canvas.drawPath(path, paint);
            } else {
                canvas.drawCircle(0, 0, size, paint);
            }
            canvas.restore();
        }
    }

    private void spawnStopBurst(int reel) {
        float cx = REEL_X + reel * CELL_W + CELL_W / 2f;
        float cy = REEL_Y + REEL_H / 2f;
        int count = reel == 4 ? 24 : 12;
        for (int i = 0; i < count; i++) {
            float angle = (float) (i * Math.PI * 2 / count + random.nextFloat() * 0.25);
            float speed = 70 + random.nextFloat() * (reel == 4 ? 190 : 110);
            particles.add(new Particle(cx, cy,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed,
                    80f, 0.42f + random.nextFloat() * 0.35f,
                    2.2f + random.nextFloat() * 3.8f,
                    i % 3 == 0 ? 0xFF5DE7FF : 0xFFFFD65A,
                    i % 2 == 0));
        }
    }

    private void spawnAnticipationDust() {
        for (int i = 0; i < 34; i++) {
            float x = REEL_X + 4 * CELL_W + random.nextFloat() * CELL_W;
            float y = REEL_Y + random.nextFloat() * REEL_H;
            particles.add(new Particle(x, y,
                    -25 + random.nextFloat() * 50,
                    -35 - random.nextFloat() * 70,
                    -15f, 0.75f + random.nextFloat() * 0.55f,
                    1.8f + random.nextFloat() * 3.2f,
                    i % 2 == 0 ? 0xFFFFD65A : 0xFF64E8FF,
                    true));
        }
    }

    private void spawnCelebration(int count) {
        for (int i = 0; i < count; i++) {
            float angle = (float) (random.nextDouble() * Math.PI * 2);
            float speed = 110 + random.nextFloat() * 330;
            int color;
            switch (i % 4) {
                case 0: color = 0xFFFFD65A; break;
                case 1: color = 0xFF64E8FF; break;
                case 2: color = 0xFFFFFFFF; break;
                default: color = 0xFFFF4F77; break;
            }
            particles.add(new Particle(640, 390,
                    (float) Math.cos(angle) * speed,
                    (float) Math.sin(angle) * speed - 80,
                    190f, 0.9f + random.nextFloat() * 1.25f,
                    2f + random.nextFloat() * 5f,
                    color, i % 3 != 0));
        }
    }

    private void drawPanel(Canvas canvas, float left, float top, float right, float bottom, float radius) {
        paint.setShader(new LinearGradient(left, top, right, bottom,
                new int[]{0xEE111621, 0xEE080A11, 0xEE171020}, null, Shader.TileMode.CLAMP));
        rect.set(left, top, right, bottom);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(0x66D69A37);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawCircleButton(Canvas canvas, float cx, float cy, float radius, String text) {
        paint.setShader(new RadialGradient(cx - 5, cy - 7, radius * 1.4f,
                new int[]{0xFF3B4558, 0xFF151A25, 0xFF07090D}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(0x99FFD56A);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        value(canvas, text, cx, cy + 9, 25, Color.WHITE);
    }

    private void drawPill(Canvas canvas, float left, float top, float right, float bottom, String text, int color) {
        paint.setColor(0xAA090C13);
        rect.set(left, top, right, bottom);
        canvas.drawRoundRect(rect, 20, 20, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(withAlpha(color, 150));
        canvas.drawRoundRect(rect, 20, 20, paint);
        paint.setStyle(Paint.Style.FILL);
        label(canvas, text, (left + right) / 2f, top + 25, 10, color);
    }

    private void label(Canvas canvas, String text, float x, float baseline, float size, int color) {
        textPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(size);
        textPaint.setColor(color);
        canvas.drawText(text, x, baseline, textPaint);
    }

    private void value(Canvas canvas, String text, float x, float baseline, float size, int color) {
        textPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(size);
        textPaint.setColor(color);
        canvas.drawText(text, x, baseline, textPaint);
    }

    private void drawFooter(Canvas canvas) {
        label(canvas, "CRÉDITOS FICTICIOS · EXPERIENCIA DEMOSTRATIVA", 640, 704, 10, 0xFF70798A);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (getWidth() <= 0 || getHeight() <= 0) return true;
        float x = (event.getX() - offsetX) / Math.max(0.001f, scale);
        float y = (event.getY() - offsetY) / Math.max(0.001f, scale);
        boolean spinHit = distance(x, y, SPIN_X, SPIN_Y) <= 108f;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (spinHit && phase == IDLE) {
                    pressedSpin = true;
                    pressStarted = SystemClock.uptimeMillis();
                    invalidate();
                    return true;
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (pressedSpin) {
                    pressedSpin = false;
                    if (spinHit) startSpin(SystemClock.uptimeMillis());
                    invalidate();
                    return true;
                }
                if (distance(x, y, 1055, 216) <= 42f && phase == IDLE) {
                    betPerLine = Math.max(1, betPerLine - 1);
                    audio.play(CinematicAudioEngine.Cue.BUTTON);
                } else if (distance(x, y, 1218, 216) <= 42f && phase == IDLE) {
                    betPerLine = Math.min(10, betPerLine + 1);
                    audio.play(CinematicAudioEngine.Cue.BUTTON);
                } else if (x >= 1035 && x <= 1238 && y >= 540 && y <= 610) {
                    audio.setEnabled(!audio.isEnabled());
                    if (audio.isEnabled()) audio.play(CinematicAudioEngine.Cue.BUTTON);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressedSpin = false;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private int accent(int symbol) {
        switch (symbol) {
            case LandscapeSlotEngine.SEVEN: return 0xFFFF3C4B;
            case LandscapeSlotEngine.BAR: return 0xFFFFC65B;
            case LandscapeSlotEngine.BELL: return 0xFFFFD65A;
            case LandscapeSlotEngine.DIAMOND: return 0xFF5DE7FF;
            case LandscapeSlotEngine.WILD: return 0xFFFFE379;
            default: return 0xFF74DBFF;
        }
    }

    private static int floorMod(int value, int divisor) {
        int result = value % divisor;
        return result < 0 ? result + divisor : result;
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static final class Particle {
        float x;
        float y;
        float vx;
        float vy;
        final float gravity;
        float life;
        final float maxLife;
        final float size;
        final int color;
        final boolean diamond;
        float rotation;
        final float spin;

        Particle(float x, float y, float vx, float vy, float gravity,
                 float life, float size, int color, boolean diamond) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.gravity = gravity;
            this.life = life;
            this.maxLife = life;
            this.size = size;
            this.color = color;
            this.diamond = diamond;
            this.spin = (vx + vy) * 0.15f;
        }
    }
}
