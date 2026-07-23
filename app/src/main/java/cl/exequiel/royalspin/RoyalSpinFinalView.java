package cl.exequiel.royalspin;

import android.content.Context;
import android.content.SharedPreferences;
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
import android.view.MotionEvent;
import android.view.View;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Final premium renderer. StakeSlotEngine remains the single source of game outcomes. */
public final class RoyalSpinFinalView extends View implements Choreographer.FrameCallback {
    private enum Phase { IDLE, SPINNING, REVEALING }

    private static final float W = 360f, H = 800f;
    private static final float REEL_LEFT = 20f, REEL_TOP = 183f;
    private static final float REEL_W = 60f, REEL_GAP = 4f, CELL_H = 84f;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final StakeSlotEngine engine = new StakeSlotEngine();
    private final Random random = new Random();
    private final Random visualRandom = new Random(0x155AA77EL);
    private final SharedPreferences prefs;
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(new Locale("es", "CL"));
    private final CasinoAudio audio;
    private final HapticEngine haptics;
    private final List<Particle> particles = new ArrayList<>();
    private final boolean[] stopTriggered = new boolean[5];
    private final long[] stopTimes = new long[5];

    private StakeSlotEngine.SpinResult current;
    private StakeSlotEngine.SpinResult pending;
    private Phase phase = Phase.IDLE;
    private long phaseStart;
    private long previousFrameNanos;
    private long fpsWindowNanos;
    private int fpsFrames;
    private int fps = 60;
    private int slowFrames;
    private int totalFrames;
    private int qualityTier = 2;
    private float dt = 1f / 60f;
    private float scale = 1f, offsetX, offsetY;
    private int credits;
    private int betPerLine;
    private int lastWin;
    private int shownWin;
    private int rounds;
    private boolean payoutApplied;
    private boolean rewardTriggered;
    private boolean soundEnabled;
    private boolean hapticEnabled;
    private boolean reducedMotion;
    private boolean frameLoop;
    private boolean spinPressed;
    private boolean anticipation;
    private String message = "20 líneas activas · toca GIRAR";

