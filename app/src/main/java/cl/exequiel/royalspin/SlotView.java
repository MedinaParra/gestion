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

/**
 * Royal Spin visual client. StakeSlotEngine remains the sole source of outcomes.
 * FEATURE_LEVEL is advanced in traceable iterations from v0.4 to v0.8.
 */
public final class SlotView extends View implements Choreographer.FrameCallback {
    public static final int FEATURE_LEVEL = 4;
    public static final String VERSION_LABEL = "v0.4 · REEL SPECTACLE";

    private enum Phase { IDLE, SPINNING, REVEALING }

    private static final float W = 360f, H = 800f;
    private static final float REEL_LEFT = 20f, REEL_TOP = 183f;
    private static final float REEL_W = 60f, REEL_GAP = 4f, CELL_H = 84f;
    private static final long[] STOP = {920L, 1210L, 1520L, 1860L, 2220L};
    private static final long REVEAL_MS = 4300L;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random random = new Random();
    private final Random visualRandom = new Random(0x51A7C0DEL);
    private final StakeSlotEngine engine = new StakeSlotEngine();
    private final SharedPreferences prefs;
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(new Locale("es", "CL"));
    private final CasinoAudio audio;
    private final HapticEngine haptics;
    private final List<Particle> particles = new ArrayList<>();
    private final boolean[] stopTriggered = new boolean[StakeSlotEngine.REEL_COUNT];

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
    private float frameDt = 1f / 60f;
    private float scale = 1f, offsetX, offsetY;
    private int credits;
    private int betPerLine;
    private int lastWin;
    private int displayedWin;
    private int rounds;
    private boolean payoutApplied;
    private boolean rewardTriggered;
    private boolean soundEnabled;
    private boolean hapticEnabled;
    private boolean reducedMotion;
    private boolean frameLoop;
    private boolean autoDemo;
    private String message = "20 líneas activas · toca GIRAR";

    public SlotView(Context context) {
        this(context, null);
    }

