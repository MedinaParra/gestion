package cl.exequiel.royalspin;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BlurMaskFilter;
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

/** Stake-backed 5x3 slot renderer. Visuals never alter the precomputed result. */
public final class SlotView extends View implements Choreographer.FrameCallback {
    private enum Phase { IDLE, SPINNING, REVEALING }

    private static final float W = 360f, H = 800f;
    private static final float REEL_LEFT = 20f, REEL_TOP = 183f;
    private static final float REEL_W = 60f, REEL_GAP = 4f, CELL_H = 84f;
    private static final long[] STOPS = {920L, 1210L, 1520L, 1860L, 2220L};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random random = new Random();
    private final Random visualRandom = new Random(0x51A7C0DEL);
    private final StakeSlotEngine engine = new StakeSlotEngine();
    private final SharedPreferences prefs;
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(new Locale("es", "CL"));
    private final CasinoAudio audio;
    private final HapticEngine haptics;
    private final List<Particle> particles = new ArrayList<>();
    private final boolean[] stopTriggered = new boolean[5];

    private StakeSlotEngine.SpinResult current;
    private StakeSlotEngine.SpinResult pending;
    private Phase phase = Phase.IDLE;
    private long phaseStart;
    private long previousFrame;
    private long fpsWindow;
    private int fpsFrames;
    private int fps = 60;
    private int slowFrames;
    private int totalFrames;
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
    private boolean frameLoop;
    private boolean spinPressed;
    private String message = "20 líneas activas · toca GIRAR";

    public SlotView(Context context) { this(context, null); }

