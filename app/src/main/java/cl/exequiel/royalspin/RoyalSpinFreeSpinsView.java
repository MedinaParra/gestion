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
import java.util.Locale;
import java.util.Random;

/**
 * v1.6 feature frontend. It proves the complete Free Spins lifecycle while keeping
 * StakeSlotEngine as the only source of boards, line wins and payouts.
 */
public final class RoyalSpinFreeSpinsView extends View implements Choreographer.FrameCallback {
    private enum Phase {
        IDLE,
        BASE_SPINNING,
        BASE_REVEALING,
        FEATURE_INTRO,
        FEATURE_READY,
        FEATURE_SPINNING,
        FEATURE_REVEALING,
        RETRIGGER,
        FEATURE_SUMMARY
    }

    private static final float W = 360f;
    private static final float H = 800f;
    private static final float REEL_LEFT = 20f;
    private static final float REEL_TOP = 205f;
    private static final float REEL_W = 60f;
    private static final float REEL_GAP = 4f;
    private static final float CELL_H = 78f;
    private static final long[] STOPS = {850L, 1110L, 1390L, 1690L, 2020L};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final StakeSlotEngine engine = new StakeSlotEngine();
    private final Random random = new Random();
    private final Random visualRandom = new Random(0xFEE160L);
    private final SharedPreferences prefs;
    private final FeatureSessionRepository featureRepository;
    private final FeatureSessionController featureController;
    private final CasinoAudio audio;
    private final HapticEngine haptics;
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(new Locale("es", "CL"));
    private final boolean[] stopPlayed = new boolean[5];

    private StakeSlotEngine.SpinResult current;
    private StakeSlotEngine.SpinResult pending;
    private Phase phase;
    private long phaseStart;
    private long previousFrame;
    private long fpsStart;
    private int fpsFrames;
    private int fps = 60;
    private float scale = 1f;
    private float offsetX;
    private float offsetY;
    private int credits;
    private int betPerLine;
    private int rounds;
    private int lastWin;
    private int shownWin;
    private int lastRetriggerAdded;
    private boolean payoutApplied;
    private boolean rewardPlayed;
    private boolean soundEnabled;
    private boolean reducedMotion;
    private boolean frameLoop;
    private boolean buttonPressed;
    private String message;

