package cl.exequiel.royalspin;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Random;

/**
 * Audiovisual 5x3 slot presentation. StakeSlotEngine fixes every outcome before this view
 * starts animating; presentation, audio and haptics never alter the mathematical result.
 */
public final class SlotView extends View implements Choreographer.FrameCallback,
        AnimationTimeline.Listener {
    private enum Phase { IDLE, SPINNING, REVEALING }

    private static final int EVENT_REEL_STOP = 1;
    private static final int EVENT_SETTLE_RESULT = 2;
    private static final int EVENT_REVEAL_RESULT = 3;
    private static final int EVENT_LINE_ACCENT = 4;
    private static final int EVENT_COMPLETE = 5;

    private static final float DESIGN_WIDTH = 360f;
    private static final float DESIGN_HEIGHT = 800f;
    private static final float REEL_LEFT = 20f;
    private static final float REEL_TOP = 176f;
    private static final float REEL_WIDTH = 60f;
    private static final float REEL_GAP = 4f;
    private static final float CELL_HEIGHT = 86f;
    private static final long[] REEL_STOP_MS = {900L, 1180L, 1480L, 1810L, 2170L};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random random = new Random();
    private final StakeSlotEngine engine = new StakeSlotEngine();
    private final SharedPreferences preferences;
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(new Locale("es", "CL"));
    private final CasinoAudio audio;
    private final HapticEngine haptics;
    private final AnimationTimeline timeline = new AnimationTimeline();
    private final ParticleField particles = new ParticleField();
    private final FrameStats frameStats = new FrameStats();
    private final Choreographer choreographer;

    private StakeSlotEngine.SpinResult currentResult;
    private StakeSlotEngine.SpinResult pendingResult;
    private Phase phase = Phase.IDLE;
    private long spinStartedAt;
    private long revealStartedAt;
    private long renderNowMs;
    private long flashUntilMs;
    private long lastImpactAtMs;
    private long lastCoinTickAtMs;
    private long lastCelebrationBurstAtMs;
    private final long[] reelStoppedAt = new long[StakeSlotEngine.REEL_COUNT];
    private boolean payoutApplied;
    private boolean attached;
    private boolean frameCallbackPosted;

    private int credits;
    private int betPerLine;
    private int lastWin;
    private int displayedWin;
    private int spinsPlayed;
    private String message = "20 líneas activas · toca GIRAR";

    private float scale = 1f;
    private float offsetX;
    private float offsetY;

    public SlotView(Context context) {
        super(context);
        preferences = context.getSharedPreferences("royal_spin_stake_slot", Context.MODE_PRIVATE);
        credits = preferences.getInt("credits", 2500);
        betPerLine = StakeSlotEngine.clampBet(preferences.getInt("bet_per_line", 1));
        spinsPlayed = preferences.getInt("spins_played", 0);
        boolean soundEnabled = preferences.getBoolean("sound_enabled", true);
        boolean hapticEnabled = preferences.getBoolean("haptic_enabled", true);
        audio = new CasinoAudio(soundEnabled);
        haptics = new HapticEngine(context, hapticEnabled);
        currentResult = engine.spin(new Random(20260722L), betPerLine);
        choreographer = Choreographer.getInstance();
        paint.setStrokeCap(Paint.Cap.ROUND);
        setFocusable(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        if (needsFrames()) requestFrameLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        if (frameCallbackPosted) choreographer.removeFrameCallback(this);
        frameCallbackPosted = false;
        frameStats.resetClock();
        super.onDetachedFromWindow();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        frameCallbackPosted = false;
        float deltaSeconds = frameStats.record(frameTimeNanos);
        renderNowMs = frameTimeNanos / 1_000_000L;
        timeline.dispatch(renderNowMs, this);
        updateContinuousAnimation(renderNowMs);
        particles.update(deltaSeconds);
        invalidate();
        if (needsFrames()) requestFrameLoop();
    }

    private void requestFrameLoop() {
        if (!attached || frameCallbackPosted) return;
        frameCallbackPosted = true;
        choreographer.postFrameCallback(this);
    }

    private boolean needsFrames() {
        return phase != Phase.IDLE || timeline.isRunning() || particles.hasParticles()
                || renderNowMs < flashUntilMs;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = renderNowMs > 0L ? renderNowMs : SystemClock.uptimeMillis();
        scale = Math.min(getWidth() / DESIGN_WIDTH, getHeight() / DESIGN_HEIGHT);
        offsetX = (getWidth() - DESIGN_WIDTH * scale) / 2f;
        offsetY = (getHeight() - DESIGN_HEIGHT * scale) / 2f;

        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        drawBackground(canvas, now);
        drawHeader(canvas);
        drawReelMachine(canvas, now);
        drawControls(canvas);
        particles.draw(canvas, paint);
        drawFlash(canvas, now);
        canvas.restore();
    }

    @Override
    public void onTimelineEvent(int type, int argument, long scheduledAtMs) {
        if (type == EVENT_REEL_STOP) {
            int reel = Math.max(0, Math.min(StakeSlotEngine.REEL_COUNT - 1, argument));
            reelStoppedAt[reel] = scheduledAtMs;
            lastImpactAtMs = scheduledAtMs;
            audio.playReelStop(reel);
            haptics.reelStop(reel);
            float centerX = REEL_LEFT + reel * (REEL_WIDTH + REEL_GAP) + REEL_WIDTH / 2f;
            particles.burst(centerX, REEL_TOP + CELL_HEIGHT * 3f - 4f,
                    13 + reel * 2, 0xFFF6C453, 0.58f + reel * 0.08f);
        } else if (type == EVENT_SETTLE_RESULT) {
            settlePendingResult(scheduledAtMs);
        } else if (type == EVENT_REVEAL_RESULT) {
            revealResult(scheduledAtMs);
        } else if (type == EVENT_LINE_ACCENT) {
            if (currentResult != null && !currentResult.lineWins.isEmpty()) {
                int index = Math.floorMod(argument, currentResult.lineWins.size());
                audio.playLineAccent(currentResult.lineWins.get(index).lineIndex);
                particles.burst(180f, 327f, 12, 0xFFFFE8A5, 0.72f);
            }
        } else if (type == EVENT_COMPLETE) {
            completeRound();
        }
    }

    private void settlePendingResult(long now) {
        if (pendingResult == null) return;
        currentResult = pendingResult;
        pendingResult = null;
        lastWin = currentResult.totalPayout;
        if (!payoutApplied) {
            credits += lastWin;
            payoutApplied = true;
        }
        displayedWin = 0;
        phase = Phase.REVEALING;
        revealStartedAt = now;
        lastCoinTickAtMs = 0L;
        lastCelebrationBurstAtMs = 0L;
        saveState();
    }

    private void revealResult(long now) {
        if (currentResult == null) return;
        double multiplier = currentResult.payoutMultiplier();
        if (lastWin > 0) {
            audio.playWin(multiplier);
            haptics.win(multiplier);
            particles.celebration(180f, 345f, multiplier);
            flashUntilMs = now + (multiplier >= 20d ? 520L : multiplier >= 5d ? 330L : 190L);
            message = multiplier >= 20d ? "GRAN PREMIO · contando créditos"
                    : "Premio confirmado · contando créditos";
        } else {
            audio.playLose();
            message = "Sin premio · cada giro es independiente";
        }
    }

    private void completeRound() {
        displayedWin = lastWin;
        phase = Phase.IDLE;
        if (lastWin > 0) {
            message = "Ganaste " + numberFormat.format(lastWin) + " CR · "
                    + formatMultiplier(currentResult.payoutMultiplier()) + "x";
        } else {
            message = "Sin premio · resultado independiente";
        }
        saveState();
    }

    private void updateContinuousAnimation(long now) {
        if (phase != Phase.REVEALING || currentResult == null) return;
        long elapsed = Math.max(0L, now - revealStartedAt);
        long countDuration = lastWin >= 1000 ? 2100L : lastWin >= 250 ? 1550L : 950L;
        float progress = Math.min(1f, elapsed / (float) countDuration);
        float eased = 1f - (float) Math.pow(1f - progress, 3d);
        int previous = displayedWin;
        displayedWin = lastWin <= 0 ? 0 : Math.min(lastWin, Math.round(lastWin * eased));
        if (displayedWin != previous && now - lastCoinTickAtMs >= 105L) {
            lastCoinTickAtMs = now;
            audio.playCountTick((int) (elapsed / 105L));
        }
        double multiplier = currentResult.payoutMultiplier();
        if (lastWin > 0 && multiplier >= 5d && elapsed < 2100L
                && now - lastCelebrationBurstAtMs >= 430L) {
            lastCelebrationBurstAtMs = now;
            particles.burst(45f + random.nextFloat() * 270f, 210f + random.nextFloat() * 190f,
                    multiplier >= 20d ? 20 : 11, 0xFFF6C453, multiplier >= 20d ? 1.15f : 0.82f);
        }
    }

    private void drawBackground(Canvas canvas, long now) {
        paint.setShader(new LinearGradient(0, 0, 360, 800,
                new int[]{0xFF02050B, 0xFF12102C, 0xFF06070E}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 360, 800, paint);
        paint.setShader(null);

        float movement = phase == Phase.IDLE ? 0f : (now - spinStartedAt) / 55f;
        for (int i = 0; i < 40; i++) {
            float x = Math.floorMod((int) (i * 83f + movement * (1 + i % 3)), 360);
            float y = Math.floorMod((int) (i * 131f + movement * (1 + i % 4) * 0.42f), 800);
            int alpha = 35 + (i % 5) * 9;
            paint.setColor((i % 4 == 0 ? 0x00F6C453 : 0x005D70FF) | (alpha << 24));
            canvas.drawCircle(x, y, i % 6 == 0 ? 1.8f : 1f, paint);
        }

        if (phase == Phase.REVEALING && lastWin > 0) {
            float pulse = 0.5f + 0.5f * (float) Math.sin((now - revealStartedAt) / 115f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f + pulse * 2f);
            paint.setColor((0xFFF6C453 & 0x00FFFFFF) | ((int) (35 + 55 * pulse) << 24));
            canvas.drawCircle(180f, 333f, 135f + pulse * 12f, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawHeader(Canvas canvas) {
        crown(canvas, 180, 27, 21, 0xFFF6C453);
        text(canvas, "ROYAL SPIN", 180, 64, 26, 0xFFF6C453, true, Paint.Align.CENTER);
        text(canvas, "AUDIOVISUAL v0.3 · DEMO SIN DINERO REAL", 180, 81, 8,
                0xFF9CA4B7, true, Paint.Align.CENTER);

        button(canvas, 10, 23, 66, 54, "RESET", 0xFF242A38, 0xFFE8EAF0, 7);
        button(canvas, 234, 23, 286, 54, haptics.isEnabled() ? "VIB ON" : "VIB OFF",
                haptics.isEnabled() ? 0xFF243D35 : 0xFF242A38,
                haptics.isEnabled() ? 0xFF65E6A4 : 0xFF9097A6, 7);
        button(canvas, 290, 23, 350, 54, audio.isEnabled() ? "SFX ON" : "SFX OFF",
                audio.isEnabled() ? 0xFF4A3413 : 0xFF242A38,
                audio.isEnabled() ? 0xFFF6C453 : 0xFF9097A6, 7);

        panel(canvas, 16, 96, 344, 151, 17, 0xE80A0E18, 0xFF8B651D);
        text(canvas, "SALDO", 34, 118, 8, 0xFF9097A6, true, Paint.Align.LEFT);
        text(canvas, numberFormat.format(credits) + " CR", 34, 141, 20, Color.WHITE, true, Paint.Align.LEFT);
        text(canvas, "RTP TEÓRICO", 236, 118, 8, 0xFF9097A6, true, Paint.Align.CENTER);
        text(canvas, "95,48%", 236, 141, 16, 0xFFF6C453, true, Paint.Align.CENTER);
        text(canvas, "TIRADAS " + spinsPlayed, 331, 141, 7, 0xFF81899A, true, Paint.Align.RIGHT);
    }

    private void drawReelMachine(Canvas canvas, long now) {
        float shakeX = 0f;
        float shakeY = 0f;
        long impactAge = now - lastImpactAtMs;
        if (impactAge >= 0L && impactAge < 190L) {
            float decay = 1f - impactAge / 190f;
            shakeX = (float) Math.sin(impactAge * 0.18f) * 2.7f * decay;
            shakeY = (float) Math.cos(impactAge * 0.22f) * 1.5f * decay;
        }

        canvas.save();
        canvas.translate(shakeX, shakeY);
        panel(canvas, 14, 162, 346, 478, 22, 0xF20A0E18, 0xFF8D6820);
        paint.setColor(0xFFEEE8D8);
        canvas.drawRoundRect(new RectF(18, 172, 342, 442), 16, 16, paint);

        long elapsed = Math.max(0L, now - spinStartedAt);
        for (int reel = 0; reel < StakeSlotEngine.REEL_COUNT; reel++) {
            float left = REEL_LEFT + reel * (REEL_WIDTH + REEL_GAP);
            RectF clip = new RectF(left, REEL_TOP, left + REEL_WIDTH, REEL_TOP + CELL_HEIGHT * 3f);
            canvas.save();
            canvas.clipRect(clip);

            boolean spinning = phase == Phase.SPINNING && elapsed < REEL_STOP_MS[reel];
            if (spinning) {
                drawSpinningReel(canvas, reel, left, elapsed);
            } else {
                float bounce = reelBounce(reel, now);
                canvas.translate(0f, bounce);
                StakeSlotEngine.SpinResult source = phase == Phase.SPINNING && pendingResult != null
                        ? pendingResult : currentResult;
                drawStoppedReel(canvas, source, reel, left);
            }
            canvas.restore();

            long stopAge = now - reelStoppedAt[reel];
            boolean glowing = stopAge >= 0L && stopAge < 360L;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(glowing ? 2.5f : 1.2f);
            paint.setColor(glowing || reel == 2 ? 0xFFF6C453 : 0xFF6B7180);
            canvas.drawRoundRect(clip, 8, 8, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        drawActiveWinLine(canvas, now);
        text(canvas, "3+ iguales desde la izquierda · ♛ sustituye · 20 líneas",
                180, 463, 8, 0xFFAEB4C1, false, Paint.Align.CENTER);
        canvas.restore();
    }

    private void drawSpinningReel(Canvas canvas, int reel, float left, long elapsed) {
        float progress = Math.min(1f, elapsed / (float) REEL_STOP_MS[reel]);
        float eased = easeInOutCubic(progress);
        float turns = 8.5f + reel * 1.55f;
        float distance = eased * turns * CELL_HEIGHT;
        float travel = distance % CELL_HEIGHT;
        int baseIndex = (int) (distance / CELL_HEIGHT) + reel * 4;

        for (int item = -1; item <= 3; item++) {
            int symbolIndex = Math.floorMod(baseIndex + item, StakeSlotEngine.SYMBOLS.length);
            float top = REEL_TOP + item * CELL_HEIGHT + travel;
            drawSymbolCell(canvas, left + 2, top + 2, REEL_WIDTH - 4, CELL_HEIGHT - 4,
                    StakeSlotEngine.SYMBOLS[symbolIndex]);
        }

        if (progress > 0.12f && progress < 0.9f) {
            paint.setColor(0x44FFFFFF);
            for (int streak = 0; streak < 4; streak++) {
                float x = left + 8f + streak * 14f;
                canvas.drawRoundRect(new RectF(x, REEL_TOP + 18f, x + 2f,
                        REEL_TOP + CELL_HEIGHT * 3f - 18f), 2f, 2f, paint);
            }
        }
    }

    private float reelBounce(int reel, long now) {
        long stopped = reelStoppedAt[reel];
        if (stopped <= 0L) return 0f;
        float age = now - stopped;
        if (age < 0f || age > 420f) return 0f;
        float decay = 1f - age / 420f;
        return (float) Math.sin(age / 33f) * 8f * decay;
    }

    private float easeInOutCubic(float value) {
        float x = Math.max(0f, Math.min(1f, value));
        return x < 0.5f ? 4f * x * x * x
                : 1f - (float) Math.pow(-2f * x + 2f, 3d) / 2f;
    }

    private void drawStoppedReel(Canvas canvas, StakeSlotEngine.SpinResult result,
                                 int reel, float left) {
        if (result == null) return;
        for (int row = 0; row < StakeSlotEngine.ROW_COUNT; row++) {
            drawSymbolCell(canvas, left + 2, REEL_TOP + row * CELL_HEIGHT + 2,
                    REEL_WIDTH - 4, CELL_HEIGHT - 4, result.board[reel][row]);
        }
    }

    private void drawSymbolCell(Canvas canvas, float left, float top, float width,
                                float height, String symbol) {
        int symbolColor = StakeSlotEngine.symbolColor(symbol);
        paint.setShader(new LinearGradient(left, top, left, top + height,
                new int[]{0xFFFFFFFF, 0xFFF4EEDC, 0xFFE2DAC7}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(left, top, left + width, top + height), 7, 7, paint);
        paint.setShader(null);

        if (StakeSlotEngine.WILD.equals(symbol)) {
            paint.setColor(0x225F3A00);
            canvas.drawCircle(left + width / 2f, top + height / 2f, width * 0.42f, paint);
        }
        String label = StakeSlotEngine.displayLabel(symbol);
        float size = label.length() >= 4 ? 13f : label.length() >= 3 ? 18f : 31f;
        text(canvas, label, left + width / 2f, top + height / 2f + size * 0.34f,
                size, symbolColor, true, Paint.Align.CENTER);
    }

    private void drawActiveWinLine(Canvas canvas, long now) {
        if (phase != Phase.REVEALING || currentResult == null || currentResult.lineWins.isEmpty()) return;
        long elapsed = Math.max(0L, now - revealStartedAt);
        int index = (int) ((elapsed / 480L) % currentResult.lineWins.size());
        StakeSlotEngine.LineWin win = currentResult.lineWins.get(index);
        float pulse = 0.65f + 0.35f * (float) Math.sin(elapsed / 80f);

        path.reset();
        for (int reel = 0; reel < StakeSlotEngine.REEL_COUNT; reel++) {
            float centerX = REEL_LEFT + reel * (REEL_WIDTH + REEL_GAP) + REEL_WIDTH / 2f;
            float centerY = REEL_TOP + win.rows[reel] * CELL_HEIGHT + CELL_HEIGHT / 2f;
            if (reel == 0) path.moveTo(centerX, centerY); else path.lineTo(centerX, centerY);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.5f + 2f * pulse);
        paint.setColor(0xFFF6C453);
        canvas.drawPath(path, paint);
        for (int reel = 0; reel < win.count; reel++) {
            float centerX = REEL_LEFT + reel * (REEL_WIDTH + REEL_GAP) + REEL_WIDTH / 2f;
            float centerY = REEL_TOP + win.rows[reel] * CELL_HEIGHT + CELL_HEIGHT / 2f;
            canvas.drawCircle(centerX, centerY, 26f + 3f * pulse, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        panel(canvas, 98, 405, 262, 435, 15, 0xEE171006, 0xFFF6C453);
        text(canvas, "LÍNEA " + (win.lineIndex + 1) + " · +" + win.payout + " CR",
                180, 425, 10, 0xFFFFE7A0, true, Paint.Align.CENTER);
    }

    private void drawControls(Canvas canvas) {
        panel(canvas, 16, 494, 344, 666, 20, 0xE80A0E18, 0xFF394052);
        text(canvas, "APUESTA POR LÍNEA", 180, 520, 9, 0xFF9CA4B4, true, Paint.Align.CENTER);
        button(canvas, 28, 536, 86, 590, "−", 0xFF252B39, Color.WHITE, 24);
        panel(canvas, 103, 536, 257, 590, 15, 0xFF151B28, 0xFF8D6820);
        text(canvas, numberFormat.format(betPerLine) + " CR", 180, 570, 22,
                0xFFF6C453, true, Paint.Align.CENTER);
        button(canvas, 274, 536, 332, 590, "+", 0xFF252B39, Color.WHITE, 24);

        int totalBet = betPerLine * StakeSlotEngine.LINE_COUNT;
        int winToShow = phase == Phase.REVEALING ? displayedWin : lastWin;
        text(canvas, "APUESTA TOTAL", 42, 615, 8, 0xFF8F97A8, true, Paint.Align.LEFT);
        text(canvas, numberFormat.format(totalBet) + " CR", 42, 640, 16, Color.WHITE, true, Paint.Align.LEFT);
        text(canvas, "ÚLTIMO PREMIO", 318, 615, 8, 0xFF8F97A8, true, Paint.Align.RIGHT);
        text(canvas, numberFormat.format(winToShow) + " CR", 318, 640, 16,
                winToShow > 0 ? 0xFFF6C453 : Color.WHITE, true, Paint.Align.RIGHT);

        text(canvas, message, 180, 687, 10, 0xFFD8DBE3, true, Paint.Align.CENTER);
        int buttonColor = phase == Phase.IDLE ? 0xFFF6C453 : 0xFF725E26;
        String buttonText = phase == Phase.IDLE ? "GIRAR" : "OMITIR ANIMACIÓN";
        button(canvas, 36, 704, 324, 762, buttonText, buttonColor,
                phase == Phase.IDLE ? 0xFF161006 : 0xFFFFE9A8, 16);
        text(canvas, frameStats.fps() + " FPS · lentos "
                        + String.format(Locale.US, "%.1f", frameStats.slowFramePercent())
                        + "% · créditos ficticios",
                180, 788, 7, 0xFF727A8C, false, Paint.Align.CENTER);
    }

    private void drawFlash(Canvas canvas, long now) {
        if (now >= flashUntilMs) return;
        float remaining = Math.max(0f, Math.min(1f, (flashUntilMs - now) / 520f));
        paint.setColor((0xFFFFE8A5 & 0x00FFFFFF) | ((int) (95f * remaining) << 24));
        canvas.drawRect(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        float x = (event.getX() - offsetX) / scale;
        float y = (event.getY() - offsetY) / scale;

        if (x >= 290 && x <= 352 && y >= 18 && y <= 62) {
            audio.setEnabled(!audio.isEnabled());
            preferences.edit().putBoolean("sound_enabled", audio.isEnabled()).apply();
            if (audio.isEnabled()) audio.playTap();
            invalidate();
            return true;
        }
        if (x >= 228 && x <= 288 && y >= 18 && y <= 62) {
            haptics.setEnabled(!haptics.isEnabled());
            preferences.edit().putBoolean("haptic_enabled", haptics.isEnabled()).apply();
            if (haptics.isEnabled()) haptics.tap();
            audio.playTap();
            invalidate();
            return true;
        }
        if (x >= 8 && x <= 72 && y >= 18 && y <= 62 && phase == Phase.IDLE) {
            credits = 2500;
            lastWin = 0;
            displayedWin = 0;
            message = "Saldo demo reiniciado";
            audio.playTap();
            haptics.tap();
            saveState();
            invalidate();
            return true;
        }

        if (phase != Phase.IDLE) {
            if (y >= 690 && y <= 775) finishImmediately();
            return true;
        }

        if (y >= 526 && y <= 600 && x <= 100) {
            betPerLine = StakeSlotEngine.clampBet(betPerLine - 1);
            message = "Apuesta total: " + (betPerLine * StakeSlotEngine.LINE_COUNT) + " CR";
            audio.playTap();
            haptics.tap();
            saveState();
        } else if (y >= 526 && y <= 600 && x >= 260) {
            betPerLine = StakeSlotEngine.clampBet(betPerLine + 1);
            message = "Apuesta total: " + (betPerLine * StakeSlotEngine.LINE_COUNT) + " CR";
            audio.playTap();
            haptics.tap();
            saveState();
        } else if (y >= 690 && y <= 775) {
            startSpin();
        }
        invalidate();
        return true;
    }

    private void startSpin() {
        int totalBet = betPerLine * StakeSlotEngine.LINE_COUNT;
        if (credits < totalBet) {
            message = "Saldo insuficiente · pulsa RESET";
            audio.playError();
            haptics.error();
            return;
        }

        credits -= totalBet;
        pendingResult = engine.spin(random, betPerLine);
        phase = Phase.SPINNING;
        spinStartedAt = SystemClock.uptimeMillis();
        renderNowMs = spinStartedAt;
        revealStartedAt = 0L;
        lastWin = 0;
        displayedWin = 0;
        payoutApplied = false;
        spinsPlayed++;
        flashUntilMs = 0L;
        lastImpactAtMs = 0L;
        particles.clear();
        for (int reel = 0; reel < reelStoppedAt.length; reel++) reelStoppedAt[reel] = 0L;

        timeline.clear();
        for (int reel = 0; reel < REEL_STOP_MS.length; reel++) {
            timeline.add(REEL_STOP_MS[reel], EVENT_REEL_STOP, reel);
        }
        long settleAt = REEL_STOP_MS[REEL_STOP_MS.length - 1] + 180L;
        timeline.add(settleAt, EVENT_SETTLE_RESULT, 0);
        timeline.add(settleAt + 120L, EVENT_REVEAL_RESULT, 0);
        int lineAccents = Math.min(4, pendingResult.lineWins.size());
        for (int i = 0; i < lineAccents; i++) {
            timeline.add(settleAt + 430L + i * 430L, EVENT_LINE_ACCENT, i);
        }
        double multiplier = pendingResult.payoutMultiplier();
        long presentationDuration = pendingResult.totalPayout <= 0 ? 1100L
                : multiplier >= 20d ? 5000L : multiplier >= 5d ? 3700L : 2600L;
        timeline.add(settleAt + presentationDuration, EVENT_COMPLETE, 0);
        timeline.start(spinStartedAt);

        message = "Resultado fijado · coreografía en ejecución";
        audio.playSpinStart();
        haptics.tap();
        saveState();
        frameStats.resetClock();
        requestFrameLoop();
    }

    private void finishImmediately() {
        timeline.clear();
        if (pendingResult != null) {
            currentResult = pendingResult;
            pendingResult = null;
            lastWin = currentResult.totalPayout;
            if (!payoutApplied) {
                credits += lastWin;
                payoutApplied = true;
            }
        }
        displayedWin = lastWin;
        phase = Phase.IDLE;
        flashUntilMs = 0L;
        particles.clear();
        message = lastWin > 0 ? "Ganaste " + numberFormat.format(lastWin) + " CR"
                : "Sin premio · resultado independiente";
        audio.playTap();
        saveState();
        invalidate();
    }

    public void onHostPause() {
        if (phase != Phase.IDLE) finishImmediately();
        frameStats.resetClock();
    }

    private void saveState() {
        preferences.edit()
                .putInt("credits", credits)
                .putInt("bet_per_line", betPerLine)
                .putInt("spins_played", spinsPlayed)
                .putBoolean("sound_enabled", audio.isEnabled())
                .putBoolean("haptic_enabled", haptics.isEnabled())
                .apply();
    }

    private String formatMultiplier(double value) {
        if (value >= 100d) return String.format(Locale.US, "%.0f", value);
        if (value >= 10d) return String.format(Locale.US, "%.1f", value);
        return String.format(Locale.US, "%.2f", value);
    }

    public void release() {
        if (frameCallbackPosted) choreographer.removeFrameCallback(this);
        frameCallbackPosted = false;
        timeline.clear();
        particles.clear();
        audio.release();
        haptics.release();
    }

    private void panel(Canvas canvas, float left, float top, float right, float bottom,
                       float radius, int fill, int line) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.3f);
        paint.setColor(line);
        canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void button(Canvas canvas, float left, float top, float right, float bottom,
                        String label, int fill, int color, float textSize) {
        paint.setColor(fill);
        canvas.drawRoundRect(new RectF(left, top, right, bottom), (bottom - top) / 2f,
                (bottom - top) / 2f, paint);
        text(canvas, label, (left + right) / 2f, (top + bottom) / 2f + textSize * 0.34f,
                textSize, color, true, Paint.Align.CENTER);
    }

    private void crown(Canvas canvas, float centerX, float centerY, float size, int color) {
        path.reset();
        path.moveTo(centerX - size * 0.55f, centerY + size * 0.28f);
        path.lineTo(centerX - size * 0.45f, centerY - size * 0.38f);
        path.lineTo(centerX - size * 0.13f, centerY - size * 0.02f);
        path.lineTo(centerX, centerY - size * 0.58f);
        path.lineTo(centerX + size * 0.15f, centerY - size * 0.02f);
        path.lineTo(centerX + size * 0.48f, centerY - size * 0.38f);
        path.lineTo(centerX + size * 0.55f, centerY + size * 0.28f);
        path.close();
        paint.setColor(color);
        canvas.drawPath(path, paint);
        canvas.drawRoundRect(new RectF(centerX - size * 0.58f, centerY + size * 0.34f,
                centerX + size * 0.58f, centerY + size * 0.48f), 2, 2, paint);
    }

    private void text(Canvas canvas, String value, float x, float y, float size,
                      int color, boolean bold, Paint.Align align) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        canvas.drawText(value, x, y, paint);
    }
}
