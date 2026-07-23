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
    private String message = "20 líneas activas · toca GIRAR";

    public SlotView(Context context) { this(context, null); }

    public SlotView(Context context, String demoMode) {
        super(context);
        prefs = context.getSharedPreferences("royal_spin_visual", Context.MODE_PRIVATE);
        credits = prefs.getInt("credits", 2500);
        betPerLine = StakeSlotEngine.clampBet(prefs.getInt("bet", 1));
        rounds = prefs.getInt("rounds", 0);
        soundEnabled = prefs.getBoolean("sound", true);
        audio = new CasinoAudio(soundEnabled, 8);
        haptics = new HapticEngine(context, prefs.getBoolean("haptic", true));
        current = engine.spin(new Random(20260723L), betPerLine);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        if ("bigwin".equals(demoMode)) postDelayed(() -> startSpin(true), 500L);
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
            shownWin = Math.round(lastWin * easeOut(clamp(elapsed / 1150f)));
            if (!rewardTriggered && elapsed >= 130L) {
                rewardTriggered = true;
                if (lastWin > 0) {
                    audio.playWin(current.payoutMultiplier());
                    haptics.win(current.payoutMultiplier());
                    spawnCelebration(now, current.payoutMultiplier());
                } else audio.playLose();
            }
            if (elapsed >= (lastWin > 0 ? 4300L : 1100L)) finishRound();
        }
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
        message = lastWin > 0 ? "PREMIO REAL · revelando líneas" : "Resultado cerrado · sin premio";
    }

    private void finishRound() {
        shownWin = lastWin;
        phase = Phase.IDLE;
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
        drawHeader(canvas);
        drawMachine(canvas, now);
        drawParticles(canvas, now);
        drawControls(canvas);
        canvas.restore();
    }

    private void drawBackground(Canvas c, long now) {
        paint.setShader(new LinearGradient(0, 0, 0, H,
                new int[]{0xFF03040C, 0xFF10102A, 0xFF160A28, 0xFF03050B},
                new float[]{0f, .34f, .68f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, paint);
        paint.setShader(null);
        float t = now * .00012f;
        for (int i = 0; i < 44; i++) {
            float x = (i * 83.7f + (float)Math.sin(t + i) * 24f + 400f) % 360f;
            float y = (i * 137.3f + (float)Math.cos(t * .8f + i) * 35f + 900f) % 800f;
            int alpha = 24 + (i % 5) * 7;
            paint.setColor((alpha << 24) | (i % 4 == 0 ? 0xF6C453 : 0x7358FF));
            c.drawCircle(x, y, i % 6 == 0 ? 1.8f : .9f, paint);
        }
        paint.setShader(new RadialGradient(180, 285, 250,
                new int[]{0x285B35FF, 0x102C0F6A, 0x00000000}, null, Shader.TileMode.CLAMP));
        c.drawCircle(180, 285, 250, paint);
        paint.setShader(null);
    }

    private void drawHeader(Canvas c) {
        crown(c, 180, 27, 20, 0xFFF6C453);
        text(c, "ROYAL SPIN", 180, 62, 26, 0xFFF6C453, true, Paint.Align.CENTER);
        text(c, "STAKE MATH · DEMO SIN DINERO REAL", 180, 80, 7, 0xFFAAB0C0, true, Paint.Align.CENTER);
        button(c, 12, 22, 74, 52, "RESET", 0xC8242935, 0xFFE6E8EE, 8);
        button(c, 286, 22, 348, 52, soundEnabled ? "SFX ON" : "SFX OFF",
                soundEnabled ? 0xFF513714 : 0xFF242936,
                soundEnabled ? 0xFFF6C453 : 0xFF8E95A4, 8);
        panel(c, 14, 91, 346, 150, 17, 0xE9090D17, 0xFF7F5C1C);
        text(c, "SALDO", 31, 113, 8, 0xFF8F97A8, true, Paint.Align.LEFT);
        text(c, numbers.format(credits) + " CR", 31, 139, 21, Color.WHITE, true, Paint.Align.LEFT);
        text(c, "RTP", 238, 113, 8, 0xFF8F97A8, true, Paint.Align.CENTER);
        text(c, "95,48%", 238, 139, 16, 0xFFF6C453, true, Paint.Align.CENTER);
        text(c, "RONDA " + rounds, 330, 139, 8, 0xFF8F97A8, true, Paint.Align.RIGHT);
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
        }
        c.save();
        c.translate(shakeX, shakeY);
        paint.setShadowLayer(22, 0, 9, 0xCC000000);
        paint.setShader(new LinearGradient(12, 160, 348, 490,
                new int[]{0xFF5A3B12, 0xFFFFDC79, 0xFF50320C, 0xFFC98A24},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(11, 160, 349, 490), 27, 27, paint);
        paint.clearShadowLayer();
        paint.setShader(null);
        panel(c, 16, 165, 344, 485, 23, 0xFF090D17, 0xFFFFD76B);
        panel(c, 20, 171, 340, 451, 17, 0xFFEEE7D6, 0xFF5A6070);

        long elapsed = phase == Phase.SPINNING ? now - phaseStart : 99999L;
        StakeSlotEngine.SpinResult source = pending != null ? pending : current;
        for (int reel = 0; reel < 5; reel++) {
            float left = REEL_LEFT + reel * (REEL_W + REEL_GAP);
            RectF clip = new RectF(left, REEL_TOP, left + REEL_W, REEL_TOP + CELL_H * 3);
            c.save();
            path.reset();
            path.addRoundRect(clip, 7f, 7f, Path.Direction.CW);
            c.clipPath(path);
            if (phase == Phase.SPINNING && elapsed < STOPS[reel]) drawSpinningReel(c, reel, left, elapsed);
            else drawStoppedReel(c, source, reel, left, now);
            drawReelShade(c, clip);
            c.restore();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(reel == 2 ? 2.3f : 1.15f);
            paint.setColor(reel == 2 ? 0xFFF6C453 : 0xFF656C7A);
            c.drawRoundRect(clip, 7, 7, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        drawWinLine(c, now);
        text(c, "20 LÍNEAS · ♛ WILD · RESULTADO PRECALCULADO", 180, 472,
                7, 0xFFA7ADBA, true, Paint.Align.CENTER);
        c.restore();
    }

    private void drawSpinningReel(Canvas c, int reel, float left, long elapsed) {
        float q = clamp(elapsed / (float)STOPS[reel]);
        float velocity = .38f + 1.75f * (float)Math.sin(Math.PI * q);
        float travel = (elapsed * velocity + reel * 31f) % CELL_H;
        int base = (int)(elapsed / Math.max(28f, 62f - velocity * 13f)) + reel * 7;
        for (int item = -2; item <= 4; item++) {
            int index = Math.floorMod(base + item, StakeSlotEngine.SYMBOLS.length);
            float top = REEL_TOP + item * CELL_H + travel;
            drawSymbol(c, left + 2, top + 2, REEL_W - 4, CELL_H - 4,
                    StakeSlotEngine.SYMBOLS[index], false, elapsed);
        }
        paint.setShader(new LinearGradient(left, REEL_TOP, left + REEL_W, REEL_TOP,
                new int[]{0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        for (int i = 0; i < 7; i++) {
            float y = REEL_TOP + ((elapsed * (1.4f + i * .11f) + i * 43f) % (CELL_H * 3));
            c.drawRoundRect(new RectF(left + 5, y, left + REEL_W - 5, y + 2.4f), 2, 2, paint);
        }
        paint.setShader(null);
    }

    private void drawStoppedReel(Canvas c, StakeSlotEngine.SpinResult result, int reel, float left, long now) {
        float bounce = 0f;
        float d = now - phaseStart - STOPS[reel];
        if ((phase == Phase.SPINNING || phase == Phase.REVEALING) && d >= 0 && d < 520) {
            bounce = (float)(Math.sin(d * .035) * Math.exp(-d / 155f) * 9.5f);
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
        float pulse = winning ? 1f + .055f * (float)Math.sin(now * .012f) : 1f;
        c.save();
        c.scale(pulse, pulse, left + width / 2, top + height / 2);
        if (winning) {
            paint.setShadowLayer(14, 0, 0, color);
            paint.setColor((color & 0x00FFFFFF) | 0x66000000);
            c.drawRoundRect(new RectF(left - 2, top - 2, left + width + 2, top + height + 2), 10, 10, paint);
            paint.clearShadowLayer();
        }
        paint.setShader(new LinearGradient(left, top, left, top + height,
                new int[]{0xFFFFFFFF, 0xFFF4EDDC, 0xFFD8D0C0}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(left, top, left + width, top + height), 7, 7, paint);
        paint.setShader(null);
        if (StakeSlotEngine.WILD.equals(symbol)) {
            crown(c, left + width / 2, top + height / 2 - 4, 25, color);
            text(c, "WILD", left + width / 2, top + height - 12, 8, 0xFF5E3D0D, true, Paint.Align.CENTER);
        } else if (StakeSlotEngine.DIAMOND.equals(symbol)) {
            diamond(c, left + width / 2, top + height / 2, 21, color);
        } else if (StakeSlotEngine.BELL.equals(symbol)) {
            bell(c, left + width / 2, top + height / 2, 22, color);
        } else {
            String label = StakeSlotEngine.displayLabel(symbol);
            float size = label.length() >= 4 ? 15 : label.length() >= 2 ? 23 : 34;
            text(c, label, left + width / 2, top + height / 2 + size * .34f,
                    size, color, true, Paint.Align.CENTER);
        }
        c.restore();
    }

    private void drawReelShade(Canvas c, RectF clip) {
        paint.setShader(new LinearGradient(0, clip.top, 0, clip.bottom,
                new int[]{0xAA000000, 0x00000000, 0x00000000, 0xAA000000},
                new float[]{0f, .18f, .82f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(clip, paint);
        paint.setShader(null);
    }

    private void drawWinLine(Canvas c, long now) {
        if (phase != Phase.REVEALING || current.lineWins.isEmpty()) return;
        StakeSlotEngine.LineWin win = current.lineWins.get(
                (int)(((now - phaseStart) / 620L) % current.lineWins.size()));
        path.reset();
        for (int reel = 0; reel < 5; reel++) {
            float x = REEL_LEFT + reel * (REEL_W + REEL_GAP) + REEL_W / 2;
            float y = REEL_TOP + win.rows[reel] * CELL_H + CELL_H / 2;
            if (reel == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8);
        paint.setColor(0x44F6C453);
        paint.setMaskFilter(new BlurMaskFilter(10, BlurMaskFilter.Blur.NORMAL));
        c.drawPath(path, paint);
        paint.setMaskFilter(null);
        paint.setStrokeWidth(3.8f);
        paint.setColor(0xFFFFE287);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        panel(c, 91, 408, 269, 438, 15, 0xED160F05, 0xFFF6C453);
        text(c, "LÍNEA " + (win.lineIndex + 1) + "  +" + numbers.format(win.payout) + " CR",
                180, 428, 10, 0xFFFFE8A0, true, Paint.Align.CENTER);
    }

    private boolean winningCell(int reel, int row, long now) {
        if (phase != Phase.REVEALING || current.lineWins.isEmpty()) return false;
        StakeSlotEngine.LineWin win = current.lineWins.get(
                (int)(((now - phaseStart) / 620L) % current.lineWins.size()));
        return reel < win.count && win.rows[reel] == row;
    }

    private void drawControls(Canvas c) {
        panel(c, 15, 505, 345, 671, 21, 0xEA090D17, 0xFF343B4D);
        text(c, "APUESTA POR LÍNEA", 180, 530, 9, 0xFF9AA2B3, true, Paint.Align.CENTER);
        button(c, 26, 543, 82, 596, "−", 0xFF242A38, Color.WHITE, 24);
        panel(c, 101, 543, 259, 596, 16, 0xFF111725, 0xFF85611D);
        text(c, betPerLine + " CR", 180, 578, 22, 0xFFF6C453, true, Paint.Align.CENTER);
        button(c, 278, 543, 334, 596, "+", 0xFF242A38, Color.WHITE, 24);
        int totalBet = betPerLine * StakeSlotEngine.LINE_COUNT;
        text(c, "APUESTA TOTAL", 35, 621, 8, 0xFF8F97A8, true, Paint.Align.LEFT);
        text(c, numbers.format(totalBet) + " CR", 35, 646, 16, Color.WHITE, true, Paint.Align.LEFT);
        text(c, "ÚLTIMO PREMIO", 325, 621, 8, 0xFF8F97A8, true, Paint.Align.RIGHT);
        int display = phase == Phase.REVEALING ? shownWin : lastWin;
        text(c, numbers.format(display) + " CR", 325, 646, 16,
                display > 0 ? 0xFFF6C453 : Color.WHITE, true, Paint.Align.RIGHT);
        text(c, message, 180, 696, 10, 0xFFD9DCE4, true, Paint.Align.CENTER);
        button(c, 34, 711, 326, 766, phase == Phase.IDLE ? "GIRAR" : "OMITIR ANIMACIÓN",
                phase == Phase.IDLE ? 0xFFF6C453 : 0xFF5B3E91,
                phase == Phase.IDLE ? 0xFF171006 : Color.WHITE, 16);
        float slow = totalFrames == 0 ? 0 : slowFrames * 100f / totalFrames;
        text(c, "FPS " + fps + " · lentos " + String.format(Locale.US, "%.1f", slow)
                        + "% · créditos ficticios",
                180, 790, 7, 0xFF727A8C, false, Paint.Align.CENTER);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        float x = (event.getX() - offsetX) / scale;
        float y = (event.getY() - offsetY) / scale;
        if (x > 280 && y < 65) {
            soundEnabled = !soundEnabled;
            audio.setEnabled(soundEnabled);
            save();
            return true;
        }
        if (x < 82 && y < 65 && phase == Phase.IDLE) {
            credits = 2500; lastWin = shownWin = rounds = 0;
            message = "Saldo demo reiniciado";
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
        } else if (y > 690) startSpin(false);
        return true;
    }

    private void startSpin(boolean forcedBigWin) {
        if (phase != Phase.IDLE) return;
        int totalBet = betPerLine * StakeSlotEngine.LINE_COUNT;
        if (!forcedBigWin && credits < totalBet) {
            message = "Saldo insuficiente · pulsa RESET";
            audio.playError(); haptics.error(); return;
        }
        if (!forcedBigWin) credits -= totalBet;
        pending = forcedBigWin ? createBigWin() : engine.spin(random, betPerLine);
        phase = Phase.SPINNING;
        phaseStart = SystemClock.uptimeMillis();
        lastWin = shownWin = 0;
        payoutApplied = rewardTriggered = false;
        particles.clear();
        for (int i = 0; i < 5; i++) stopTriggered[i] = false;
        rounds++;
        message = "Resultado calculado · coreografía en ejecución";
        audio.playSpinStart(); haptics.tap(); save();
    }

    private StakeSlotEngine.SpinResult createBigWin() {
        String[][] board = new String[5][3];
        String[] middle = {StakeSlotEngine.ACE, StakeSlotEngine.KING, StakeSlotEngine.QUEEN,
                StakeSlotEngine.JACK, StakeSlotEngine.BELL};
        String[] bottom = {StakeSlotEngine.JACK, StakeSlotEngine.QUEEN, StakeSlotEngine.KING,
                StakeSlotEngine.ACE, StakeSlotEngine.BAR};
        for (int reel = 0; reel < 5; reel++) {
            board[reel][0] = StakeSlotEngine.WILD;
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
    }

    private void spawnImpact(int reel, long now) {
        float cx = REEL_LEFT + reel * (REEL_W + REEL_GAP) + REEL_W / 2;
        for (int i = 0; i < 12; i++) {
            float angle = (float)(visualRandom.nextDouble() * Math.PI * 2);
            float speed = 28 + visualRandom.nextFloat() * 70;
            particles.add(new Particle(cx, REEL_TOP + CELL_H * 3 + 2,
                    (float)Math.cos(angle) * speed, -Math.abs((float)Math.sin(angle)) * speed,
                    now, 520 + visualRandom.nextInt(420), 0xFFF6C453,
                    1.4f + visualRandom.nextFloat() * 2.2f));
        }
    }

    private void spawnCelebration(long now, double multiplier) {
        int amount = multiplier >= 20 ? 130 : multiplier >= 5 ? 75 : 42;
        int[] colors = {0xFFF6C453, 0xFFFF6BB5, 0xFF64DCFF, 0xFF8B65FF, 0xFFFFFFFF};
        for (int i = 0; i < amount; i++) {
            particles.add(new Particle(28 + visualRandom.nextFloat() * 304,
                    165 + visualRandom.nextFloat() * 240,
                    -55 + visualRandom.nextFloat() * 110,
                    -135 - visualRandom.nextFloat() * 145,
                    now, 1300 + visualRandom.nextInt(1700), colors[i % colors.length],
                    1.4f + visualRandom.nextFloat() * 3.5f));
        }
    }

    private void updateParticles(long now) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle q = particles.get(i);
            if (now - q.birth > q.life) { particles.remove(i); continue; }
            q.vy += 160f * dt;
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

    private void panel(Canvas c, float l, float t, float r, float b, float radius, int fill, int line) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(fill);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.25f); paint.setColor(line);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void button(Canvas c, float l, float t, float r, float b, String label,
                        int fill, int color, float size) {
        paint.setShadowLayer(7, 0, 3, (fill & 0x00FFFFFF) | 0x55000000);
        paint.setColor(fill);
        c.drawRoundRect(new RectF(l, t, r, b), (b - t) / 2, (b - t) / 2, paint);
        paint.clearShadowLayer();
        text(c, label, (l + r) / 2, (t + b) / 2 + size * .34f,
                size, color, true, Paint.Align.CENTER);
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
        paint.setColor(color);
        c.drawPath(path, paint);
        c.drawRoundRect(new RectF(cx - size * .6f, cy + size * .34f,
                cx + size * .6f, cy + size * .49f), 2, 2, paint);
    }

    private void diamond(Canvas c, float cx, float cy, float size, int color) {
        path.reset();
        path.moveTo(cx, cy - size);
        path.lineTo(cx + size * .82f, cy - size * .27f);
        path.lineTo(cx + size * .55f, cy + size);
        path.lineTo(cx - size * .55f, cy + size);
        path.lineTo(cx - size * .82f, cy - size * .27f);
        path.close();
        paint.setColor(color);
        paint.setShadowLayer(8, 0, 0, color);
        c.drawPath(path, paint);
        paint.clearShadowLayer();
    }

    private void bell(Canvas c, float cx, float cy, float size, int color) {
        path.reset();
        path.moveTo(cx - size * .7f, cy + size * .45f);
        path.quadTo(cx - size * .5f, cy - size * .75f, cx, cy - size * .85f);
        path.quadTo(cx + size * .5f, cy - size * .75f, cx + size * .7f, cy + size * .45f);
        path.close();
        paint.setColor(color);
        c.drawPath(path, paint);
        c.drawOval(new RectF(cx - size * .82f, cy + size * .32f,
                cx + size * .82f, cy + size * .62f), paint);
        paint.setColor(0xFF8B5714);
        c.drawCircle(cx, cy + size * .72f, size * .2f, paint);
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

    private static final class Particle {
        float x, y, vx, vy, rotation;
        final long birth, life;
        final int color;
        final float size;
        Particle(float x, float y, float vx, float vy, long birth, long life, int color, float size) {
            this.x=x; this.y=y; this.vx=vx; this.vy=vy; this.birth=birth; this.life=life;
            this.color=color; this.size=size;
        }
    }
}