    public SlotView(Context context, String demoMode) {
        super(context);
        prefs = context.getSharedPreferences("royal_spin_visual", Context.MODE_PRIVATE);
        credits = prefs.getInt("credits", 2500);
        betPerLine = StakeSlotEngine.clampBet(prefs.getInt("bet", 1));
        rounds = prefs.getInt("rounds", 0);
        soundEnabled = prefs.getBoolean("sound", true);
        hapticEnabled = prefs.getBoolean("haptic", true);
        reducedMotion = prefs.getBoolean("reduced_motion", false);
        audio = new CasinoAudio(soundEnabled, FEATURE_LEVEL);
        haptics = new HapticEngine(context, hapticEnabled);
        current = engine.spin(new Random(20260723L), betPerLine);
        autoDemo = "bigwin".equals(demoMode);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        if (autoDemo) postDelayed(() -> startSpin(true), 500L);
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
            frameDt = Math.min(0.05f, delta / 1_000_000_000f);
            totalFrames++;
            if (delta > 22_000_000L) slowFrames++;
        }
        previousFrameNanos = frameTimeNanos;
        if (fpsWindowNanos == 0L) fpsWindowNanos = frameTimeNanos;
        fpsFrames++;
        if (frameTimeNanos - fpsWindowNanos >= 1_000_000_000L) {
            fps = fpsFrames;
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
            for (int reel = 0; reel < STOP.length; reel++) {
                if (!stopTriggered[reel] && elapsed >= STOP[reel]) {
                    stopTriggered[reel] = true;
                    audio.playReelStop(reel);
                    haptics.reelStop(reel);
                    spawnImpact(reel, now);
                }
            }
            if (elapsed >= STOP[4] + 170L) settleResult(now);
        } else if (phase == Phase.REVEALING) {
            long elapsed = now - phaseStart;
            float progress = clamp(elapsed / 1150f);
            displayedWin = Math.round(lastWin * easeOut(progress));
            if (!rewardTriggered && elapsed > 130L) {
                rewardTriggered = true;
                if (lastWin > 0) {
                    audio.playWin(current.payoutMultiplier());
                    haptics.win(current.payoutMultiplier());
                    spawnCelebration(now, current.payoutMultiplier());
                } else audio.playLose();
            }
            if (elapsed >= (lastWin > 0 ? REVEAL_MS : 1100L)) finishRound();
        }
    }

    private void settleResult(long now) {
        if (pending == null) return;
        current = pending;
        pending = null;
        lastWin = current.totalPayout;
        displayedWin = 0;
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
        displayedWin = lastWin;
        phase = Phase.IDLE;
        if (lastWin > 0) {
            message = "Ganaste " + numbers.format(lastWin) + " CR · "
                    + formatMultiplier(current.payoutMultiplier()) + "x";
        } else message = "Sin premio · cada giro es independiente";
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
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
        p.setShader(new LinearGradient(0, 0, 0, H,
                new int[]{0xFF03040C, 0xFF10102A, 0xFF160A28, 0xFF03050B},
                new float[]{0f, .34f, .68f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
        float t = now * 0.00012f;
        for (int i = 0; i < 44; i++) {
            float x = (i * 83.7f + (float)Math.sin(t + i) * 24f + 400f) % 360f;
            float y = (i * 137.3f + (float)Math.cos(t * .8f + i) * 35f + 900f) % 800f;
            int alpha = 24 + (i % 5) * 7;
            p.setColor((alpha << 24) | (i % 4 == 0 ? 0xF6C453 : 0x7358FF));
            c.drawCircle(x, y, i % 6 == 0 ? 1.8f : .9f, p);
        }
        p.setShader(new RadialGradient(180, 285, 250,
                new int[]{0x285B35FF, 0x102C0F6A, 0x00000000}, null, Shader.TileMode.CLAMP));
        c.drawCircle(180, 285, 250, p);
        p.setShader(null);
    }

    private void drawHeader(Canvas c) {
        crown(c, 180, 27, 20, 0xFFF6C453);
        text(c, "ROYAL SPIN", 180, 62, 26, 0xFFF6C453, true, Paint.Align.CENTER);
        text(c, VERSION_LABEL, 180, 79, 8, 0xFFAAB0C0, true, Paint.Align.CENTER);
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
        if (!reducedMotion && phase == Phase.SPINNING) {
            long e = now - phaseStart;
            for (int r = 0; r < STOP.length; r++) {
                float d = Math.abs(e - STOP[r]);
                if (d < 110f) {
                    float amp = (1f - d / 110f) * (1.1f + r * .18f);
                    shakeX += (float)Math.sin(e * .18f + r) * amp;
                    shakeY += (float)Math.cos(e * .23f + r) * amp * .55f;
                }
            }
        }
        c.save();
        c.translate(shakeX, shakeY);
        // Outer shadow and bevels.
        p.setShadowLayer(22, 0, 9, 0xCC000000);
        p.setShader(new LinearGradient(12, 160, 348, 490,
                new int[]{0xFF5A3B12, 0xFFFFDC79, 0xFF50320C, 0xFFC98A24},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(11, 160, 349, 490), 27, 27, p);
        p.clearShadowLayer();
        p.setShader(null);
        panel(c, 16, 165, 344, 485, 23, 0xFF090D17, 0xFFFFD76B);
        panel(c, 20, 171, 340, 451, 17, 0xFFEEE7D6, 0xFF5A6070);

        // Top and bottom metallic lips create depth.
        p.setShader(new LinearGradient(0, 171, 0, 196,
                new int[]{0xFFFFFFFF, 0xFF9D9686, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(20, 171, 340, 210), 15, 15, p);
        p.setShader(new LinearGradient(0, 416, 0, 451,
                new int[]{0x00FFFFFF, 0xFF8A8374, 0xFFFFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(20, 410, 340, 451), 15, 15, p);
        p.setShader(null);

        long elapsed = phase == Phase.SPINNING ? now - phaseStart : 99999L;
        StakeSlotEngine.SpinResult source = pending != null ? pending : current;
        for (int reel = 0; reel < StakeSlotEngine.REEL_COUNT; reel++) {
            float left = REEL_LEFT + reel * (REEL_W + REEL_GAP);
            RectF clip = new RectF(left, REEL_TOP, left + REEL_W, REEL_TOP + CELL_H * 3);
            c.save();
            c.clipRoundRect(clip, 7, 7);
            boolean spinning = phase == Phase.SPINNING && elapsed < STOP[reel];
            if (spinning) drawSpinningReel(c, reel, left, elapsed);
            else drawStoppedReel(c, source, reel, left, now);
            drawReelShading(c, clip, spinning);
            c.restore();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(reel == 2 ? 2.3f : 1.15f);
            p.setColor(reel == 2 ? 0xFFF6C453 : 0xFF656C7A);
            c.drawRoundRect(clip, 7, 7, p);
            p.setStyle(Paint.Style.FILL);
        }
        drawWinLine(c, now);
        text(c, "20 LÍNEAS · ♛ WILD · RESULTADO FIJADO ANTES DE ANIMAR", 180, 472,
                7, 0xFFA7ADBA, true, Paint.Align.CENTER);
        c.restore();
    }

    private void drawSpinningReel(Canvas c, int reel, float left, long elapsed) {
        float stop = STOP[reel];
        float q = clamp(elapsed / stop);
        float velocity = reducedMotion ? 0.55f : (0.38f + 1.75f * (float)Math.sin(Math.PI * Math.min(1f, q)));
        float travel = (elapsed * velocity + reel * 31f) % CELL_H;
        int base = (int)(elapsed / Math.max(28f, 62f - velocity * 13f)) + reel * 7;
        for (int item = -2; item <= 4; item++) {
            int index = Math.floorMod(base + item, StakeSlotEngine.SYMBOLS.length);
            float top = REEL_TOP + item * CELL_H + travel;
            drawSymbolCell(c, left + 2, top + 2, REEL_W - 4, CELL_H - 4,
                    StakeSlotEngine.SYMBOLS[index], 1f, false, elapsed);
        }
        if (!reducedMotion) {
            p.setShader(new LinearGradient(left, REEL_TOP, left + REEL_W, REEL_TOP,
                    new int[]{0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            for (int i = 0; i < 7; i++) {
                float y = REEL_TOP + ((elapsed * (1.4f + i * .11f) + i * 43f) % (CELL_H * 3));
                c.drawRoundRect(new RectF(left + 5, y, left + REEL_W - 5, y + 2.4f), 2, 2, p);
            }
            p.setShader(null);
        }
    }

    private void drawStoppedReel(Canvas c, StakeSlotEngine.SpinResult result, int reel, float left, long now) {
        float bounce = 0f;
        if (phase == Phase.SPINNING || phase == Phase.REVEALING) {
            float d = now - phaseStart - STOP[reel];
            if (d >= 0 && d < 520 && !reducedMotion) {
                bounce = (float)(Math.sin(d * .035) * Math.exp(-d / 155f) * 9.5f);
            }
        }
        for (int row = 0; row < StakeSlotEngine.ROW_COUNT; row++) {
            String symbol = result.board[reel][row];
            drawSymbolCell(c, left + 2, REEL_TOP + row * CELL_H + 2 + bounce,
                    REEL_W - 4, CELL_H - 4, symbol, 1f, isWinningCell(reel, row, now), now);
        }
    }

    private void drawSymbolCell(Canvas c, float left, float top, float width, float height,
                                String symbol, float alpha, boolean winning, long now) {
        int color = StakeSlotEngine.symbolColor(symbol);
        float pulse = winning ? 1f + .055f * (float)Math.sin(now * .012f) : 1f;
        c.save();
        c.scale(pulse, pulse, left + width / 2, top + height / 2);
        if (winning) {
            p.setShadowLayer(14, 0, 0, color);
            p.setColor((color & 0x00FFFFFF) | 0x66000000);
            c.drawRoundRect(new RectF(left - 2, top - 2, left + width + 2, top + height + 2), 10, 10, p);
            p.clearShadowLayer();
        }
        p.setShader(new LinearGradient(left, top, left, top + height,
                new int[]{0xFFFFFFFF, 0xFFF4EDDC, 0xFFD8D0C0}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(left, top, left + width, top + height), 7, 7, p);
        p.setShader(null);
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
            p.setShadowLayer(3, 0, 2, 0x55000000);
            text(c, label, left + width / 2, top + height / 2 + size * .34f,
                    size, color, true, Paint.Align.CENTER);
            p.clearShadowLayer();
        }
        c.restore();
    }

    private void drawReelShading(Canvas c, RectF clip, boolean spinning) {
        p.setShader(new LinearGradient(0, clip.top, 0, clip.bottom,
                new int[]{0xAA000000, 0x00000000, 0x00000000, 0xAA000000},
                new float[]{0f, .18f, .82f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(clip, p);
        p.setShader(null);
        if (spinning) {
            p.setColor(0x14FFFFFF);
            c.drawRect(clip.left, clip.top, clip.right, clip.bottom, p);
        }
    }

    private void drawWinLine(Canvas c, long now) {
        if (phase != Phase.REVEALING || current.lineWins.isEmpty()) return;
        int index = (int)(((now - phaseStart) / 620L) % current.lineWins.size());
        StakeSlotEngine.LineWin win = current.lineWins.get(index);
        path.reset();
        for (int reel = 0; reel < 5; reel++) {
            float x = REEL_LEFT + reel * (REEL_W + REEL_GAP) + REEL_W / 2;
            float y = REEL_TOP + win.rows[reel] * CELL_H + CELL_H / 2;
            if (reel == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(8);
        p.setColor(0x44F6C453);
        p.setMaskFilter(new BlurMaskFilter(10, BlurMaskFilter.Blur.NORMAL));
        c.drawPath(path, p);
        p.setMaskFilter(null);
        p.setStrokeWidth(3.8f);
        p.setColor(0xFFFFE287);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        panel(c, 91, 408, 269, 438, 15, 0xED160F05, 0xFFF6C453);
        text(c, "LÍNEA " + (win.lineIndex + 1) + "  +" + numbers.format(win.payout) + " CR",
                180, 428, 10, 0xFFFFE8A0, true, Paint.Align.CENTER);
    }

    private boolean isWinningCell(int reel, int row, long now) {
        if (phase != Phase.REVEALING || current == null || current.lineWins.isEmpty()) return false;
        int index = (int)(((now - phaseStart) / 620L) % current.lineWins.size());
        StakeSlotEngine.LineWin win = current.lineWins.get(index);
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
        int winShown = phase == Phase.REVEALING ? displayedWin : lastWin;
        text(c, numbers.format(winShown) + " CR", 325, 646, 16,
                winShown > 0 ? 0xFFF6C453 : Color.WHITE, true, Paint.Align.RIGHT);
        text(c, message, 180, 696, 10, 0xFFD9DCE4, true, Paint.Align.CENTER);
        String label = phase == Phase.IDLE ? "GIRAR" : "OMITIR ANIMACIÓN";
        int fill = phase == Phase.IDLE ? 0xFFF6C453 : 0xFF5B3E91;
        button(c, 34, 711, 326, 766, label, fill,
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
            invalidate();
            return true;
        }
        if (x < 82 && y < 65 && phase == Phase.IDLE) {
            credits = 2500; lastWin = displayedWin = rounds = 0;
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
        lastWin = displayedWin = 0;
        payoutApplied = rewardTriggered = false;
        particles.clear();
        for (int i = 0; i < stopTriggered.length; i++) stopTriggered[i] = false;
        rounds++;
        message = "Resultado calculado · coreografía en ejecución";
        audio.playSpinStart(); haptics.tap(); save();
    }

    private StakeSlotEngine.SpinResult createBigWin() {
        String[][] b = new String[5][3];
        String[] mid = {StakeSlotEngine.ACE, StakeSlotEngine.KING, StakeSlotEngine.QUEEN,
                StakeSlotEngine.JACK, StakeSlotEngine.BELL};
        String[] low = {StakeSlotEngine.JACK, StakeSlotEngine.QUEEN, StakeSlotEngine.KING,
                StakeSlotEngine.ACE, StakeSlotEngine.BAR};
        for (int r = 0; r < 5; r++) {
            b[r][0] = StakeSlotEngine.WILD;
            b[r][1] = mid[r];
            b[r][2] = low[r];
        }
        return engine.evaluate(b, new int[5], betPerLine);
    }

    private void skipAnimation() {
        long now = SystemClock.uptimeMillis();
        if (phase == Phase.SPINNING) settleResult(now);
        if (phase == Phase.REVEALING) finishRound();
        particles.clear();
        audio.stopRewardSequence();
    }

    private void spawnImpact(int reel, long now) {
        int amount = reducedMotion ? 4 : 12;
        float cx = REEL_LEFT + reel * (REEL_W + REEL_GAP) + REEL_W / 2;
        for (int i = 0; i < amount; i++) {
            float angle = (float)(visualRandom.nextDouble() * Math.PI * 2);
            float speed = 28 + visualRandom.nextFloat() * 70;
            particles.add(new Particle(cx, REEL_TOP + CELL_H * 3 + 2,
                    (float)Math.cos(angle) * speed, -Math.abs((float)Math.sin(angle)) * speed,
                    now, 520 + visualRandom.nextInt(420), 0xFFF6C453, 1.4f + visualRandom.nextFloat() * 2.2f));
        }
    }

    private void spawnCelebration(long now, double multiplier) {
        int amount = reducedMotion ? 18 : multiplier >= 20 ? 130 : multiplier >= 5 ? 75 : 42;
        for (int i = 0; i < amount; i++) {
            float x = 28 + visualRandom.nextFloat() * 304;
            float y = 165 + visualRandom.nextFloat() * 240;
            float vx = -55 + visualRandom.nextFloat() * 110;
            float vy = -135 - visualRandom.nextFloat() * 145;
            int[] colors = {0xFFF6C453, 0xFFFF6BB5, 0xFF64DCFF, 0xFF8B65FF, 0xFFFFFFFF};
            particles.add(new Particle(x, y, vx, vy, now,
                    1300 + visualRandom.nextInt(1700), colors[i % colors.length],
                    1.4f + visualRandom.nextFloat() * 3.5f));
        }
    }

    private void updateParticles(long now) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle q = particles.get(i);
            if (now - q.birth > q.life) { particles.remove(i); continue; }
            q.vy += 160f * frameDt;
            q.x += q.vx * frameDt;
            q.y += q.vy * frameDt;
            q.rotation += q.vx * frameDt * .06f;
        }
    }

    private void drawParticles(Canvas c, long now) {
        for (Particle q : particles) {
            float life = clamp((now - q.birth) / (float)q.life);
            int a = (int)(255 * (1f - life));
            p.setColor((q.color & 0x00FFFFFF) | (a << 24));
            c.save();
            c.rotate(q.rotation, q.x, q.y);
            c.drawRoundRect(new RectF(q.x - q.size, q.y - q.size * .45f,
                    q.x + q.size, q.y + q.size * .45f), q.size / 2, q.size / 2, p);
            c.restore();
        }
    }

    private void save() {
        prefs.edit().putInt("credits", credits).putInt("bet", betPerLine)
                .putInt("rounds", rounds).putBoolean("sound", soundEnabled)
                .putBoolean("haptic", hapticEnabled).putBoolean("reduced_motion", reducedMotion).apply();
    }

    public void onHostPause() {
        if (phase != Phase.IDLE) skipAnimation();
    }

    public void release() {
        frameLoop = false;
        Choreographer.getInstance().removeFrameCallback(this);
        audio.release(); haptics.release(); particles.clear();
    }

    private void panel(Canvas c, float l, float t, float r, float b, float radius, int fill, int line) {
        p.setStyle(Paint.Style.FILL); p.setColor(fill);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.25f); p.setColor(line);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void button(Canvas c, float l, float t, float r, float b, String label,
                        int fill, int color, float size) {
        p.setShadowLayer(7, 0, 3, (fill & 0x00FFFFFF) | 0x55000000);
        p.setColor(fill); c.drawRoundRect(new RectF(l, t, r, b), (b - t) / 2, (b - t) / 2, p);
        p.clearShadowLayer(); text(c, label, (l + r) / 2, (t + b) / 2 + size * .34f,
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
        path.lineTo(cx + size * .58f, cy + size * .28f); path.close();
        p.setColor(color); c.drawPath(path, p);
        c.drawRoundRect(new RectF(cx - size * .6f, cy + size * .34f,
                cx + size * .6f, cy + size * .49f), 2, 2, p);
    }

    private void diamond(Canvas c, float cx, float cy, float size, int color) {
        path.reset(); path.moveTo(cx, cy - size); path.lineTo(cx + size * .82f, cy - size * .27f);
        path.lineTo(cx + size * .55f, cy + size); path.lineTo(cx - size * .55f, cy + size);
        path.lineTo(cx - size * .82f, cy - size * .27f); path.close();
        p.setColor(color); p.setShadowLayer(8, 0, 0, color); c.drawPath(path, p); p.clearShadowLayer();
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0xCCFFFFFF);
        c.drawLine(cx, cy - size, cx, cy + size, p); p.setStyle(Paint.Style.FILL);
    }

    private void bell(Canvas c, float cx, float cy, float size, int color) {
        path.reset(); path.moveTo(cx - size * .7f, cy + size * .45f);
        path.quadTo(cx - size * .5f, cy - size * .75f, cx, cy - size * .85f);
        path.quadTo(cx + size * .5f, cy - size * .75f, cx + size * .7f, cy + size * .45f);
        path.close(); p.setColor(color); p.setShadowLayer(7, 0, 2, 0x77000000); c.drawPath(path, p); p.clearShadowLayer();
        c.drawOval(new RectF(cx - size * .82f, cy + size * .32f, cx + size * .82f, cy + size * .62f), p);
        p.setColor(0xFF8B5714); c.drawCircle(cx, cy + size * .72f, size * .2f, p);
    }

    private void text(Canvas c, String value, float x, float y, float size, int color,
                      boolean bold, Paint.Align align) {
        p.setShader(null); p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextSize(size);
        p.setTextAlign(align); p.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(value, x, y, p);
    }

    private String formatMultiplier(double value) {
        if (value >= 100) return String.format(Locale.US, "%.0f", value);
        if (value >= 10) return String.format(Locale.US, "%.1f", value);
        return String.format(Locale.US, "%.2f", value);
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
    private static float easeOut(float v) { float q = 1f - v; return 1f - q * q * q; }

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