    public SlotView(Context context, String demoMode) {
        super(context);
        prefs = context.getSharedPreferences("royal_spin_visual", Context.MODE_PRIVATE);
        credits = prefs.getInt("credits", 2500);
        betPerLine = StakeSlotEngine.clampBet(prefs.getInt("bet", 1));
        rounds = prefs.getInt("rounds", 0);
        soundEnabled = prefs.getBoolean("sound", true);
        audio = new CasinoAudio(soundEnabled, 9);
        haptics = new HapticEngine(context, prefs.getBoolean("haptic", true));
        current = engine.spin(new Random(20260723L), betPerLine);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        if (demoMode != null && !demoMode.isEmpty()) {
            postDelayed(() -> startSpin(demoMode), 500L);
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        frameLoop = true;
        previousFrame = 0L;
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override protected void onDetachedFromWindow() {
        frameLoop = false;
        Choreographer.getInstance().removeFrameCallback(this);
        super.onDetachedFromWindow();
    }

    @Override public void doFrame(long frameTimeNanos) {
        if (!frameLoop) return;
        if (previousFrame != 0L) {
            long delta = frameTimeNanos - previousFrame;
            dt = Math.min(.05f, delta / 1_000_000_000f);
            totalFrames++;
            if (delta > 22_000_000L) slowFrames++;
        }
        previousFrame = frameTimeNanos;
        if (fpsWindow == 0L) fpsWindow = frameTimeNanos;
        fpsFrames++;
        if (frameTimeNanos - fpsWindow >= 1_000_000_000L) {
            fps = fpsFrames;
            fpsFrames = 0;
            fpsWindow = frameTimeNanos;
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
                if (!stopTriggered[reel] && elapsed >= STOPS[reel]) {
                    stopTriggered[reel] = true;
                    audio.playReelStop(reel);
                    haptics.reelStop(reel);
                    spawnImpact(reel, now);
                }
            }
            if (elapsed >= STOPS[4] + 170L) settle(now);
        } else if (phase == Phase.REVEALING) {
            long elapsed = now - phaseStart;
            shownWin = Math.round(lastWin * easeOut(clamp(elapsed / 1300f)));
            if (!rewardTriggered && elapsed >= 130L) {
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
        if (lastWin <= 0) return 1100L;
        double multiplier = current == null ? 0d : current.payoutMultiplier();
        if (multiplier >= 100d) return 5600L;
        if (multiplier >= 25d) return 5000L;
        if (multiplier >= 5d) return 4500L;
        return 3000L;
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
        message = lastWin > 0 ? "PREMIO REAL · coreografía por símbolo" : "Resultado cerrado · sin premio";
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
        drawMachine(canvas, now);
        drawParticles(canvas, now);
        drawControls(canvas, now);
        canvas.restore();
    }

    private void drawBackground(Canvas c, long now) {
        paint.setShader(new LinearGradient(0, 0, 0, H,
                new int[]{0xFF020307, 0xFF0B0918, 0xFF160919, 0xFF020306},
                new float[]{0f, .34f, .68f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, paint);
        paint.setShader(null);
        float t = now * .00012f;
        for (int i = 0; i < 54; i++) {
            float x = (i * 83.7f + (float)Math.sin(t + i) * 24f + 400f) % 360f;
            float y = (i * 137.3f + (float)Math.cos(t * .8f + i) * 35f + 900f) % 800f;
            int alpha = 20 + (i % 5) * 7;
            int rgb = i % 5 == 0 ? 0xF6C453 : i % 5 == 1 ? 0x9C62FF : 0x5FCBFF;
            paint.setColor((alpha << 24) | rgb);
            c.drawCircle(x, y, i % 7 == 0 ? 1.8f : .85f, paint);
        }
        paint.setShader(new RadialGradient(180, 300, 260,
                new int[]{0x304E2EFF, 0x15270C52, 0x00000000}, null, Shader.TileMode.CLAMP));
        c.drawCircle(180, 300, 260, paint);
        paint.setShader(null);
        float floorPulse = .5f + .5f * (float)Math.sin(now * .0015f);
        paint.setShader(new RadialGradient(180, 735, 220,
                new int[]{((25 + (int)(20 * floorPulse)) << 24) | 0x00F6C453, 0x00000000},
                null, Shader.TileMode.CLAMP));
        c.drawOval(new RectF(25, 685, 335, 805), paint);
        paint.setShader(null);
    }

    private void drawHeader(Canvas c, long now) {
        float crownPulse = 1f + .055f * (float)Math.sin(now * .003f);
        c.save();
        c.scale(crownPulse, crownPulse, 180, 27);
        crown(c, 180, 27, 20, 0xFFF6C453);
        c.restore();
        goldText(c, "ROYAL SPIN", 180, 62, 26);
        text(c, "MATH TRAZABLE · CRÉDITOS FICTICIOS", 180, 80, 7,
                0xFFAAB0C0, true, Paint.Align.CENTER);
        premiumButton(c, 12, 22, 74, 52, "RESET", false, now);
        premiumButton(c, 286, 22, 348, 52, soundEnabled ? "SFX ON" : "SFX OFF",
                soundEnabled, now);
        premiumPanel(c, 14, 91, 346, 150, 18);
        text(c, "SALDO", 31, 113, 8, 0xFF9AA2B2, true, Paint.Align.LEFT);
        text(c, numbers.format(credits) + " CR", 31, 139, 21, Color.WHITE, true, Paint.Align.LEFT);
        text(c, "RTP", 238, 113, 8, 0xFF9AA2B2, true, Paint.Align.CENTER);
        goldText(c, "95,48%", 238, 139, 16);
        text(c, "RONDA " + rounds, 330, 139, 8, 0xFF9AA2B2, true, Paint.Align.RIGHT);
    }

    private void drawMachine(Canvas c, long now) {
        float shakeX = 0f, shakeY = 0f;
        if (phase == Phase.SPINNING) {
            long e = now - phaseStart;
            for (int r = 0; r < 5; r++) {
                float d = Math.abs(e - STOPS[r]);
                if (d < 110f) {
                    float amp = (1f - d / 110f) * (1.1f + r * .18f);
                    shakeX += (float)Math.sin(e * .18f + r) * amp;
                    shakeY += (float)Math.cos(e * .23f + r) * amp * .55f;
                }
            }
        } else if (phase == Phase.REVEALING && current != null && current.payoutMultiplier() >= 25d) {
            long e = now - phaseStart;
            float decay = (float)Math.exp(-e / 850f);
            shakeX = (float)Math.sin(e * .13f) * 2.3f * decay;
            shakeY = (float)Math.cos(e * .17f) * 1.4f * decay;
        }
        c.save();
        c.translate(shakeX, shakeY);
        paint.setShadowLayer(24, 0, 10, 0xDD000000);
        paint.setShader(new LinearGradient(10, 158, 350, 492,
                new int[]{0xFF2C1A07, 0xFFFFE397, 0xFF7E4E12, 0xFFFFD36A, 0xFF2A1806},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(10, 158, 350, 492), 29, 29, paint);
        paint.clearShadowLayer();
        paint.setShader(null);
        panel(c, 15, 164, 345, 487, 24, 0xFF06080D, 0xFFFFD76A);
        panel(c, 19, 170, 341, 453, 18, 0xFF0B0D12, 0xFFB9A675);
        long elapsed = phase == Phase.SPINNING ? now - phaseStart : 99999L;
        StakeSlotEngine.SpinResult source = pending != null ? pending : current;
        for (int reel = 0; reel < 5; reel++) {
            float left = REEL_LEFT + reel * (REEL_W + REEL_GAP);
            RectF clip = new RectF(left, REEL_TOP, left + REEL_W, REEL_TOP + CELL_H * 3);
            c.save();
            path.reset();
            path.addRoundRect(clip, 8f, 8f, Path.Direction.CW);
            c.clipPath(path);
            if (phase == Phase.SPINNING && elapsed < STOPS[reel]) {
                drawSpinningReel(c, reel, left, elapsed);
            } else {
                drawStoppedReel(c, source, reel, left, now);
            }
            drawReelShade(c, clip);
            c.restore();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(reel == 2 ? 2.4f : 1.2f);
            paint.setShader(new LinearGradient(left, clip.top, left + REEL_W, clip.bottom,
                    reel == 2
                            ? new int[]{0xFFFFE58A, 0xFFF6B83F, 0xFFFFF1B5}
                            : new int[]{0xFF676E7B, 0xFFBBAF92, 0xFF575D68},
                    null, Shader.TileMode.MIRROR));
            c.drawRoundRect(clip, 8, 8, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
        }
        drawWinLine(c, now);
        text(c, "20 LÍNEAS · ♛ WILD · RESULTADO PRECALCULADO", 180, 473,
                7, 0xFFB5B9C4, true, Paint.Align.CENTER);
        c.restore();
    }

    private void drawSpinningReel(Canvas c, int reel, float left, long elapsed) {
        float q = clamp(elapsed / (float)STOPS[reel]);
        float velocity = .34f + 1.95f * (float)Math.sin(Math.PI * q);
        float travel = (elapsed * velocity + reel * 31f) % CELL_H;
        int base = (int)(elapsed / Math.max(24f, 62f - velocity * 14f)) + reel * 7;
        for (int item = -2; item <= 4; item++) {
            int index = Math.floorMod(base + item, StakeSlotEngine.SYMBOLS.length);
            float top = REEL_TOP + item * CELL_H + travel;
            drawSymbol(c, left + 2, top + 2, REEL_W - 4, CELL_H - 4,
                    StakeSlotEngine.SYMBOLS[index], false, elapsed);
        }
        paint.setShader(new LinearGradient(left, REEL_TOP, left + REEL_W, REEL_TOP,
                new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        for (int i = 0; i < 9; i++) {
            float y = REEL_TOP + ((elapsed * (1.5f + i * .11f) + i * 37f) % (CELL_H * 3));
            c.drawRoundRect(new RectF(left + 4, y, left + REEL_W - 4, y + 2.1f), 2, 2, paint);
        }
        paint.setShader(null);
    }

    private void drawStoppedReel(Canvas c, StakeSlotEngine.SpinResult result, int reel,
                                 float left, long now) {
        if (result == null) return;
        float bounce = 0f;
        float d = now - phaseStart - STOPS[reel];
        if ((phase == Phase.SPINNING || phase == Phase.REVEALING) && d >= 0 && d < 560) {
            bounce = (float)(Math.sin(d * .034) * Math.exp(-d / 160f) * 10.5f);
        }
        for (int row = 0; row < 3; row++) {
            drawSymbol(c, left + 2, REEL_TOP + row * CELL_H + 2 + bounce,
                    REEL_W - 4, CELL_H - 4, result.board[reel][row],
                    winningCell(reel, row, now), now);
        }
    }

    private void drawSymbol(Canvas c, float left, float top, float width, float height,
                            String symbol, boolean winning, long now) {
        int color = StakeSlotEngine.symbolColor(symbol);
        SymbolAnimationDirector.Frame frame = winning
                ? SymbolAnimationDirector.frame(symbol, featuredCount(), currentMultiplier(),
                Math.max(0L, now - phaseStart))
                : SymbolAnimationDirector.frame("", 0, 0d, 0L);
        float cx = left + width / 2f;
        float cy = top + height / 2f;
        c.save();
        if (winning) {
            c.translate(0, frame.lift);
            c.rotate(frame.rotation, cx, cy);
            c.scale(frame.scale, frame.scale, cx, cy);
        }
        if (winning) {
            int alpha = Math.min(105, Math.round(55f + 40f * frame.energy));
            paint.setShadowLayer(15 + frame.intensity * 3f, 0, 0, color);
            paint.setColor((alpha << 24) | (color & 0x00FFFFFF));
            c.drawRoundRect(new RectF(left - 3, top - 3, left + width + 3, top + height + 3),
                    11, 11, paint);
            paint.clearShadowLayer();
        }
        paint.setShader(new LinearGradient(left, top, left, top + height,
                new int[]{0xFF4B4740, 0xFF15161A, 0xFF090A0D, 0xFF39332A},
                new float[]{0f, .18f, .68f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(left, top, left + width, top + height), 8, 8, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(0x66FFF1C0);
        c.drawRoundRect(new RectF(left + 1, top + 1, left + width - 1, top + height - 1),
                7, 7, paint);
        paint.setStyle(Paint.Style.FILL);
        float glint = (now * .09f + left * 2f + top) % (height + 40f) - 20f;
        paint.setShader(new LinearGradient(left, top + glint - 8, left + width, top + glint + 8,
                new int[]{0x00FFFFFF, 0x44FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(left + 2, top + glint - 5, left + width - 2, top + glint + 5),
                5, 5, paint);
        paint.setShader(null);
        paint.setShadowLayer(winning ? 12 : 5, 0, 2, winning ? color : 0x99000000);
        if (StakeSlotEngine.WILD.equals(symbol)) {
            crown(c, cx, cy - 5, 24, color);
            text(c, "WILD", cx, top + height - 11, 8, 0xFFFFE7A0, true, Paint.Align.CENTER);
        } else if (StakeSlotEngine.DIAMOND.equals(symbol)) {
            diamond(c, cx, cy, 21, color);
        } else if (StakeSlotEngine.BELL.equals(symbol)) {
            bell(c, cx, cy, 22, color);
        } else if (StakeSlotEngine.BAR.equals(symbol)) {
            drawBarSymbol(c, cx, cy, width, color);
        } else {
            String label = StakeSlotEngine.displayLabel(symbol);
            float size = label.length() >= 4 ? 15 : label.length() >= 2 ? 23 : 34;
            text(c, label, cx, cy + size * .34f, size, color, true, Paint.Align.CENTER);
        }
        paint.clearShadowLayer();
        c.restore();
    }

    private void drawBarSymbol(Canvas c, float cx, float cy, float width, int color) {
        paint.setShader(new LinearGradient(cx - width * .35f, cy, cx + width * .35f, cy,
                new int[]{0xFF1C1207, 0xFFC79035, 0xFFFFE39A, 0xFFC79035, 0xFF1C1207},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(cx - width * .36f, cy - 13, cx + width * .36f, cy + 13),
                5, 5, paint);
        paint.setShader(null);
        text(c, "BAR", cx, cy + 6, 16, color, true, Paint.Align.CENTER);
    }

    private void drawReelShade(Canvas c, RectF clip) {
        paint.setShader(new LinearGradient(0, clip.top, 0, clip.bottom,
                new int[]{0xCC000000, 0x00000000, 0x00000000, 0xCC000000},
                new float[]{0f, .18f, .82f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(clip, paint);
        paint.setShader(null);
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
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(9);
        paint.setColor((70 << 24) | (accent & 0x00FFFFFF));
        paint.setMaskFilter(new BlurMaskFilter(11, BlurMaskFilter.Blur.NORMAL));
        c.drawPath(path, paint);
        paint.setMaskFilter(null);
        paint.setStrokeWidth(4f);
        paint.setColor(0xFFFFE8A0);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        panel(c, 88, 407, 272, 439, 16, 0xF21A1004, accent);
        text(c, win.symbol + " · LÍNEA " + (win.lineIndex + 1) + " · +"
                        + numbers.format(win.payout) + " CR",
                180, 428, 9, 0xFFFFEDB2, true, Paint.Align.CENTER);
    }

    private StakeSlotEngine.LineWin rotatingWin(long now) {
        if (phase != Phase.REVEALING || current == null || current.lineWins.isEmpty()) return null;
        return current.lineWins.get((int)(((now - phaseStart) / 720L) % current.lineWins.size()));
    }

    private StakeSlotEngine.LineWin featuredWin() {
        return current == null ? null : SymbolAnimationDirector.primaryWin(current.lineWins);
    }

    private int featuredCount() {
        StakeSlotEngine.LineWin win = featuredWin();
        return win == null ? 0 : win.count;
    }

    private double currentMultiplier() {
        return current == null ? 0d : current.payoutMultiplier();
    }

    private boolean winningCell(int reel, int row, long now) {
        StakeSlotEngine.LineWin win = rotatingWin(now);
        return win != null && reel < win.count && win.rows[reel] == row;
    }

    private void drawControls(Canvas c, long now) {
        premiumPanel(c, 15, 505, 345, 671, 22);
        text(c, "APUESTA POR LÍNEA", 180, 530, 9, 0xFFADB3C0, true, Paint.Align.CENTER);
        premiumButton(c, 26, 543, 82, 596, "−", false, now);
        panel(c, 101, 543, 259, 596, 17, 0xFF090B10, 0xFFC58B29);
        goldText(c, betPerLine + " CR", 180, 578, 22);
        premiumButton(c, 278, 543, 334, 596, "+", false, now);
        int totalBet = betPerLine * StakeSlotEngine.LINE_COUNT;
        text(c, "APUESTA TOTAL", 35, 621, 8, 0xFF9AA2B2, true, Paint.Align.LEFT);
        text(c, numbers.format(totalBet) + " CR", 35, 646, 16, Color.WHITE, true, Paint.Align.LEFT);
        text(c, "ÚLTIMO PREMIO", 325, 621, 8, 0xFF9AA2B2, true, Paint.Align.RIGHT);
        int display = phase == Phase.REVEALING ? shownWin : lastWin;
        goldText(c, numbers.format(display) + " CR", 325, 646, 16, Paint.Align.RIGHT);
        text(c, message, 180, 696, 10, 0xFFE2E4EA, true, Paint.Align.CENTER);
        drawSpinButton(c, now);
        float slow = totalFrames == 0 ? 0 : slowFrames * 100f / totalFrames;
        text(c, "FPS " + fps + " · lentos " + String.format(Locale.US, "%.1f", slow)
                        + "% · créditos ficticios",
                180, 790, 7, 0xFF7B8290, false, Paint.Align.CENTER);
    }

    private void drawSpinButton(Canvas c, long now) {
        float pulse = phase == Phase.IDLE ? .5f + .5f * (float)Math.sin(now * .0045f) : 0f;
        float press = spinPressed ? .96f : 1f;
        c.save();
        c.scale(press, press, 180, 739);
        paint.setShadowLayer(12 + pulse * 9, 0, 5, phase == Phase.IDLE ? 0xAAF6C453 : 0xAA7045C0);
        paint.setShader(new LinearGradient(34, 711, 326, 766,
                phase == Phase.IDLE
                        ? new int[]{0xFFFFF0A0, 0xFFF6B83F, 0xFFFFD96C}
                        : new int[]{0xFF8B65D8, 0xFF58348F, 0xFF8B65D8},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(34, 711, 326, 766), 28, 28, paint);
        paint.clearShadowLayer();
        paint.setShader(null);
        if (phase == Phase.IDLE) {
            float sweep = 30 + (now * .12f % 330f);
            paint.setShader(new LinearGradient(sweep - 45, 711, sweep + 45, 766,
                    new int[]{0x00FFFFFF, 0x88FFFFFF, 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(36, 713, 324, 764), 26, 26, paint);
            paint.setShader(null);
        }
        text(c, phase == Phase.IDLE ? "GIRAR" : "OMITIR ANIMACIÓN",
                180, 746, phase == Phase.IDLE ? 18 : 14,
                phase == Phase.IDLE ? 0xFF1B1003 : Color.WHITE, true, Paint.Align.CENTER);
        c.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = (event.getX() - offsetX) / scale;
        float y = (event.getY() - offsetY) / scale;
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
        if (x > 280 && y < 65) {
            soundEnabled = !soundEnabled;
            audio.setEnabled(soundEnabled);
            save();
            return true;
        }
        if (x < 82 && y < 65 && phase == Phase.IDLE) {
            credits = 2500;
            lastWin = shownWin = rounds = 0;
            message = "Saldo demo reiniciado";
            audio.playTap();
            haptics.tap();
            save();
            return true;
        }
        if (phase != Phase.IDLE) {
            if (y > 695) skipAnimation();
            return true;
        }
        if (y > 530 && y < 610 && x < 100) {
            betPerLine = StakeSlotEngine.clampBet(betPerLine - 1);
            audio.playTap();
            haptics.tap();
            save();
        } else if (y > 530 && y < 610 && x > 260) {
            betPerLine = StakeSlotEngine.clampBet(betPerLine + 1);
            audio.playTap();
            haptics.tap();
            save();
        } else if (y > 690) {
            startSpin((String)null);
        }
        return true;
    }

    private void startSpin(String forcedSymbol) {
        if (phase != Phase.IDLE) return;
        int totalBet = betPerLine * StakeSlotEngine.LINE_COUNT;
        boolean demo = forcedSymbol != null;
        if (!demo && credits < totalBet) {
            message = "Saldo insuficiente · pulsa RESET";
            audio.playError();
            haptics.error();
            return;
        }
        if (!demo) credits -= totalBet;
        pending = demo ? createShowcase(forcedSymbol) : engine.spin(random, betPerLine);
        phase = Phase.SPINNING;
        phaseStart = SystemClock.uptimeMillis();
        lastWin = shownWin = 0;
        payoutApplied = rewardTriggered = false;
        particles.clear();
        for (int i = 0; i < 5; i++) stopTriggered[i] = false;
        rounds++;
        message = "Resultado calculado · coreografía premium";
        audio.playSpinStart();
        haptics.tap();
        save();
    }

    private StakeSlotEngine.SpinResult createShowcase(String requested) {
        String symbol = requested == null ? StakeSlotEngine.WILD : requested.trim().toUpperCase(Locale.US);
        if ("BIGWIN".equals(symbol) || "WILD".equals(symbol) || "CROWN".equals(symbol)) {
            symbol = StakeSlotEngine.WILD;
        } else if ("BELL".equals(symbol) || "CAMPANA".equals(symbol)) {
            symbol = StakeSlotEngine.BELL;
        } else if ("BAR".equals(symbol)) {
            symbol = StakeSlotEngine.BAR;
        } else if ("SEVEN".equals(symbol) || "7".equals(symbol)) {
            symbol = StakeSlotEngine.SEVEN;
        } else if ("DIAMOND".equals(symbol) || "GEMA".equals(symbol)) {
            symbol = StakeSlotEngine.DIAMOND;
        } else if ("QUEEN".equals(symbol) || "Q".equals(symbol)) {
            symbol = StakeSlotEngine.QUEEN;
        } else {
            symbol = StakeSlotEngine.WILD;
        }
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

    CelebrationState getCelebrationState(long now) {
        if (phase != Phase.REVEALING || current == null || lastWin <= 0) return null;
        StakeSlotEngine.LineWin featured = featuredWin();
        if (featured == null) return null;
        return new CelebrationState(true, featured.symbol, featured.count, featured.rows.clone(),
                current.payoutMultiplier(), Math.max(0L, now - phaseStart),
                featured.lineIndex, featured.payout);
    }

    private void spawnImpact(int reel, long now) {
        float cx = REEL_LEFT + reel * (REEL_W + REEL_GAP) + REEL_W / 2;
        for (int i = 0; i < 15; i++) {
            float angle = (float)(visualRandom.nextDouble() * Math.PI * 2);
            float speed = 30 + visualRandom.nextFloat() * 78;
            particles.add(new Particle(cx, REEL_TOP + CELL_H * 3 + 2,
                    (float)Math.cos(angle) * speed, -Math.abs((float)Math.sin(angle)) * speed,
                    now, 540 + visualRandom.nextInt(440), 0xFFF6C453,
                    1.3f + visualRandom.nextFloat() * 2.5f));
        }
    }

    private void spawnCelebration(long now, String symbol, double multiplier) {
        int amount = multiplier >= 100 ? 180 : multiplier >= 25 ? 140 : multiplier >= 5 ? 90 : 48;
        int accent = StakeSlotEngine.symbolColor(symbol);
        int[] colors = {0xFFF6C453, accent, 0xFF64DCFF, 0xFF8B65FF, 0xFFFFFFFF};
        for (int i = 0; i < amount; i++) {
            particles.add(new Particle(28 + visualRandom.nextFloat() * 304,
                    165 + visualRandom.nextFloat() * 240,
                    -62 + visualRandom.nextFloat() * 124,
                    -145 - visualRandom.nextFloat() * 165,
                    now, 1400 + visualRandom.nextInt(2000), colors[i % colors.length],
                    1.3f + visualRandom.nextFloat() * 3.8f));
        }
    }

    private void updateParticles(long now) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle q = particles.get(i);
            if (now - q.birth > q.life) {
                particles.remove(i);
                continue;
            }
            q.vy += 165f * dt;
            q.x += q.vx * dt;
            q.y += q.vy * dt;
            q.rotation += q.vx * dt * .06f;
        }
    }

    private void drawParticles(Canvas c, long now) {
        for (Particle q : particles) {
            float life = clamp((now - q.birth) / (float)q.life);
            paint.setColor((q.color & 0x00FFFFFF) | ((int)(255 * (1f - life)) << 24));
            c.save();
            c.rotate(q.rotation, q.x, q.y);
            c.drawRoundRect(new RectF(q.x - q.size, q.y - q.size * .45f,
                    q.x + q.size, q.y + q.size * .45f), q.size / 2, q.size / 2, paint);
            c.restore();
        }
    }

    private void save() {
        prefs.edit().putInt("credits", credits).putInt("bet", betPerLine)
                .putInt("rounds", rounds).putBoolean("sound", soundEnabled).apply();
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
        paint.setShadowLayer(14, 0, 5, 0xAA000000);
        paint.setShader(new LinearGradient(l, t, r, b,
                new int[]{0xFF2A1907, 0xFFFFD978, 0xFF30200B}, null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
        paint.clearShadowLayer();
        paint.setShader(null);
        panel(c, l + 2, t + 2, r - 2, b - 2, radius - 2, 0xF2080B11, 0xFF7C5B1F);
    }

    private void premiumButton(Canvas c, float l, float t, float r, float b, String label,
                               boolean active, long now) {
        float pulse = active ? .5f + .5f * (float)Math.sin(now * .005f) : 0f;
        paint.setShadowLayer(7 + pulse * 5, 0, 3, active ? 0x99F6C453 : 0x66000000);
        paint.setShader(new LinearGradient(l, t, r, b,
                active
                        ? new int[]{0xFF3A2308, 0xFF8A5918, 0xFF3A2308}
                        : new int[]{0xFF262B38, 0xFF11151E, 0xFF262B38},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(l, t, r, b), (b - t) / 2f, (b - t) / 2f, paint);
        paint.clearShadowLayer();
        paint.setShader(null);
        text(c, label, (l + r) / 2f, (t + b) / 2f + 2.8f, 8,
                active ? 0xFFFFDE7A : 0xFFE6E8EE, true, Paint.Align.CENTER);
    }

    private void panel(Canvas c, float l, float t, float r, float b,
                       float radius, int fill, int line) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(fill);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.25f); paint.setColor(line);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
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
        paint.setShader(new LinearGradient(cx - size, cy - size, cx + size, cy + size,
                new int[]{0xFFFFF1A4, color, 0xFFFFD15A}, null, Shader.TileMode.MIRROR));
        c.drawPath(path, paint);
        c.drawRoundRect(new RectF(cx - size * .6f, cy + size * .34f,
                cx + size * .6f, cy + size * .49f), 2, 2, paint);
        paint.setShader(null);
    }

    private void diamond(Canvas c, float cx, float cy, float size, int color) {
        path.reset();
        path.moveTo(cx, cy - size);
        path.lineTo(cx + size * .82f, cy - size * .27f);
        path.lineTo(cx + size * .55f, cy + size);
        path.lineTo(cx - size * .55f, cy + size);
        path.lineTo(cx - size * .82f, cy - size * .27f);
        path.close();
        paint.setShader(new LinearGradient(cx - size, cy - size, cx + size, cy + size,
                new int[]{0xFFFFFFFF, color, 0xFF1777C8, 0xFF7FF1FF},
                null, Shader.TileMode.MIRROR));
        c.drawPath(path, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.2f); paint.setColor(0xCCFFFFFF);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void bell(Canvas c, float cx, float cy, float size, int color) {
        path.reset();
        path.moveTo(cx - size * .7f, cy + size * .45f);
        path.quadTo(cx - size * .5f, cy - size * .75f, cx, cy - size * .85f);
        path.quadTo(cx + size * .5f, cy - size * .75f, cx + size * .7f, cy + size * .45f);
        path.close();
        paint.setShader(new LinearGradient(cx - size, cy - size, cx + size, cy + size,
                new int[]{0xFFFFF2A7, color, 0xFFB66A0D, 0xFFFFD569},
                null, Shader.TileMode.MIRROR));
        c.drawPath(path, paint);
        c.drawOval(new RectF(cx - size * .82f, cy + size * .32f,
                cx + size * .82f, cy + size * .62f), paint);
        paint.setShader(null);
        paint.setColor(0xFF8B5714);
        c.drawCircle(cx, cy + size * .72f, size * .2f, paint);
    }

    private void goldText(Canvas c, String value, float x, float y, float size) {
        goldText(c, value, x, y, size, Paint.Align.CENTER);
    }

    private void goldText(Canvas c, String value, float x, float y, float size, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setTypeface(Typeface.create("serif", Typeface.BOLD));
        paint.setShader(new LinearGradient(x - 80, y - size, x + 80, y + 4,
                new int[]{0xFFFFF0A0, 0xFFF0A82D, 0xFFFFDF78},
                null, Shader.TileMode.MIRROR));
        c.drawText(value, x, y, paint);
        paint.setShader(null);
    }

    private void text(Canvas c, String value, float x, float y, float size, int color,
                      boolean bold, Paint.Align align) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(value, x, y, paint);
    }

    private String multiplier(double value) {
        if (value >= 100) return String.format(Locale.US, "%.0f", value);
        if (value >= 10) return String.format(Locale.US, "%.1f", value);
        return String.format(Locale.US, "%.2f", value);
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
    private static float easeOut(float value) { float q = 1f - value; return 1f - q * q * q; }

    static final class CelebrationState {
        final boolean active;
        final String symbol;
        final int count;
        final int[] rows;
        final double multiplier;
        final long elapsedMs;
        final int lineIndex;
        final int payout;

        CelebrationState(boolean active, String symbol, int count, int[] rows,
                         double multiplier, long elapsedMs, int lineIndex, int payout) {
            this.active = active;
            this.symbol = symbol;
            this.count = count;
            this.rows = rows;
            this.multiplier = multiplier;
            this.elapsedMs = elapsedMs;
            this.lineIndex = lineIndex;
            this.payout = payout;
        }
    }

    private static final class Particle {
        float x, y, vx, vy, rotation;
        final long birth, life;
        final int color;
        final float size;

        Particle(float x, float y, float vx, float vy, long birth, long life,
                 int color, float size) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.birth = birth; this.life = life;
            this.color = color; this.size = size;
        }
    }
}