    public RoyalSpinFreeSpinsView(Context context, String demoMode) {
        super(context);
        prefs = context.getSharedPreferences("royal_spin_feature_v16", Context.MODE_PRIVATE);
        featureRepository = new FeatureSessionRepository(prefs);
        featureController = new FeatureSessionController(featureRepository.load());
        credits = prefs.getInt("credits", 5000);
        betPerLine = StakeSlotEngine.clampBet(prefs.getInt("bet", 1));
        rounds = prefs.getInt("rounds", 0);
        soundEnabled = prefs.getBoolean("sound", true);
        reducedMotion = prefs.getBoolean("reduced_motion", false);
        audio = new CasinoAudio(soundEnabled, 16);
        haptics = new HapticEngine(context, prefs.getBoolean("haptic", true));
        current = engine.spin(new Random(20260724L), betPerLine, GameMode.BASE_GAME);
        phase = featureController.isActive() ? Phase.FEATURE_READY : Phase.IDLE;
        message = featureController.isActive()
                ? "Sesión recuperada · continúa tus juegos gratis"
                : "3 WILD en línea activan 30 juegos gratis";
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setFocusable(true);
        setContentDescription("Royal Spin Free Spins, demostración con créditos ficticios");
        if (demoMode != null && !demoMode.isEmpty()) {
            postDelayed(() -> startBaseSpin(demoMode), 650L);
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
        previousFrame = frameTimeNanos;
        if (fpsStart == 0L) fpsStart = frameTimeNanos;
        fpsFrames++;
        if (frameTimeNanos - fpsStart >= 1_000_000_000L) {
            fps = fpsFrames;
            fpsFrames = 0;
            fpsStart = frameTimeNanos;
        }
        update(SystemClock.uptimeMillis());
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void update(long now) {
        long elapsed = now - phaseStart;
        if (phase == Phase.BASE_SPINNING || phase == Phase.FEATURE_SPINNING) {
            for (int reel = 0; reel < 5; reel++) {
                if (!stopPlayed[reel] && elapsed >= STOPS[reel]) {
                    stopPlayed[reel] = true;
                    audio.playReelStop(reel);
                    haptics.reelStop(reel);
                }
            }
            if (elapsed >= STOPS[4] + 180L) settleRound(now);
            return;
        }

        if (phase == Phase.BASE_REVEALING || phase == Phase.FEATURE_REVEALING) {
            shownWin = Math.round(lastWin * easeOut(clamp(elapsed / 1250f)));
            if (!rewardPlayed && elapsed >= 120L) {
                rewardPlayed = true;
                if (lastWin > 0) {
                    StakeSlotEngine.LineWin win = featuredWin();
                    audio.playSymbolWin(win == null ? "" : win.symbol,
                            win == null ? 0 : win.count,
                            current == null ? 0d : current.payoutMultiplier());
                    haptics.symbolWin(win == null ? "" : win.symbol,
                            current == null ? 0d : current.payoutMultiplier());
                } else {
                    audio.playLose();
                }
            }
            if (elapsed >= revealDuration()) completeReveal(now);
            return;
        }

        if (phase == Phase.FEATURE_INTRO && elapsed >= (reducedMotion ? 1700L : 3600L)) {
            phase = Phase.FEATURE_READY;
            phaseStart = now;
            message = "Bonus activo · " + featureController.spinsRemaining() + " juegos restantes";
            saveAll();
        } else if (phase == Phase.FEATURE_READY && elapsed >= 900L) {
            startFreeSpin();
        } else if (phase == Phase.RETRIGGER && elapsed >= (reducedMotion ? 1300L : 2500L)) {
            phase = Phase.FEATURE_READY;
            phaseStart = now;
            message = "Retrigger aplicado · " + featureController.spinsRemaining() + " restantes";
            saveAll();
        } else if (phase == Phase.FEATURE_SUMMARY && elapsed >= (reducedMotion ? 1800L : 4200L)) {
            featureController.finishFeature();
            featureRepository.clear();
            phase = Phase.IDLE;
            phaseStart = now;
            message = "Bonus finalizado · total " + numbers.format(lastWin) + " CR";
            saveAll();
        }
    }

    private long revealDuration() {
        double multiplier = current == null ? 0d : current.payoutMultiplier();
        long value = multiplier >= 25d ? 3800L : multiplier >= 5d ? 3000L : 1900L;
        return reducedMotion ? Math.min(value, 1500L) : value;
    }

    private void startBaseSpin(String demoMode) {
        if (phase != Phase.IDLE) return;
        int totalBet = betPerLine * StakeSlotEngine.LINE_COUNT;
        boolean demo = demoMode != null;
        if (!demo && credits < totalBet) {
            message = "Saldo insuficiente · pulsa RESET";
            audio.playError();
            haptics.error();
            return;
        }
        if (!demo) credits -= totalBet;
        pending = demo ? createShowcase(demoMode, GameMode.BASE_GAME)
                : engine.spin(random, betPerLine, GameMode.BASE_GAME);
        beginSpin(Phase.BASE_SPINNING);
        message = pending.featureTrigger.triggered
                ? "Resultado fijado · posible Royal Feature"
                : "Resultado fijado · giro base";
    }

    private void startFreeSpin() {
        if (phase != Phase.FEATURE_READY || !featureController.isActive()) return;
        featureController.consumeNextSpin();
        pending = engine.spin(random, featureController.lockedBetPerLine(), GameMode.FREE_SPINS);
        beginSpin(Phase.FEATURE_SPINNING);
        message = "Juego gratis " + featureController.spinsPlayed()
                + " · quedan " + featureController.spinsRemaining();
        featureRepository.save(featureController.snapshot());
    }

    private void beginSpin(Phase spinPhase) {
        phase = spinPhase;
        phaseStart = SystemClock.uptimeMillis();
        lastWin = 0;
        shownWin = 0;
        payoutApplied = false;
        rewardPlayed = false;
        lastRetriggerAdded = 0;
        for (int i = 0; i < stopPlayed.length; i++) stopPlayed[i] = false;
        rounds++;
        audio.playSpinStart();
        haptics.tap();
        saveAll();
    }

    private void settleRound(long now) {
        if (pending == null) return;
        current = pending;
        pending = null;
        lastWin = current.totalPayout;
        shownWin = 0;
        if (!payoutApplied) {
            credits += lastWin;
            payoutApplied = true;
        }
        if (current.mode == GameMode.FREE_SPINS) {
            lastRetriggerAdded = featureController.settleSpin(lastWin, current.featureTrigger);
            featureRepository.save(featureController.snapshot());
            phase = Phase.FEATURE_REVEALING;
        } else {
            phase = Phase.BASE_REVEALING;
        }
        phaseStart = now;
        rewardPlayed = false;
        message = lastWin > 0 ? "Premio " + numbers.format(lastWin) + " CR" : "Sin premio";
        saveAll();
    }

    private void completeReveal(long now) {
        shownWin = lastWin;
        if (phase == Phase.BASE_REVEALING) {
            if (current != null && current.featureTrigger.isInitialFeature()) {
                featureController.beginFeature(current.featureTrigger, betPerLine);
                featureRepository.save(featureController.snapshot());
                phase = Phase.FEATURE_INTRO;
                phaseStart = now;
                message = "ROYAL FEATURE · 30 JUEGOS GRATIS";
                audio.playSymbolWin(StakeSlotEngine.WILD,
                        current.featureTrigger.highestWildCount, 100d);
                haptics.symbolWin(StakeSlotEngine.WILD, 100d);
            } else {
                phase = Phase.IDLE;
                phaseStart = now;
                message = lastWin > 0
                        ? "Ganaste " + numbers.format(lastWin) + " CR"
                        : "3 WILD en línea activan 30 juegos gratis";
            }
        } else if (phase == Phase.FEATURE_REVEALING) {
            if (lastRetriggerAdded > 0) {
                phase = Phase.RETRIGGER;
                phaseStart = now;
                message = "+" + lastRetriggerAdded + " JUEGOS GRATIS";
                audio.playSymbolWin(StakeSlotEngine.WILD, 3, 25d);
                haptics.symbolWin(StakeSlotEngine.WILD, 25d);
            } else if (featureController.shouldFinish()) {
                lastWin = featureController.totalFeatureWin();
                shownWin = lastWin;
                phase = Phase.FEATURE_SUMMARY;
                phaseStart = now;
                message = "BONUS TOTAL · " + numbers.format(lastWin) + " CR";
            } else {
                phase = Phase.FEATURE_READY;
                phaseStart = now;
                message = featureController.spinsRemaining() + " juegos gratis restantes";
            }
            featureRepository.save(featureController.snapshot());
        }
        saveAll();
    }

    private StakeSlotEngine.SpinResult createShowcase(String requested, GameMode mode) {
        String key = requested == null ? "FEATURE" : requested.trim().toUpperCase(Locale.US);
        String[][] board = new String[5][3];
        for (int reel = 0; reel < 5; reel++) {
            board[reel][0] = StakeSlotEngine.JACK;
            board[reel][1] = reel % 2 == 0 ? StakeSlotEngine.BELL : StakeSlotEngine.QUEEN;
            board[reel][2] = reel % 2 == 0 ? StakeSlotEngine.BAR : StakeSlotEngine.KING;
        }
        int wilds = key.contains("FIVE") ? 5 : key.contains("FOUR") ? 4 : 3;
        for (int reel = 0; reel < wilds; reel++) board[reel][0] = StakeSlotEngine.WILD;
        return engine.evaluate(board, new int[5], betPerLine, mode);
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
        drawControls(canvas, now);
        drawFeatureOverlay(canvas, now);
        canvas.restore();
    }

    private boolean featureVisualActive() {
        return featureController.isActive() || phase == Phase.FEATURE_INTRO
                || phase == Phase.RETRIGGER || phase == Phase.FEATURE_SUMMARY;
    }

    private void drawBackground(Canvas c, long now) {
        boolean feature = featureVisualActive();
        paint.setShader(new LinearGradient(0, 0, 0, H,
                feature
                        ? new int[]{0xFF01050E, 0xFF071D35, 0xFF18102F, 0xFF02040A}
                        : new int[]{0xFF010205, 0xFF0A0717, 0xFF180A18, 0xFF020205},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, paint);
        paint.setShader(null);
        int stars = reducedMotion ? 22 : 54;
        for (int i = 0; i < stars; i++) {
            float x = (i * 83.7f + (float)Math.sin(now * .00015f + i) * 22f + 400f) % W;
            float y = (i * 137.3f + (float)Math.cos(now * .00011f + i) * 30f + 900f) % H;
            int rgb = feature ? (i % 3 == 0 ? 0x58DFFF : 0xF6C453)
                    : (i % 3 == 0 ? 0x9C62FF : 0xF6C453);
            paint.setColor(((18 + i % 5 * 8) << 24) | rgb);
            c.drawCircle(x, y, i % 8 == 0 ? 1.7f : .8f, paint);
        }
        paint.setShader(new RadialGradient(180, 320, 280,
                new int[]{feature ? 0x3540CFFF : 0x354E2EFF, 0x00000000},
                null, Shader.TileMode.CLAMP));
        c.drawCircle(180, 320, 280, paint);
        paint.setShader(null);
    }

    private void drawHeader(Canvas c, long now) {
        float pulse = reducedMotion ? 1f : 1f + .045f * (float)Math.sin(now * .003f);
        c.save();
        c.scale(pulse, pulse, 180, 28);
        crown(c, 180, 28, 20, 0xFFF6C453);
        c.restore();
        goldText(c, "ROYAL SPIN", 180, 61, 25, Paint.Align.CENTER);
        text(c, "v1.6 · FREE SPINS FOUNDATION", 180, 79, 7,
                0xFFFFE7A0, true, Paint.Align.CENTER);
        button(c, 10, 21, 75, 51, "RESET", false, now);
        button(c, 285, 21, 350, 51, soundEnabled ? "SFX ON" : "SFX OFF", soundEnabled, now);

        panel(c, 14, 92, 346, 160, 18, 0xE9080B12, featureVisualActive() ? 0xFF55DFFF : 0xFF9D7427);
        text(c, "SALDO", 30, 114, 8, 0xFF9FA7B7, true, Paint.Align.LEFT);
        text(c, numbers.format(credits) + " CR", 30, 142, 21, Color.WHITE, true, Paint.Align.LEFT);
        text(c, "RTP BONUS", 231, 114, 8, 0xFF9FA7B7, true, Paint.Align.CENTER);
        text(c, "EN VALIDACIÓN", 231, 141, 11, 0xFFFFCF67, true, Paint.Align.CENTER);
        text(c, "RONDA " + rounds, 330, 142, 8, 0xFF9FA7B7, true, Paint.Align.RIGHT);

        if (featureController.isActive()) {
            panel(c, 22, 167, 338, 198, 14, 0xE90A1626, 0xFF58DFFF);
            text(c, "FREE SPINS", 38, 187, 8, 0xFF9FEFFF, true, Paint.Align.LEFT);
            text(c, String.valueOf(featureController.spinsRemaining()), 126, 188, 18,
                    0xFFFFDF75, true, Paint.Align.CENTER);
            text(c, "BONUS WIN", 180, 187, 8, 0xFF9FEFFF, true, Paint.Align.LEFT);
            text(c, numbers.format(featureController.totalFeatureWin()) + " CR", 326, 188,
                    15, Color.WHITE, true, Paint.Align.RIGHT);
        }
    }

    private void drawMachine(Canvas c, long now) {
        float top = featureController.isActive() ? REEL_TOP : REEL_TOP - 25f;
        float shift = top - REEL_TOP;
        paint.setShadowLayer(24, 0, 10, 0xCC000000);
        paint.setShader(new LinearGradient(9, top - 20, 351, top + 270,
                new int[]{0xFF241305, 0xFFFFE291, 0xFF875211, 0xFFFFD36A, 0xFF211205},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(9, top - 20, 351, top + CELL_H * 3 + 31), 28, 28, paint);
        paint.clearShadowLayer();
        paint.setShader(null);
        panel(c, 14, top - 14, 346, top + CELL_H * 3 + 25, 23, 0xFF05070C, 0xFFFFD76A);

        StakeSlotEngine.SpinResult source = pending != null ? pending : current;
        long elapsed = isSpinning() ? now - phaseStart : Long.MAX_VALUE;
        for (int reel = 0; reel < 5; reel++) {
            float left = REEL_LEFT + reel * (REEL_W + REEL_GAP);
            RectF clip = new RectF(left, top, left + REEL_W, top + CELL_H * 3);
            c.save();
            path.reset();
            path.addRoundRect(clip, 8, 8, Path.Direction.CW);
            c.clipPath(path);
            if (isSpinning() && elapsed < STOPS[reel]) {
                drawSpinningReel(c, reel, left, top, elapsed);
            } else {
                drawStoppedReel(c, source, reel, left, top, now);
            }
            drawGlass(c, clip, now, reel);
            c.restore();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(reel == 2 ? 2.2f : 1.1f);
            paint.setColor(reel == 2 ? 0xFFFFD76A : 0xFF8E8A7A);
            c.drawRoundRect(clip, 8, 8, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        text(c, featureController.isActive()
                        ? "MODO BONUS · APUESTA BLOQUEADA " + featureController.lockedBetPerLine() * 20 + " CR"
                        : "20 LÍNEAS · 3 WILD = 30 JUEGOS GRATIS",
                180, top + CELL_H * 3 + 17, 7, 0xFFBBC0CA, true, Paint.Align.CENTER);
    }

    private boolean isSpinning() {
        return phase == Phase.BASE_SPINNING || phase == Phase.FEATURE_SPINNING;
    }

    private void drawSpinningReel(Canvas c, int reel, float left, float top, long elapsed) {
        float travel = (elapsed * (1.15f + reel * .08f)) % CELL_H;
        int base = (int)(elapsed / 55L) + reel * 7;
        for (int item = -2; item <= 4; item++) {
            String symbol = StakeSlotEngine.SYMBOLS[Math.floorMod(base + item,
                    StakeSlotEngine.SYMBOLS.length)];
            float y = top + item * CELL_H + travel;
            float center = y + CELL_H / 2f;
            float normalized = Math.abs(center - (top + CELL_H * 1.5f)) / (CELL_H * 1.5f);
            float sy = .72f + .28f * (1f - Math.min(1f, normalized));
            c.save();
            c.scale(1f, sy, left + REEL_W / 2f, center);
            drawSymbol(c, left + 2, y + 2, REEL_W - 4, CELL_H - 4, symbol, false, elapsed);
            c.restore();
        }
        for (int i = 0; i < 7; i++) {
            float y = top + ((elapsed * (1.4f + i * .11f) + i * 37f) % (CELL_H * 3));
            paint.setColor((45 + i * 6) << 24 | 0x00FFFFFF);
            c.drawRoundRect(new RectF(left + 5, y, left + REEL_W - 5, y + 2.4f), 2, 2, paint);
        }
    }

    private void drawStoppedReel(Canvas c, StakeSlotEngine.SpinResult result, int reel,
                                 float left, float top, long now) {
        if (result == null) return;
        for (int row = 0; row < 3; row++) {
            drawSymbol(c, left + 2, top + row * CELL_H + 2, REEL_W - 4, CELL_H - 4,
                    result.board[reel][row], winningCell(reel, row, now), now);
        }
    }

    private void drawSymbol(Canvas c, float left, float top, float width, float height,
                            String symbol, boolean winning, long now) {
        int color = StakeSlotEngine.symbolColor(symbol);
        float cx = left + width / 2f;
        float cy = top + height / 2f;
        c.save();
        if (winning && !reducedMotion) {
            float wave = .5f + .5f * (float)Math.sin((now - phaseStart) * .014f);
            c.scale(1f + wave * .08f, 1f + wave * .08f, cx, cy);
            c.rotate((float)Math.sin((now - phaseStart) * .018f) * 4f, cx, cy);
        }
        if (winning) {
            paint.setColor(0x55000000 | (color & 0x00FFFFFF));
            c.drawRoundRect(new RectF(left - 3, top - 3, left + width + 3, top + height + 3), 11, 11, paint);
        }
        paint.setShader(new LinearGradient(left, top, left, top + height,
                new int[]{0xFF5B554B, 0xFF17191E, 0xFF07080A, 0xFF41382D},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(left, top, left + width, top + height), 8, 8, paint);
        paint.setShader(null);
        paint.setShadowLayer(winning ? 12 : 4, 0, 2, winning ? color : 0x99000000);
        if (StakeSlotEngine.WILD.equals(symbol)) {
            crown(c, cx, cy - 5, 23, color);
            text(c, "WILD", cx, top + height - 9, 7.5f, 0xFFFFE7A0, true, Paint.Align.CENTER);
        } else if (StakeSlotEngine.DIAMOND.equals(symbol)) {
            diamond(c, cx, cy, 20, color);
        } else if (StakeSlotEngine.BELL.equals(symbol)) {
            bell(c, cx, cy, 21, color);
        } else if (StakeSlotEngine.BAR.equals(symbol)) {
            panel(c, left + 8, cy - 13, left + width - 8, cy + 13, 5, 0xFFE7E1D2, 0xFFFFD76A);
            text(c, "BAR", cx, cy + 5, 14, 0xFF151515, true, Paint.Align.CENTER);
        } else {
            String label = StakeSlotEngine.displayLabel(symbol);
            text(c, label, cx, cy + 12, label.length() > 1 ? 22 : 34,
                    color, true, Paint.Align.CENTER);
        }
        paint.clearShadowLayer();
        c.restore();
    }

    private void drawGlass(Canvas c, RectF clip, long now, int reel) {
        paint.setShader(new LinearGradient(0, clip.top, 0, clip.bottom,
                new int[]{0xCC000000, 0x10000000, 0x00000000, 0x10000000, 0xCC000000},
                new float[]{0f, .16f, .5f, .84f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(clip, paint);
        paint.setShader(null);
        float x = clip.left + ((now * (.025f + reel * .002f) + reel * 23f) % REEL_W);
        paint.setShader(new LinearGradient(x - 10, clip.top, x + 10, clip.bottom,
                new int[]{0x00FFFFFF, 0x32FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRect(clip, paint);
        paint.setShader(null);
    }

    private boolean winningCell(int reel, int row, long now) {
        if ((phase != Phase.BASE_REVEALING && phase != Phase.FEATURE_REVEALING)
                || current == null || current.lineWins.isEmpty()) return false;
        StakeSlotEngine.LineWin win = current.lineWins.get((int)(((now - phaseStart) / 700L)
                % current.lineWins.size()));
        return reel < win.count && win.rows[reel] == row;
    }

    private StakeSlotEngine.LineWin featuredWin() {
        return current == null ? null : SymbolAnimationDirector.primaryWin(current.lineWins);
    }

    private void drawControls(Canvas c, long now) {
        panel(c, 15, 510, 345, 675, 22, 0xF0080B12, featureVisualActive() ? 0xFF397E9D : 0xFF73551E);
        if (!featureController.isActive()) {
            text(c, "APUESTA POR LÍNEA", 180, 535, 9, 0xFFADB3C0, true, Paint.Align.CENTER);
            button(c, 27, 548, 83, 600, "−", false, now);
            panel(c, 101, 548, 259, 600, 17, 0xFF090B10, 0xFFC58B29);
            goldText(c, betPerLine + " CR", 180, 583, 22, Paint.Align.CENTER);
            button(c, 277, 548, 333, 600, "+", false, now);
            text(c, "APUESTA TOTAL", 35, 628, 8, 0xFF9AA2B2, true, Paint.Align.LEFT);
            text(c, numbers.format(betPerLine * 20) + " CR", 35, 653, 16,
                    Color.WHITE, true, Paint.Align.LEFT);
        } else {
            text(c, "ROYAL FREE SPINS", 180, 542, 14, 0xFFFFDF75, true, Paint.Align.CENTER);
            text(c, "TIRADAS JUGADAS", 35, 580, 8, 0xFF9FEFFF, true, Paint.Align.LEFT);
            text(c, String.valueOf(featureController.spinsPlayed()), 35, 606, 18,
                    Color.WHITE, true, Paint.Align.LEFT);
            text(c, "RETRIGGERS", 325, 580, 8, 0xFF9FEFFF, true, Paint.Align.RIGHT);
            text(c, String.valueOf(featureController.snapshot().retriggerCount), 325, 606, 18,
                    Color.WHITE, true, Paint.Align.RIGHT);
            text(c, "APUESTA BLOQUEADA", 180, 635, 8, 0xFF9FEFFF, true, Paint.Align.CENTER);
            text(c, numbers.format(featureController.lockedBetPerLine() * 20) + " CR",
                    180, 658, 15, 0xFFFFDF75, true, Paint.Align.CENTER);
        }
        text(c, message, 180, 698, 9.3f, 0xFFE4E7EE, true, Paint.Align.CENTER);
        drawMainButton(c, now);
        text(c, "FPS " + fps + " · matemática separada · créditos ficticios",
                180, 790, 6.8f, 0xFF7D8492, false, Paint.Align.CENTER);
    }

    private void drawMainButton(Canvas c, long now) {
        boolean ready = phase == Phase.IDLE || phase == Phase.FEATURE_READY;
        float pulse = ready && !reducedMotion ? .5f + .5f * (float)Math.sin(now * .0045f) : 0f;
        float press = buttonPressed ? .955f : 1f;
        c.save();
        c.scale(press, press, 180, 742);
        paint.setShadowLayer(13 + pulse * 9, 0, 5, ready ? 0xAAF6C453 : 0xAA5A48B0);
        paint.setShader(new LinearGradient(34, 713, 326, 768,
                ready
                        ? new int[]{0xFFFFF1A8, 0xFFF6B83F, 0xFFFFD96C}
                        : new int[]{0xFF8B65D8, 0xFF513381, 0xFF8B65D8},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(34, 713, 326, 768), 28, 28, paint);
        paint.clearShadowLayer();
        paint.setShader(null);
        String label = phase == Phase.IDLE ? "GIRAR"
                : phase == Phase.FEATURE_READY ? "GIRO GRATIS"
                : "OMITIR ANIMACIÓN";
        text(c, label, 180, 749, ready ? 18 : 13.5f,
                ready ? 0xFF1B1003 : Color.WHITE, true, Paint.Align.CENTER);
        c.restore();
    }

    private void drawFeatureOverlay(Canvas c, long now) {
        long elapsed = now - phaseStart;
        if (phase != Phase.FEATURE_INTRO && phase != Phase.RETRIGGER
                && phase != Phase.FEATURE_SUMMARY) return;
        paint.setColor(0xC9000000);
        c.drawRect(0, 0, W, H, paint);
        float enter = easeOut(clamp(elapsed / 650f));
        float energy = .5f + .5f * (float)Math.sin(elapsed * .012f);
        int rays = reducedMotion ? 8 : 20;
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < rays; i++) {
            double angle = i * Math.PI * 2 / rays + elapsed * .0007;
            float inner = 60f;
            float outer = 150f + energy * 35f;
            paint.setStrokeWidth(i % 2 == 0 ? 2.2f : 1f);
            paint.setColor(i % 2 == 0 ? 0xAAFFD76A : 0x7758DFFF);
            c.drawLine(180 + (float)Math.cos(angle) * inner,
                    310 + (float)Math.sin(angle) * inner,
                    180 + (float)Math.cos(angle) * outer,
                    310 + (float)Math.sin(angle) * outer, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        c.save();
        c.scale(.75f + .25f * enter, .75f + .25f * enter, 180, 255);
        crown(c, 180, 255, 62, 0xFFFFD76A);
        c.restore();

        if (phase == Phase.FEATURE_INTRO) {
            goldText(c, "ROYAL FEATURE", 180, 370, 28, Paint.Align.CENTER);
            int count = Math.min(FeatureRules.INITIAL_FREE_SPINS,
                    Math.round(FeatureRules.INITIAL_FREE_SPINS * clamp(elapsed / 1900f)));
            text(c, String.valueOf(count), 180, 445, 62, Color.WHITE, true, Paint.Align.CENTER);
            text(c, "JUEGOS GRATIS", 180, 485, 18, 0xFFFFDF75, true, Paint.Align.CENTER);
            text(c, "3+ WILD EN LÍNEA", 180, 520, 9, 0xFFBFEFFF, true, Paint.Align.CENTER);
        } else if (phase == Phase.RETRIGGER) {
            goldText(c, "WILD RETRIGGER", 180, 380, 27, Paint.Align.CENTER);
            text(c, "+" + lastRetriggerAdded, 180, 452, 64, Color.WHITE, true, Paint.Align.CENTER);
            text(c, "JUEGOS GRATIS", 180, 490, 18, 0xFFFFDF75, true, Paint.Align.CENTER);
        } else {
            goldText(c, "BONUS COMPLETADO", 180, 382, 25, Paint.Align.CENTER);
            text(c, numbers.format(featureController.totalFeatureWin()) + " CR",
                    180, 458, 42, Color.WHITE, true, Paint.Align.CENTER);
            text(c, "TOTAL GANADO EN FREE SPINS", 180, 500, 10,
                    0xFFBFEFFF, true, Paint.Align.CENTER);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = (event.getX() - offsetX) / Math.max(.0001f, scale);
        float y = (event.getY() - offsetY) / Math.max(.0001f, scale);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            buttonPressed = y > 700;
            invalidate();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            buttonPressed = false;
            return true;
        }
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        buttonPressed = false;

        if (x > 280 && y < 65) {
            soundEnabled = !soundEnabled;
            audio.setEnabled(soundEnabled);
            saveAll();
            return true;
        }
        if (x < 82 && y < 65 && phase == Phase.IDLE) {
            credits = 5000;
            rounds = lastWin = shownWin = 0;
            message = "Saldo demo reiniciado";
            saveAll();
            return true;
        }
        if (phase == Phase.IDLE) {
            if (y > 535 && y < 615 && x < 100) {
                betPerLine = StakeSlotEngine.clampBet(betPerLine - 1);
                audio.playTap();
                saveAll();
            } else if (y > 535 && y < 615 && x > 260) {
                betPerLine = StakeSlotEngine.clampBet(betPerLine + 1);
                audio.playTap();
                saveAll();
            } else if (y > 700) {
                startBaseSpin(null);
            }
        } else if (phase == Phase.FEATURE_READY && y > 700) {
            startFreeSpin();
        } else if (y > 700) {
            skipAnimation();
        }
        return true;
    }

    private void skipAnimation() {
        long now = SystemClock.uptimeMillis();
        if (phase == Phase.BASE_SPINNING || phase == Phase.FEATURE_SPINNING) settleRound(now);
        if (phase == Phase.BASE_REVEALING || phase == Phase.FEATURE_REVEALING) completeReveal(now);
        if (phase == Phase.FEATURE_INTRO) {
            phase = Phase.FEATURE_READY;
            phaseStart = now;
        } else if (phase == Phase.RETRIGGER) {
            phase = Phase.FEATURE_READY;
            phaseStart = now;
        } else if (phase == Phase.FEATURE_SUMMARY) {
            featureController.finishFeature();
            featureRepository.clear();
            phase = Phase.IDLE;
            phaseStart = now;
        }
        audio.stopRewardSequence();
        haptics.cancel();
        saveAll();
    }

    private void saveAll() {
        prefs.edit()
                .putInt("credits", credits)
                .putInt("bet", betPerLine)
                .putInt("rounds", rounds)
                .putBoolean("sound", soundEnabled)
                .putBoolean("reduced_motion", reducedMotion)
                .apply();
        if (featureController.isActive()) featureRepository.save(featureController.snapshot());
    }

    public void onHostPause() {
        if (phase == Phase.BASE_SPINNING || phase == Phase.FEATURE_SPINNING
                || phase == Phase.BASE_REVEALING || phase == Phase.FEATURE_REVEALING) {
            skipAnimation();
        }
        saveAll();
    }

    public void release() {
        frameLoop = false;
        Choreographer.getInstance().removeFrameCallback(this);
        audio.release();
        haptics.release();
    }

    private void panel(Canvas c, float l, float t, float r, float b, float radius,
                       int fill, int line) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.25f);
        paint.setColor(line);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void button(Canvas c, float l, float t, float r, float b, String label,
                        boolean active, long now) {
        float pulse = active && !reducedMotion ? .5f + .5f * (float)Math.sin(now * .005f) : 0f;
        paint.setShadowLayer(6 + pulse * 4, 0, 3, active ? 0x99F6C453 : 0x66000000);
        paint.setShader(new LinearGradient(l, t, r, b,
                active ? new int[]{0xFF3A2308, 0xFF8A5918, 0xFF3A2308}
                        : new int[]{0xFF262B38, 0xFF11151E, 0xFF262B38},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(l, t, r, b), (b - t) / 2f, (b - t) / 2f, paint);
        paint.clearShadowLayer();
        paint.setShader(null);
        text(c, label, (l + r) / 2f, (t + b) / 2f + 3,
                label.length() > 6 ? 7 : 9,
                active ? 0xFFFFE39A : 0xFFE7E9EF, true, Paint.Align.CENTER);
    }

    private void goldText(Canvas c, String value, float x, float y, float size, Paint.Align align) {
        paint.setShader(new LinearGradient(x - size * 2, y - size, x + size * 2, y,
                new int[]{0xFFC78320, 0xFFFFF0AA, 0xFFF6B83F, 0xFFFFE58B},
                null, Shader.TileMode.MIRROR));
        text(c, value, x, y, size, Color.WHITE, true, align);
        paint.setShader(null);
    }

    private void text(Canvas c, String value, float x, float y, float size, int color,
                      boolean bold, Paint.Align align) {
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTextAlign(align);
        paint.setTypeface(bold ? Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                : Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        c.drawText(value, x, y, paint);
    }

    private void crown(Canvas c, float cx, float cy, float size, int color) {
        path.reset();
        path.moveTo(cx - size * .55f, cy + size * .32f);
        path.lineTo(cx - size * .48f, cy - size * .28f);
        path.lineTo(cx - size * .14f, cy + size * .02f);
        path.lineTo(cx, cy - size * .5f);
        path.lineTo(cx + size * .16f, cy + size * .02f);
        path.lineTo(cx + size * .5f, cy - size * .28f);
        path.lineTo(cx + size * .55f, cy + size * .32f);
        path.close();
        paint.setColor(color);
        c.drawPath(path, paint);
        c.drawRoundRect(new RectF(cx - size * .57f, cy + size * .36f,
                cx + size * .57f, cy + size * .48f), size * .05f, size * .05f, paint);
    }

    private void diamond(Canvas c, float cx, float cy, float size, int color) {
        path.reset();
        path.moveTo(cx, cy - size);
        path.lineTo(cx + size * .78f, cy - size * .22f);
        path.lineTo(cx + size * .48f, cy + size);
        path.lineTo(cx - size * .48f, cy + size);
        path.lineTo(cx - size * .78f, cy - size * .22f);
        path.close();
        paint.setShader(new LinearGradient(cx - size, cy - size, cx + size, cy + size,
                new int[]{0xFFFFFFFF, color, 0xFF3977FF}, null, Shader.TileMode.CLAMP));
        c.drawPath(path, paint);
        paint.setShader(null);
    }

    private void bell(Canvas c, float cx, float cy, float size, int color) {
        paint.setColor(color);
        RectF body = new RectF(cx - size * .65f, cy - size * .58f,
                cx + size * .65f, cy + size * .55f);
        c.drawArc(body, 180, 180, true, paint);
        c.drawRoundRect(new RectF(cx - size * .75f, cy + size * .35f,
                cx + size * .75f, cy + size * .58f), size * .08f, size * .08f, paint);
        paint.setColor(0xFF8D5510);
        c.drawCircle(cx, cy + size * .72f, size * .16f, paint);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float easeOut(float value) {
        float x = clamp(value);
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }
}