    public RoyalSpinFinalView(Context context, String demoMode) {
        super(context);
        prefs = context.getSharedPreferences("royal_spin_final", Context.MODE_PRIVATE);
        credits = prefs.getInt("credits", 5000);
        betPerLine = StakeSlotEngine.clampBet(prefs.getInt("bet", 1));
        rounds = prefs.getInt("rounds", 0);
        soundEnabled = prefs.getBoolean("sound", true);
        hapticEnabled = prefs.getBoolean("haptic", true);
        reducedMotion = prefs.getBoolean("reduced_motion", false);
        audio = new CasinoAudio(soundEnabled, 15);
        haptics = new HapticEngine(context, hapticEnabled);
        current = engine.spin(new Random(20260723L), betPerLine);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        setFocusable(true);
        setContentDescription("Royal Spin, tragamonedas de demostración con créditos ficticios");
        if (demoMode != null && !demoMode.isEmpty()) {
            postDelayed(() -> startSpin(demoMode), 550L);
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        frameLoop = true;
        previousFrameNanos = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        frameLoop = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!frameLoop) return;
        if (previousFrameNanos != 0L) {
            long delta = frameTimeNanos - previousFrameNanos;
            dt = Math.min(.05f, delta / 1_000_000_000f);
            totalFrames++;
            fpsFrames++;
            if (delta > 22_000_000L) slowFrames++;
        }
        previousFrameNanos = frameTimeNanos;
        if (fpsWindowNanos == 0L) fpsWindowNanos = frameTimeNanos;
        if (frameTimeNanos - fpsWindowNanos >= 1_000_000_000L) {
            fps = Math.max(1, fpsFrames);
            float slowRate = totalFrames == 0 ? 0f : slowFrames / (float) totalFrames;
            qualityTier = fps < 40 || slowRate > .28f ? 0 : fps < 53 || slowRate > .14f ? 1 : 2;
            fpsFrames = 0;
            fpsWindowNanos = frameTimeNanos;
        }
        update(SystemClock.uptimeMillis());
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void update(long now) {
        updateParticles(now);
        if (phase == Phase.SPINNING) {
            long elapsed = now - phaseStart;
            for (int reel = 0; reel < 5; reel++) {
                if (!stopTriggered[reel] && elapsed >= stopTimes[reel]) {
                    stopTriggered[reel] = true;
                    audio.playReelStop(reel);
                    haptics.reelStop(reel);
                    spawnImpact(reel, now);
                }
            }
            if (elapsed >= stopTimes[4] + 190L) settle(now);
        } else if (phase == Phase.REVEALING) {
            long elapsed = now - phaseStart;
            shownWin = Math.round(lastWin * easeOut(clamp(elapsed / 1450f)));
            if (!rewardTriggered && elapsed >= 120L) {
                rewardTriggered = true;
                if (lastWin > 0) {
                    StakeSlotEngine.LineWin featured = featuredWin();
                    String symbol = featured == null ? "" : featured.symbol;
                    int count = featured == null ? 0 : featured.count;
                    audio.playSymbolWin(symbol, count, current.payoutMultiplier());
                    haptics.symbolWin(symbol, current.payoutMultiplier());
                    spawnCelebration(now, symbol, current.payoutMultiplier());
                } else {
                    audio.playLose();
                }
            }
            if (elapsed >= revealDurationMs()) finishRound();
        }
    }

    private long revealDurationMs() {
        if (lastWin <= 0) return 900L;
        double m = current == null ? 0d : current.payoutMultiplier();
        long duration = m >= 100d ? 6000L : m >= 25d ? 5200L : m >= 5d ? 4400L : 2850L;
        return reducedMotion ? Math.min(duration, 2300L) : duration;
    }

    private void settle(long now) {
        if (pending == null) return;
        current = pending;
        pending = null;
        lastWin = current.totalPayout;
        shownWin = 0;
        if (!payoutApplied) {
            credits += lastWin;
            payoutApplied = true;
            save();
        }
        phase = Phase.REVEALING;
        phaseStart = now;
        rewardTriggered = false;
        message = lastWin > 0 ? "PREMIO REAL · coreografía cinematográfica" : "Resultado cerrado · sin premio";
    }

    private void finishRound() {
        shownWin = lastWin;
        phase = Phase.IDLE;
        spinPressed = false;
        message = lastWin > 0
                ? "Ganaste " + numbers.format(lastWin) + " CR · " + multiplier(current.payoutMultiplier()) + "x"
                : "Sin premio · cada giro es independiente";
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        scale = Math.min(getWidth() / W, getHeight() / H);
        offsetX = (getWidth() - W * scale) / 2f;
        offsetY = (getHeight() - H * scale) / 2f;
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        drawBackground(canvas, now);
        drawHeader(canvas, now);
        float zoom = phase == Phase.REVEALING && current != null
                ? PremiumReelDynamics.cameraZoom(current.payoutMultiplier(), now - phaseStart, reducedMotion) : 1f;
        canvas.save();
        canvas.scale(zoom, zoom, 180, 328);
        drawMachine(canvas, now);
        drawCelebration(canvas, now);
        canvas.restore();
        drawParticles(canvas, now);
        drawControls(canvas, now);
        canvas.restore();
    }

    private void drawBackground(Canvas c, long now) {
        p.setShader(new LinearGradient(0, 0, 0, H,
                new int[]{0xFF010205, 0xFF090616, 0xFF170A17, 0xFF020205},
                new float[]{0f, .33f, .72f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
        int stars = qualityTier == 2 ? 58 : qualityTier == 1 ? 38 : 18;
        float t = now * .00011f;
        for (int i = 0; i < stars; i++) {
            float x = (i * 89.1f + (float)Math.sin(t + i) * 23f + 410f) % W;
            float y = (i * 131.7f + (float)Math.cos(t * .8f + i) * 29f + 900f) % H;
            int rgb = i % 4 == 0 ? 0xF6C453 : i % 4 == 1 ? 0x7B66FF : 0x58D7FF;
            int alpha = 18 + (i % 5) * 8;
            p.setColor((alpha << 24) | rgb);
            c.drawCircle(x, y, i % 8 == 0 ? 1.7f : .8f, p);
        }
        p.setShader(new RadialGradient(180, 305, 285,
                new int[]{0x354C2DFF, 0x14220B48, 0x00000000}, null, Shader.TileMode.CLAMP));
        c.drawCircle(180, 305, 285, p);
        p.setShader(null);
        float floor = .5f + .5f * (float)Math.sin(now * .0014f);
        p.setShader(new RadialGradient(180, 745, 210,
                new int[]{((20 + (int)(24 * floor)) << 24) | 0x00F6C453, 0x00000000},
                null, Shader.TileMode.CLAMP));
        c.drawOval(new RectF(28, 690, 332, 806), p);
        p.setShader(null);
    }

    private void drawHeader(Canvas c, long now) {
        float pulse = reducedMotion ? 1f : 1f + .045f * (float)Math.sin(now * .003f);
        c.save();
        c.scale(pulse, pulse, 180, 27);
        crown(c, 180, 27, 20, 0xFFF6C453);
        c.restore();
        goldText(c, "ROYAL SPIN", 180, 61, 25, Paint.Align.CENTER);
        text(c, "MATH TRAZABLE · SOLO CRÉDITOS FICTICIOS", 180, 79, 6.8f,
                0xFFAAB0C0, true, Paint.Align.CENTER);
        premiumButton(c, 10, 21, 75, 51, "RESET", false, now);
        premiumButton(c, 285, 21, 350, 51, soundEnabled ? "SFX ON" : "SFX OFF", soundEnabled, now);
        panel(c, 84, 65, 276, 87, 11, 0xE70B0E17, reducedMotion ? 0xFF62D9FF : 0xFFF6C453);
        text(c, "v1.5 RC · " + (reducedMotion ? "MOVIMIENTO REDUCIDO" : "CINEMATIC FULL"),
                180, 79.5f, 7.2f, reducedMotion ? 0xFFBCEEFF : 0xFFFFE6A0, true, Paint.Align.CENTER);
        premiumPanel(c, 14, 92, 346, 151, 18);
        text(c, "SALDO", 31, 114, 8, 0xFF9AA2B2, true, Paint.Align.LEFT);
        text(c, numbers.format(credits) + " CR", 31, 140, 21, Color.WHITE, true, Paint.Align.LEFT);
        text(c, "RTP", 238, 114, 8, 0xFF9AA2B2, true, Paint.Align.CENTER);
        goldText(c, "95,48%", 238, 140, 16, Paint.Align.CENTER);
        text(c, "RONDA " + rounds, 330, 140, 8, 0xFF9AA2B2, true, Paint.Align.RIGHT);
    }

    private void drawMachine(Canvas c, long now) {
        float shakeX = 0f, shakeY = 0f;
        if (!reducedMotion && phase == Phase.SPINNING) {
            long elapsed = now - phaseStart;
            for (int reel = 0; reel < 5; reel++) {
                float impact = PremiumReelDynamics.impactPulse(elapsed - stopTimes[reel]);
                shakeX += (float)Math.sin(elapsed * .22f + reel) * impact * (1.1f + reel * .13f);
                shakeY += (float)Math.cos(elapsed * .27f + reel) * impact * .65f;
            }
        }
        c.save();
        c.translate(shakeX, shakeY);
        p.setShadowLayer(25, 0, 11, 0xDD000000);
        p.setShader(new LinearGradient(9, 157, 351, 493,
                new int[]{0xFF261406, 0xFFFFE59B, 0xFF8E5713, 0xFFFFD36A, 0xFF241305},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(9, 157, 351, 493), 30, 30, p);
        p.clearShadowLayer();
        p.setShader(null);
        panel(c, 14, 163, 346, 488, 25, 0xFF05070C, 0xFFFFD76A);
        panel(c, 19, 170, 341, 453, 18, 0xFF090B10, 0xFFB9A675);
        StakeSlotEngine.SpinResult source = pending != null ? pending : current;
        long elapsed = phase == Phase.SPINNING ? now - phaseStart : Long.MAX_VALUE;
        for (int reel = 0; reel < 5; reel++) {
            float left = REEL_LEFT + reel * (REEL_W + REEL_GAP);
            RectF clip = new RectF(left, REEL_TOP, left + REEL_W, REEL_TOP + CELL_H * 3);
            c.save();
            path.reset();
            path.addRoundRect(clip, 8, 8, Path.Direction.CW);
            c.clipPath(path);
            if (phase == Phase.SPINNING && elapsed < stopTimes[reel]) {
                drawSpinningReel(c, reel, left, elapsed);
            } else {
                drawStoppedReel(c, source, reel, left, now);
            }
            drawReelGlass(c, clip, reel, now);
            c.restore();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(reel == 2 ? 2.4f : 1.15f);
            p.setShader(new LinearGradient(left, clip.top, left + REEL_W, clip.bottom,
                    reel == 2
                            ? new int[]{0xFFFFF0A8, 0xFFF0AA2C, 0xFFFFE99B}
                            : new int[]{0xFF626875, 0xFFC8B78E, 0xFF555B67},
                    null, Shader.TileMode.MIRROR));
            c.drawRoundRect(clip, 8, 8, p);
            p.setShader(null);
            p.setStyle(Paint.Style.FILL);
        }
        drawAnticipation(c, now);
        drawWinLine(c, now);
        text(c, "20 LÍNEAS · ♛ WILD · RESULTADO PRECALCULADO", 180, 474,
                7, 0xFFB7BBC5, true, Paint.Align.CENTER);
        c.restore();
    }

    private void drawSpinningReel(Canvas c, int reel, float left, long elapsed) {
        long stop = stopTimes[reel];
        float velocity = PremiumReelDynamics.velocity(elapsed, stop);
        float travel = (elapsed * (18f + velocity * 28f) + reel * 47f) % CELL_H;
        int base = (int)(elapsed / Math.max(25f, 66f - velocity * 14f)) + reel * 9;
        for (int item = -2; item <= 4; item++) {
            int index = Math.floorMod(base + item, StakeSlotEngine.SYMBOLS.length);
            float centerY = REEL_TOP + item * CELL_H + travel + CELL_H / 2f;
            float normalized = (centerY - (REEL_TOP + CELL_H * 1.5f)) / (CELL_H * 1.5f);
            float sy = PremiumReelDynamics.cylinderScale(normalized);
            float alpha = PremiumReelDynamics.cylinderAlpha(normalized);
            c.save();
            c.scale(1f, sy, left + REEL_W / 2f, centerY);
            drawSymbol(c, left + 2, centerY - CELL_H / 2f + 2, REEL_W - 4, CELL_H - 4,
                    StakeSlotEngine.SYMBOLS[index], false, elapsed, alpha);
            c.restore();
        }
        int streaks = qualityTier == 2 ? 10 : qualityTier == 1 ? 6 : 3;
        for (int i = 0; i < streaks; i++) {
            float y = REEL_TOP + ((elapsed * (1.7f + i * .13f) + i * 41f) % (CELL_H * 3));
            int alpha = Math.min(125, Math.round(32 + velocity * 35));
            p.setShader(new LinearGradient(left + 3, y, left + REEL_W - 3, y,
                    new int[]{0x00FFFFFF, (alpha << 24) | 0x00FFFFFF, 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(left + 4, y, left + REEL_W - 4, y + 2f + velocity), 2, 2, p);
            p.setShader(null);
        }
    }

    private void drawStoppedReel(Canvas c, StakeSlotEngine.SpinResult result, int reel,
                                 float left, long now) {
        if (result == null) return;
        long d = now - phaseStart - stopTimes[reel];
        float bounce = (phase == Phase.SPINNING || phase == Phase.REVEALING)
                ? PremiumReelDynamics.landingOffset(d, reducedMotion) : 0f;
        for (int row = 0; row < 3; row++) {
            drawSymbol(c, left + 2, REEL_TOP + row * CELL_H + 2 + bounce,
                    REEL_W - 4, CELL_H - 4, result.board[reel][row],
                    winningCell(reel, row, now), now, 1f);
        }
    }

    private void drawSymbol(Canvas c, float left, float top, float width, float height,
                            String symbol, boolean winning, long now, float alpha) {
        int color = StakeSlotEngine.symbolColor(symbol);
        SymbolAnimationDirector.Frame frame = winning
                ? SymbolAnimationDirector.frame(symbol, featuredCount(), currentMultiplier(),
                Math.max(0L, now - phaseStart))
                : SymbolAnimationDirector.frame("", 0, 0d, 0L);
        float cx = left + width / 2f, cy = top + height / 2f;
        c.save();
        if (winning && !reducedMotion) {
            c.translate(0, frame.lift);
            c.rotate(frame.rotation, cx, cy);
            c.scale(frame.scale, frame.scale, cx, cy);
        }
        if (winning) {
            int haloAlpha = Math.min(120, Math.round(55 + frame.energy * 55));
            p.setColor((haloAlpha << 24) | (color & 0x00FFFFFF));
            c.drawRoundRect(new RectF(left - 3, top - 3, left + width + 3, top + height + 3), 11, 11, p);
        }
        p.setAlpha(Math.round(255 * alpha));
        p.setShader(new LinearGradient(left, top, left, top + height,
                new int[]{0xFF575148, 0xFF18191D, 0xFF07080A, 0xFF40382D},
                new float[]{0f, .19f, .7f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(left, top, left + width, top + height), 8, 8, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(0x77FFF0BE);
        c.drawRoundRect(new RectF(left + 1, top + 1, left + width - 1, top + height - 1), 7, 7, p);
        p.setStyle(Paint.Style.FILL);
        float glint = (now * .085f + left * 2f + top) % (height + 42f) - 21f;
        p.setShader(new LinearGradient(left, top + glint - 8, left + width, top + glint + 8,
                new int[]{0x00FFFFFF, 0x42FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(left + 2, top + glint - 5, left + width - 2, top + glint + 5), 5, 5, p);
        p.setShader(null);
        p.setAlpha(Math.round(255 * alpha));
        p.setShadowLayer(winning ? 11 : 4, 0, 2, winning ? color : 0x99000000);
        if (StakeSlotEngine.WILD.equals(symbol)) {
            crown(c, cx, cy - 5, 24, color);
            text(c, "WILD", cx, top + height - 11, 8, 0xFFFFE7A0, true, Paint.Align.CENTER);
        } else if (StakeSlotEngine.DIAMOND.equals(symbol)) {
            diamond(c, cx, cy, 21, color);
        } else if (StakeSlotEngine.BELL.equals(symbol)) {
            bell(c, cx, cy, 22, color);
        } else if (StakeSlotEngine.BAR.equals(symbol)) {
            bar(c, cx, cy, width, color);
        } else {
            String label = StakeSlotEngine.displayLabel(symbol);
            float size = label.length() >= 4 ? 15 : label.length() >= 2 ? 23 : 34;
            text(c, label, cx, cy + size * .34f, size, color, true, Paint.Align.CENTER);
        }
        p.clearShadowLayer();
        p.setAlpha(255);
        c.restore();
    }

    private void drawReelGlass(Canvas c, RectF clip, int reel, long now) {
        p.setShader(new LinearGradient(0, clip.top, 0, clip.bottom,
                new int[]{0xD5000000, 0x10000000, 0x00000000, 0x10000000, 0xD5000000},
                new float[]{0f, .16f, .5f, .84f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(clip, p);
        p.setShader(null);
        float x = clip.left + ((now * (.025f + reel * .002f) + reel * 23f) % REEL_W);
        p.setShader(new LinearGradient(x - 12, clip.top, x + 12, clip.bottom,
                new int[]{0x00FFFFFF, 0x30FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRect(clip, p);
        p.setShader(null);
    }

    private void drawAnticipation(Canvas c, long now) {
        if (phase != Phase.SPINNING || !anticipation || reducedMotion) return;
        long elapsed = now - phaseStart;
        float pulse = PremiumReelDynamics.anticipationPulse(elapsed, PremiumReelDynamics.BASE_STOPS[4]);
        if (pulse <= 0f) return;
        float left = REEL_LEFT + 4 * (REEL_W + REEL_GAP);
        RectF box = new RectF(left - 5, REEL_TOP - 7, left + REEL_W + 5, REEL_TOP + CELL_H * 3 + 7);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f + pulse * 4f);
        p.setColor(((55 + Math.round(pulse * 150)) << 24) | 0x00FFD76A);
        c.drawRoundRect(box, 12, 12, p);
        for (int i = 0; i < 3; i++) {
            float radius = 7 + i * 8 + pulse * 5;
            p.setColor(((40 + i * 18) << 24) | (i % 2 == 0 ? 0x00FFD76A : 0x0065DFFF));
            c.drawCircle(left + REEL_W / 2f, REEL_TOP - 13, radius, p);
        }
        p.setStyle(Paint.Style.FILL);
        panel(c, 216, 158, 342, 179, 10, 0xE3130C04, 0xFFFFD76A);
        text(c, "ANTICIPACIÓN · ÚLTIMO REEL", 279, 172, 6.7f, 0xFFFFEAB0, true, Paint.Align.CENTER);
    }

    private void drawWinLine(Canvas c, long now) {
        StakeSlotEngine.LineWin win = rotatingWin(now);
        if (win == null) return;
        path.reset();
        for (int reel = 0; reel < 5; reel++) {
            float x = REEL_LEFT + reel * (REEL_W + REEL_GAP) + REEL_W / 2f;
            float y = REEL_TOP + win.rows[reel] * CELL_H + CELL_H / 2f;
            if (reel == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        int accent = StakeSlotEngine.symbolColor(win.symbol);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(9);
        p.setColor((65 << 24) | (accent & 0x00FFFFFF));
        c.drawPath(path, p);
        p.setStrokeWidth(3.8f);
        p.setColor(0xFFFFEDAE);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        panel(c, 85, 407, 275, 440, 16, 0xF21A1004, accent);
        text(c, win.symbol + " · LÍNEA " + (win.lineIndex + 1) + " · +"
                        + numbers.format(win.payout) + " CR",
                180, 429, 9, 0xFFFFEDB2, true, Paint.Align.CENTER);
    }

    private void drawCelebration(Canvas c, long now) {
        if (phase != Phase.REVEALING || current == null || lastWin <= 0) return;
        StakeSlotEngine.LineWin win = featuredWin();
        if (win == null) return;
        long elapsed = now - phaseStart;
        SymbolAnimationDirector.Frame f = SymbolAnimationDirector.frame(win.symbol, win.count,
                current.payoutMultiplier(), elapsed);
        int accent = StakeSlotEngine.symbolColor(win.symbol);
        if (!reducedMotion) {
            float radius = 22 + f.ring * 145;
            int alpha = Math.max(0, Math.round(125 * (1f - f.ring)));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f + f.energy * 2f);
            p.setColor((alpha << 24) | (accent & 0x00FFFFFF));
            c.drawOval(new RectF(180 - radius * 1.8f, 310 - radius,
                    180 + radius * 1.8f, 310 + radius), p);
            p.setStyle(Paint.Style.FILL);
        }
        switch (f.style) {
            case BELL: drawBellVfx(c, f, win, elapsed); break;
            case BAR: drawBarVfx(c, f, win, elapsed); break;
            case SEVEN: drawSevenVfx(c, f, win, elapsed); break;
            case DIAMOND: drawDiamondVfx(c, f, win, elapsed); break;
            case WILD: drawWildVfx(c, f, win, elapsed); break;
            default: drawCardVfx(c, f, win, elapsed); break;
        }
        drawTierTitle(c, f, elapsed);
    }

    private void drawBellVfx(Canvas c, SymbolAnimationDirector.Frame f, StakeSlotEngine.LineWin win, long elapsed) {
        for (int reel = 0; reel < win.count; reel++) {
            float cx = cellX(reel), cy = cellY(win.rows[reel]);
            for (int ring = 0; ring < (qualityTier == 2 ? 3 : 2); ring++) {
                float q = (f.ring + ring * .24f) % 1f;
                float r = 18 + q * 45;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.2f + ring * .45f);
                p.setColor((Math.round(105 * (1f - q)) << 24) | 0x00FFD76A);
                c.drawArc(new RectF(cx - r * 1.45f, cy - r, cx + r * 1.45f, cy + r), -45, 90, false, p);
                c.drawArc(new RectF(cx - r * 1.45f, cy - r, cx + r * 1.45f, cy + r), 135, 90, false, p);
            }
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawBarVfx(Canvas c, SymbolAnimationDirector.Frame f, StakeSlotEngine.LineWin win, long elapsed) {
        float lock = clamp(elapsed / 480f);
        for (int reel = 0; reel < win.count; reel++) {
            float cx = cellX(reel), cy = cellY(win.rows[reel]);
            float gap = 29 * (1f - easeOut(lock));
            p.setShader(new LinearGradient(cx - 38, cy, cx + 38, cy,
                    new int[]{0xFF2C1906, 0xFFFFE08A, 0xFF2C1906}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(cx - 37 - gap, cy - 6, cx - 20 - gap, cy + 6), 3, 3, p);
            c.drawRoundRect(new RectF(cx + 20 + gap, cy - 6, cx + 37 + gap, cy + 6), 3, 3, p);
            p.setShader(null);
        }
    }

    private void drawSevenVfx(Canvas c, SymbolAnimationDirector.Frame f, StakeSlotEngine.LineWin win, long elapsed) {
        if (reducedMotion) return;
        float slash = clamp((elapsed - 100f) / 560f);
        float x = 25 + slash * 350;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(16);
        p.setShader(new LinearGradient(45, 420, 315, 170,
                new int[]{0x00FF234E, 0xDFFF234E, 0xFFFFD76A, 0x00FF234E}, null, Shader.TileMode.CLAMP));
        c.drawLine(x - 150, 430, x + 85, 165, p);
        p.setShader(null);
        p.setStrokeWidth(3);
        p.setColor(0xFFFFE08A);
        c.drawLine(x - 145, 430, x + 90, 165, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawDiamondVfx(Canvas c, SymbolAnimationDirector.Frame f, StakeSlotEngine.LineWin win, long elapsed) {
        int rays = qualityTier == 2 ? 10 : qualityTier == 1 ? 7 : 4;
        p.setStyle(Paint.Style.STROKE);
        for (int reel = 0; reel < win.count; reel++) {
            float cx = cellX(reel), cy = cellY(win.rows[reel]);
            for (int ray = 0; ray < rays; ray++) {
                double a = ray * Math.PI * 2 / rays + elapsed * .0015;
                float len = 29 + 22 * f.energy;
                p.setStrokeWidth(ray % 2 == 0 ? 2f : 1f);
                p.setColor(ray % 2 == 0 ? 0xAA59DBFF : 0x88FFFFFF);
                c.drawLine(cx, cy, cx + (float)Math.cos(a) * len, cy + (float)Math.sin(a) * len, p);
            }
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawWildVfx(Canvas c, SymbolAnimationDirector.Frame f, StakeSlotEngine.LineWin win, long elapsed) {
        float cx = 180, cy = 31 - (reducedMotion ? 0 : 7 * f.enter);
        int rays = qualityTier == 2 ? 18 : qualityTier == 1 ? 12 : 8;
        p.setStyle(Paint.Style.STROKE);
        for (int ray = 0; ray < rays; ray++) {
            double a = ray * Math.PI * 2 / rays + elapsed * .0008;
            float outer = 53 + 12 * f.energy;
            p.setStrokeWidth(ray % 2 == 0 ? 2f : 1f);
            p.setColor(ray % 2 == 0 ? 0xAAFFD76A : 0x7771DFFF);
            c.drawLine(cx + (float)Math.cos(a) * 28, cy + (float)Math.sin(a) * 28,
                    cx + (float)Math.cos(a) * outer, cy + (float)Math.sin(a) * outer, p);
        }
        p.setStyle(Paint.Style.FILL);
        int jewels = qualityTier == 2 ? 12 : 7;
        for (int i = 0; i < jewels; i++) {
            double a = i * Math.PI * 2 / jewels + elapsed * .0018;
            p.setColor(i % 3 == 0 ? 0xDDFF5CA8 : i % 3 == 1 ? 0xDD66DFFF : 0xDDFFD76A);
            c.drawCircle(cx + (float)Math.cos(a) * 48, cy + (float)Math.sin(a) * 22, 2f + f.energy, p);
        }
    }

    private void drawCardVfx(Canvas c, SymbolAnimationDirector.Frame f, StakeSlotEngine.LineWin win, long elapsed) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        for (int reel = 0; reel < win.count; reel++) {
            float cx = cellX(reel), cy = cellY(win.rows[reel]);
            float r = 25 + 5 * f.energy;
            p.setColor(0x887BDFFF);
            c.drawRoundRect(new RectF(cx - r, cy - r, cx + r, cy + r), 9, 9, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawTierTitle(Canvas c, SymbolAnimationDirector.Frame f, long elapsed) {
        String title = f.tier == SymbolAnimationDirector.Tier.ROYAL ? "ROYAL WIN"
                : f.tier == SymbolAnimationDirector.Tier.MEGA ? "MEGA WIN"
                : f.tier == SymbolAnimationDirector.Tier.BIG ? "BIG WIN" : "PREMIO";
        float enter = easeOut(clamp(elapsed / 520f));
        float y = 170 - 14 * enter;
        p.setShadowLayer(16, 0, 4, 0xCCF6B83F);
        goldText(c, title, 180, y, f.tier == SymbolAnimationDirector.Tier.ROYAL ? 25 : 21, Paint.Align.CENTER);
        p.clearShadowLayer();
    }

    private void drawControls(Canvas c, long now) {
        premiumPanel(c, 15, 505, 345, 671, 22);
        text(c, "APUESTA POR LÍNEA", 180, 530, 9, 0xFFADB3C0, true, Paint.Align.CENTER);
        premiumButton(c, 26, 543, 82, 596, "−", false, now);
        panel(c, 101, 543, 259, 596, 17, 0xFF090B10, 0xFFC58B29);
        goldText(c, betPerLine + " CR", 180, 578, 22, Paint.Align.CENTER);
        premiumButton(c, 278, 543, 334, 596, "+", false, now);
        int totalBet = betPerLine * StakeSlotEngine.LINE_COUNT;
        text(c, "APUESTA TOTAL", 35, 621, 8, 0xFF9AA2B2, true, Paint.Align.LEFT);
        text(c, numbers.format(totalBet) + " CR", 35, 646, 16, Color.WHITE, true, Paint.Align.LEFT);
        text(c, "ÚLTIMO PREMIO", 325, 621, 8, 0xFF9AA2B2, true, Paint.Align.RIGHT);
        int display = phase == Phase.REVEALING ? shownWin : lastWin;
        goldText(c, numbers.format(display) + " CR", 325, 646, 16, Paint.Align.RIGHT);
        text(c, message, 180, 696, 9.5f, 0xFFE2E4EA, true, Paint.Align.CENTER);
        drawSpinButton(c, now);
        float slow = totalFrames == 0 ? 0f : slowFrames * 100f / totalFrames;
        String quality = qualityTier == 2 ? "ULTRA" : qualityTier == 1 ? "ALTA" : "LITE";
        text(c, "FPS " + fps + " · lentos " + String.format(Locale.US, "%.1f", slow)
                        + "% · " + quality + " · ficticio",
                180, 790, 6.8f, 0xFF7B8290, false, Paint.Align.CENTER);
    }

    private void drawSpinButton(Canvas c, long now) {
        float pulse = phase == Phase.IDLE && !reducedMotion ? .5f + .5f * (float)Math.sin(now * .0045f) : 0f;
        float press = spinPressed ? .955f : 1f;
        c.save();
        c.scale(press, press, 180, 739);
        p.setShadowLayer(13 + pulse * 10, 0, 5, phase == Phase.IDLE ? 0xAAF6C453 : 0xAA7045C0);
        p.setShader(new LinearGradient(34, 711, 326, 766,
                phase == Phase.IDLE
                        ? new int[]{0xFFFFF1A8, 0xFFF6B83F, 0xFFFFD96C}
                        : new int[]{0xFF8B65D8, 0xFF58348F, 0xFF8B65D8},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(34, 711, 326, 766), 28, 28, p);
        p.clearShadowLayer();
        p.setShader(null);
        if (phase == Phase.IDLE && !reducedMotion) {
            float sweep = 25 + (now * .12f % 340f);
            p.setShader(new LinearGradient(sweep - 45, 711, sweep + 45, 766,
                    new int[]{0x00FFFFFF, 0x99FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(36, 713, 324, 764), 26, 26, p);
            p.setShader(null);
        }
        text(c, phase == Phase.IDLE ? "GIRAR" : "OMITIR ANIMACIÓN", 180, 746,
                phase == Phase.IDLE ? 18 : 14, phase == Phase.IDLE ? 0xFF1B1003 : Color.WHITE,
                true, Paint.Align.CENTER);
        c.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = (event.getX() - offsetX) / Math.max(.0001f, scale);
        float y = (event.getY() - offsetY) / Math.max(.0001f, scale);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            spinPressed = y > 690;
            invalidate();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            spinPressed = false;
            invalidate();
            return true;
        }
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        spinPressed = false;
        if (x > 280 && y < 62) {
            soundEnabled = !soundEnabled;
            audio.setEnabled(soundEnabled);
            audio.playTap();
            save();
            return true;
        }
        if (x < 82 && y < 62 && phase == Phase.IDLE) {
            credits = 5000;
            lastWin = shownWin = rounds = 0;
            message = "Saldo demo reiniciado";
            audio.playTap(); haptics.tap(); save();
            return true;
        }
        if (x > 82 && x < 278 && y >= 62 && y <= 91 && phase == Phase.IDLE) {
            reducedMotion = !reducedMotion;
            message = reducedMotion ? "Movimiento reducido activado" : "Animación cinematográfica activada";
            audio.playTap(); haptics.tap(); save();
            return true;
        }
        if (phase != Phase.IDLE) {
            if (y > 695) skipAnimation();
            return true;
        }
        if (y > 530 && y < 610 && x < 100) {
            betPerLine = StakeSlotEngine.clampBet(betPerLine - 1);
            audio.playTap(); haptics.tap(); save();
        } else if (y > 530 && y < 610 && x > 260) {
            betPerLine = StakeSlotEngine.clampBet(betPerLine + 1);
            audio.playTap(); haptics.tap(); save();
        } else if (y > 690) {
            startSpin(null);
        }
        return true;
    }

    private void startSpin(String demoMode) {
        if (phase != Phase.IDLE) return;
        int totalBet = betPerLine * StakeSlotEngine.LINE_COUNT;
        boolean demo = demoMode != null;
        if (!demo && credits < totalBet) {
            message = "Saldo insuficiente · pulsa RESET";
            audio.playError(); haptics.error(); return;
        }
        if (!demo) credits -= totalBet;
        pending = demo ? createShowcase(demoMode) : engine.spin(random, betPerLine);
        anticipation = PremiumReelDynamics.shouldAnticipate(pending)
                || "anticipation".equalsIgnoreCase(demoMode);
        for (int reel = 0; reel < 5; reel++) {
            stopTimes[reel] = PremiumReelDynamics.stopTime(reel, anticipation, reducedMotion);
            stopTriggered[reel] = false;
        }
        phase = Phase.SPINNING;
        phaseStart = SystemClock.uptimeMillis();
        lastWin = shownWin = 0;
        payoutApplied = rewardTriggered = false;
        particles.clear();
        rounds++;
        message = anticipation ? "Resultado fijado · anticipación válida" : "Resultado fijado · giro en ejecución";
        audio.playSpinStart(); haptics.tap(); save();
    }

    private StakeSlotEngine.SpinResult createShowcase(String requested) {
        String key = requested == null ? "WILD" : requested.trim().toUpperCase(Locale.US);
        String symbol;
        if (key.contains("BELL") || key.contains("CAMPANA")) symbol = StakeSlotEngine.BELL;
        else if (key.contains("BAR")) symbol = StakeSlotEngine.BAR;
        else if (key.contains("SEVEN") || key.equals("7") || key.contains("ANTICIP")) symbol = StakeSlotEngine.SEVEN;
        else if (key.contains("DIAMOND") || key.contains("GEMA")) symbol = StakeSlotEngine.DIAMOND;
        else if (key.contains("Q") || key.contains("QUEEN")) symbol = StakeSlotEngine.QUEEN;
        else symbol = StakeSlotEngine.WILD;
        String[][] board = new String[5][3];
        String[] middle = {StakeSlotEngine.ACE, StakeSlotEngine.KING, StakeSlotEngine.QUEEN,
                StakeSlotEngine.JACK, StakeSlotEngine.BELL};
        String[] bottom = {StakeSlotEngine.JACK, StakeSlotEngine.QUEEN, StakeSlotEngine.KING,
                StakeSlotEngine.ACE, StakeSlotEngine.BAR};
        for (int reel = 0; reel < 5; reel++) {
            board[reel][0] = symbol;
            board[reel][1] = middle[reel];
            board[reel][2] = bottom[reel];
        }
        return engine.evaluate(board, new int[5], betPerLine);
    }

    private void skipAnimation() {
        long now = SystemClock.uptimeMillis();
        if (phase == Phase.SPINNING) settle(now);
        if (phase == Phase.REVEALING) finishRound();
        particles.clear();
        audio.stopRewardSequence();
        haptics.cancel();
    }

    private void spawnImpact(int reel, long now) {
        if (reducedMotion) return;
        float cx = REEL_LEFT + reel * (REEL_W + REEL_GAP) + REEL_W / 2f;
        int amount = qualityTier == 2 ? 18 : qualityTier == 1 ? 11 : 6;
        for (int i = 0; i < amount; i++) {
            float angle = (float)(visualRandom.nextDouble() * Math.PI * 2);
            float speed = 32 + visualRandom.nextFloat() * 82;
            particles.add(new Particle(cx, REEL_TOP + CELL_H * 3 + 2,
                    (float)Math.cos(angle) * speed, -Math.abs((float)Math.sin(angle)) * speed,
                    now, 520 + visualRandom.nextInt(460), 0xFFF6C453,
                    1.3f + visualRandom.nextFloat() * 2.6f));
        }
    }

    private void spawnCelebration(long now, String symbol, double multiplier) {
        int amount = PremiumReelDynamics.particleBudget(multiplier, reducedMotion, qualityTier);
        int accent = StakeSlotEngine.symbolColor(symbol);
        int[] colors = {0xFFF6C453, accent, 0xFF64DCFF, 0xFF8B65FF, 0xFFFFFFFF, 0xFFFF5D8F};
        for (int i = 0; i < amount; i++) {
            particles.add(new Particle(24 + visualRandom.nextFloat() * 312,
                    155 + visualRandom.nextFloat() * 250,
                    -68 + visualRandom.nextFloat() * 136,
                    -150 - visualRandom.nextFloat() * 180,
                    now, 1400 + visualRandom.nextInt(2200), colors[i % colors.length],
                    1.2f + visualRandom.nextFloat() * 4f));
        }
    }

    private void updateParticles(long now) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle q = particles.get(i);
            if (now - q.birth > q.life) { particles.remove(i); continue; }
            q.vy += 165f * dt;
            q.x += q.vx * dt;
            q.y += q.vy * dt;
            q.rotation += q.vx * dt * .06f;
        }
    }

    private void drawParticles(Canvas c, long now) {
        for (Particle q : particles) {
            float life = clamp((now - q.birth) / (float)q.life);
            p.setColor((q.color & 0x00FFFFFF) | (Math.round(255 * (1f - life)) << 24));
            c.save();
            c.rotate(q.rotation, q.x, q.y);
            if (((int)q.size & 1) == 0) {
                c.drawCircle(q.x, q.y, q.size, p);
            } else {
                c.drawRoundRect(new RectF(q.x - q.size, q.y - q.size * .45f,
                        q.x + q.size, q.y + q.size * .45f), q.size / 2, q.size / 2, p);
            }
            c.restore();
        }
    }

    private StakeSlotEngine.LineWin rotatingWin(long now) {
        if (phase != Phase.REVEALING || current == null || current.lineWins.isEmpty()) return null;
        return current.lineWins.get((int)(((now - phaseStart) / 760L) % current.lineWins.size()));
    }

    private StakeSlotEngine.LineWin featuredWin() {
        return current == null ? null : SymbolAnimationDirector.primaryWin(current.lineWins);
    }

    private boolean winningCell(int reel, int row, long now) {
        StakeSlotEngine.LineWin win = rotatingWin(now);
        return win != null && reel < win.count && win.rows[reel] == row;
    }

    private int featuredCount() {
        StakeSlotEngine.LineWin win = featuredWin();
        return win == null ? 0 : win.count;
    }

    private double currentMultiplier() { return current == null ? 0d : current.payoutMultiplier(); }

    private float cellX(int reel) { return REEL_LEFT + reel * (REEL_W + REEL_GAP) + REEL_W / 2f; }
    private float cellY(int row) { return REEL_TOP + row * CELL_H + CELL_H / 2f; }

    private void save() {
        prefs.edit().putInt("credits", credits).putInt("bet", betPerLine).putInt("rounds", rounds)
                .putBoolean("sound", soundEnabled).putBoolean("haptic", hapticEnabled)
                .putBoolean("reduced_motion", reducedMotion).apply();
    }

    public void onHostPause() { if (phase != Phase.IDLE) skipAnimation(); }

    public void release() {
        frameLoop = false;
        Choreographer.getInstance().removeFrameCallback(this);
        audio.release();
        haptics.release();
        particles.clear();
    }

    private void premiumPanel(Canvas c, float l, float t, float r, float b, float radius) {
        p.setShadowLayer(14, 0, 5, 0xAA000000);
        p.setShader(new LinearGradient(l, t, r, b,
                new int[]{0xFF281706, 0xFFFFDC7B, 0xFF30200B}, null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, p);
        p.clearShadowLayer(); p.setShader(null);
        panel(c, l + 2, t + 2, r - 2, b - 2, radius - 2, 0xF2070A10, 0xFF7C5B1F);
    }

    private void premiumButton(Canvas c, float l, float t, float r, float b, String label,
                               boolean active, long now) {
        float pulse = active && !reducedMotion ? .5f + .5f * (float)Math.sin(now * .005f) : 0f;
        p.setShadowLayer(7 + pulse * 5, 0, 3, active ? 0x99F6C453 : 0x66000000);
        p.setShader(new LinearGradient(l, t, r, b,
                active ? new int[]{0xFF3A2308, 0xFF8A5918, 0xFF3A2308}
                        : new int[]{0xFF262B38, 0xFF11151E, 0xFF262B38},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(l, t, r, b), (b - t) / 2f, (b - t) / 2f, p);
        p.clearShadowLayer(); p.setShader(null);
        text(c, label, (l + r) / 2f, (t + b) / 2f + 3, label.length() > 6 ? 7 : 9,
                active ? 0xFFFFE39A : 0xFFE7E9EF, true, Paint.Align.CENTER);
    }

    private void panel(Canvas c, float l, float t, float r, float b, float radius, int fill, int line) {
        p.setStyle(Paint.Style.FILL); p.setColor(fill);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.25f); p.setColor(line);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void goldText(Canvas c, String value, float x, float y, float size, Paint.Align align) {
        p.setShader(new LinearGradient(x - size * 2, y - size, x + size * 2, y,
                new int[]{0xFFC78320, 0xFFFFF0AA, 0xFFF6B83F, 0xFFFFE58B},
                null, Shader.TileMode.MIRROR));
        p.setTextSize(size); p.setTextAlign(align);
        p.setTypeface(Typeface.create("serif", Typeface.BOLD));
        c.drawText(value, x, y, p);
        p.setShader(null);
    }

    private void text(Canvas c, String value, float x, float y, float size, int color,
                      boolean bold, Paint.Align align) {
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextSize(size);
        p.setTextAlign(align); p.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(value, x, y, p);
    }

    private void crown(Canvas c, float cx, float cy, float size, int color) {
        path.reset();
        path.moveTo(cx - size * .58f, cy + size * .28f);
        path.lineTo(cx - size * .47f, cy - size * .37f);
        path.lineTo(cx - size * .14f, cy - size * .02f);
        path.lineTo(cx, cy - size * .59f);
        path.lineTo(cx + size * .15f, cy - size * .02f);
        path.lineTo(cx + size * .49f, cy - size * .38f);
        path.lineTo(cx + size * .58f, cy + size * .28f);
        path.close();
        p.setColor(color); c.drawPath(path, p);
        c.drawRoundRect(new RectF(cx - size * .6f, cy + size * .34f,
                cx + size * .6f, cy + size * .49f), 2, 2, p);
        p.setColor(0xFFFF5CA8); c.drawCircle(cx, cy + size * .1f, size * .09f, p);
        p.setColor(0xFF68DFFF); c.drawCircle(cx - size * .32f, cy + size * .12f, size * .07f, p);
        c.drawCircle(cx + size * .32f, cy + size * .12f, size * .07f, p);
    }

    private void diamond(Canvas c, float cx, float cy, float size, int color) {
        path.reset(); path.moveTo(cx, cy - size);
        path.lineTo(cx + size * .82f, cy - size * .27f);
        path.lineTo(cx + size * .55f, cy + size);
        path.lineTo(cx - size * .55f, cy + size);
        path.lineTo(cx - size * .82f, cy - size * .27f); path.close();
        p.setShader(new LinearGradient(cx - size, cy - size, cx + size, cy + size,
                new int[]{0xFFB8F7FF, color, 0xFF315EFF}, null, Shader.TileMode.CLAMP));
        c.drawPath(path, p); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f); p.setColor(0xCCFFFFFF);
        c.drawPath(path, p); p.setStyle(Paint.Style.FILL);
    }

    private void bell(Canvas c, float cx, float cy, float size, int color) {
        path.reset();
        path.moveTo(cx - size * .7f, cy + size * .45f);
        path.quadTo(cx - size * .5f, cy - size * .75f, cx, cy - size * .85f);
        path.quadTo(cx + size * .5f, cy - size * .75f, cx + size * .7f, cy + size * .45f);
        path.close();
        p.setShader(new LinearGradient(cx - size, cy - size, cx + size, cy + size,
                new int[]{0xFFFFF0A0, color, 0xFF9A5A08}, null, Shader.TileMode.CLAMP));
        c.drawPath(path, p); p.setShader(null);
        p.setColor(color); c.drawOval(new RectF(cx - size * .82f, cy + size * .32f,
                cx + size * .82f, cy + size * .62f), p);
        p.setColor(0xFF7A4308); c.drawCircle(cx, cy + size * .72f, size * .2f, p);
    }

    private void bar(Canvas c, float cx, float cy, float width, int color) {
        p.setShader(new LinearGradient(cx - width * .38f, cy, cx + width * .38f, cy,
                new int[]{0xFF120A03, 0xFFC79035, 0xFFFFE49D, 0xFFC79035, 0xFF120A03},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(cx - width * .38f, cy - 14, cx + width * .38f, cy + 14), 5, 5, p);
        p.setShader(null); text(c, "BAR", cx, cy + 6, 16, color, true, Paint.Align.CENTER);
    }

    private String multiplier(double value) {
        if (value >= 100) return String.format(Locale.US, "%.0f", value);
        if (value >= 10) return String.format(Locale.US, "%.1f", value);
        return String.format(Locale.US, "%.2f", value);
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
    private static float easeOut(float value) { float q = 1f - clamp(value); return 1f - q * q * q; }

    private static final class Particle {
        float x, y, vx, vy, rotation;
        final long birth, life;
        final int color;
        final float size;
        Particle(float x, float y, float vx, float vy, long birth, long life, int color, float size) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.birth = birth; this.life = life; this.color = color; this.size = size;
        }
    }
}