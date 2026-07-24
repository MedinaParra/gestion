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
import java.util.concurrent.atomic.AtomicLong;

/** Royal Spin 2.0: complete free-spin lifecycle and advanced audiovisual presentation. */
public final class RoyalSpinV2View extends View implements Choreographer.FrameCallback {
    private enum Phase {
        IDLE, BASE_SPINNING, BASE_REVEALING,
        FEATURE_INTRO, FEATURE_READY, FEATURE_SPINNING, FEATURE_REVEALING,
        RETRIGGER, FEATURE_SUMMARY
    }

    private static final AtomicLong ROUND_IDS = new AtomicLong(2_026_072_400_000L);
    private static final float W = 360f, H = 800f;
    private static final float REEL_LEFT = 20f, REEL_TOP = 202f;
    private static final float REEL_W = 60f, REEL_GAP = 4f, CELL_H = 78f;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final StakeSlotEngine engine = new StakeSlotEngine();
    private final Random random = new Random();
    private final SharedPreferences prefs;
    private final FeatureSessionRepository featureRepository;
    private final FeatureSessionController featureController;
    private final RoundTransactionRepository roundRepository;
    private final CinematicAudioDirector audio;
    private final HapticEngine haptics;
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(new Locale("es", "CL"));
    private final boolean[] stopPlayed = new boolean[5];
    private final long[] stopTimes = new long[5];

    private StakeSlotEngine.SpinResult current;
    private StakeSlotEngine.SpinResult pending;
    private RoundTransactionRepository.Record roundRecord;
    private Phase phase;
    private long phaseStart;
    private long previousFrame;
    private long fpsStart;
    private int fpsFrames;
    private int fps = 60;
    private int slowFrames;
    private int totalFrames;
    private int qualityTier = 2;
    private float scale = 1f, offsetX, offsetY;
    private int credits;
    private int betPerLine;
    private int rounds;
    private int lastWin;
    private int shownWin;
    private int lastRetriggerAdded;
    private boolean rewardPlayed;
    private boolean soundEnabled;
    private boolean hapticEnabled;
    private boolean reducedMotion;
    private boolean frameLoop;
    private boolean buttonPressed;
    private boolean anticipation;
    private String message;

    public RoyalSpinV2View(Context context, String demoMode) {
        super(context);
        prefs = context.getSharedPreferences("royal_spin_feature_v16", Context.MODE_PRIVATE);
        featureRepository = new FeatureSessionRepository(prefs);
        featureController = new FeatureSessionController(featureRepository.load());
        roundRepository = new RoundTransactionRepository(prefs);
        credits = prefs.getInt("credits", 5000);
        betPerLine = StakeSlotEngine.clampBet(prefs.getInt("bet", 1));
        rounds = prefs.getInt("rounds", 0);
        soundEnabled = prefs.getBoolean("sound", true);
        hapticEnabled = prefs.getBoolean("haptic", true);
        reducedMotion = prefs.getBoolean("reduced_motion", false);
        audio = new CinematicAudioDirector(soundEnabled);
        haptics = new HapticEngine(context, hapticEnabled);
        current = engine.spin(new Random(20260724L), betPerLine, GameMode.BASE_GAME);
        phase = featureController.isActive() ? Phase.FEATURE_READY : Phase.IDLE;
        message = featureController.isActive()
                ? "Sesión recuperada · continúa tus juegos gratis"
                : "3 WILD en línea activan 30 juegos gratis";
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        setFocusable(true);
        setContentDescription("Royal Spin 2.0, tragamonedas demostrativa con créditos ficticios");
        recoverPersistedRound();

        if (demoMode != null && !demoMode.isEmpty()) {
            String key = demoMode.toLowerCase(Locale.US);
            if (key.contains("retrigger") || key.contains("bonus")) {
                if (!featureController.isActive()) {
                    featureController.beginFeature(new FeatureTrigger(true, 3, 30, 0,
                            java.util.Collections.singletonList(0)), betPerLine,
                            ROUND_IDS.incrementAndGet());
                    featureRepository.save(featureController.snapshot());
                    phase = Phase.FEATURE_READY;
                }
                postDelayed(() -> startFreeSpin(key), 650L);
            } else {
                postDelayed(() -> startBaseSpin(key), 650L);
            }
        }
    }

    private void recoverPersistedRound() {
        RoundTransactionRepository.Record record = roundRepository.load();
        if (record == null) return;
        StakeSlotEngine.SpinResult restored = roundRepository.restoreResult(engine, record);
        if (restored == null) {
            roundRepository.clear();
            return;
        }
        roundRecord = record;
        current = restored;
        lastWin = restored.totalPayout;
        shownWin = 0;
        if (!roundRecord.payoutCommitted) {
            credits += restored.totalPayout;
            roundRecord = roundRepository.commitPayout(roundRecord, credits);
        }
        if (restored.mode == GameMode.FREE_SPINS && !roundRecord.featureSettled
                && featureController.isActive()) {
            lastRetriggerAdded = featureController.settleSpin(roundRecord.roundId,
                    restored.totalPayout, restored.featureTrigger);
            featureRepository.save(featureController.snapshot());
            roundRecord = roundRepository.markFeatureSettled(roundRecord);
        }
        phase = restored.mode == GameMode.FREE_SPINS
                ? Phase.FEATURE_REVEALING : Phase.BASE_REVEALING;
        phaseStart = SystemClock.uptimeMillis();
        rewardPlayed = false;
        message = "Ronda recuperada y liquidada de forma segura";
        savePreferences();
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
            totalFrames++;
            if (delta > 22_000_000L) slowFrames++;
        }
        previousFrame = frameTimeNanos;
        if (fpsStart == 0L) fpsStart = frameTimeNanos;
        fpsFrames++;
        if (frameTimeNanos - fpsStart >= 1_000_000_000L) {
            fps = Math.max(1, fpsFrames);
            float slow = totalFrames == 0 ? 0f : slowFrames / (float) totalFrames;
            qualityTier = fps < 40 || slow > .28f ? 0 : fps < 53 || slow > .14f ? 1 : 2;
            fpsFrames = 0;
            fpsStart = frameTimeNanos;
        }
        update(SystemClock.uptimeMillis());
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void update(long now) {
        long elapsed = now - phaseStart;
        if (isSpinning()) {
            for (int reel = 0; reel < 5; reel++) {
                if (!stopPlayed[reel] && elapsed >= stopTimes[reel]) {
                    stopPlayed[reel] = true;
                    audio.playReelStop(reel);
                    haptics.reelStop(reel);
                }
            }
            if (elapsed >= stopTimes[4] + 190L) settleRound(now);
            return;
        }
        if (isRevealing()) {
            shownWin = Math.round(lastWin * easeOut(clamp(elapsed / 1450f)));
            if (!rewardPlayed && elapsed >= 100L) {
                rewardPlayed = true;
                StakeSlotEngine.LineWin win = featuredWin();
                if (lastWin > 0) {
                    audio.playSymbolWin(win == null ? "" : win.symbol,
                            win == null ? 0 : win.count, currentMultiplier());
                    haptics.symbolWin(win == null ? "" : win.symbol, currentMultiplier());
                } else {
                    audio.playLose();
                }
            }
            if (elapsed >= revealDuration()) completeReveal(now);
            return;
        }
        if (phase == Phase.FEATURE_INTRO && elapsed >= (reducedMotion ? 1700L : 5200L)) {
            phase = Phase.FEATURE_READY;
            phaseStart = now;
            message = "Royal Feature activo · " + featureController.spinsRemaining() + " restantes";
            saveAll();
        } else if (phase == Phase.FEATURE_READY && elapsed >= 850L) {
            startFreeSpin(null);
        } else if (phase == Phase.RETRIGGER && elapsed >= (reducedMotion ? 1300L : 3600L)) {
            phase = Phase.FEATURE_READY;
            phaseStart = now;
            message = "Retrigger aplicado · " + featureController.spinsRemaining() + " restantes";
            saveAll();
        } else if (phase == Phase.FEATURE_SUMMARY && elapsed >= (reducedMotion ? 1900L : 5600L)) {
            featureController.finishFeature();
            featureRepository.clear();
            phase = Phase.IDLE;
            phaseStart = now;
            message = "Bonus finalizado · resultado cerrado";
            saveAll();
        }
    }

    private long revealDuration() {
        if (lastWin <= 0) return 850L;
        double multiplier = currentMultiplier();
        long value = multiplier >= 50d ? 6200L : multiplier >= 15d ? 5000L
                : multiplier >= 5d ? 3900L : 2450L;
        return reducedMotion ? Math.min(value, 1900L) : value;
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
        StakeSlotEngine.SpinResult result = demo
                ? createShowcase(demoMode, GameMode.BASE_GAME)
                : engine.spin(random, betPerLine, GameMode.BASE_GAME);
        int afterDebit = demo ? credits : credits - totalBet;
        long id = ROUND_IDS.incrementAndGet();
        roundRecord = roundRepository.begin(id, result, afterDebit, !demo);
        credits = afterDebit;
        pending = result;
        beginSpin(Phase.BASE_SPINNING);
        message = result.featureTrigger.triggered
                ? "Resultado fijado · Royal Feature detectado"
                : "Resultado fijado · giro base";
    }

    private void startFreeSpin(String demoMode) {
        if (phase != Phase.FEATURE_READY || !featureController.isActive()) return;
        featureController.consumeNextSpin();
        featureRepository.save(featureController.snapshot());
        StakeSlotEngine.SpinResult result = demoMode == null
                ? engine.spin(random, featureController.lockedBetPerLine(), GameMode.FREE_SPINS)
                : createShowcase(demoMode, GameMode.FREE_SPINS);
        long id = ROUND_IDS.incrementAndGet();
        roundRecord = roundRepository.begin(id, result, credits, false);
        pending = result;
        beginSpin(Phase.FEATURE_SPINNING);
        message = "Juego gratis " + featureController.spinsPlayed()
                + " · quedan " + featureController.spinsRemaining();
    }

    private void beginSpin(Phase spinPhase) {
        phase = spinPhase;
        phaseStart = SystemClock.uptimeMillis();
        lastWin = shownWin = 0;
        lastRetriggerAdded = 0;
        rewardPlayed = false;
        anticipation = pending != null && (pending.featureTrigger.triggered
                || PremiumReelDynamics.shouldAnticipate(pending));
        for (int reel = 0; reel < 5; reel++) {
            stopTimes[reel] = PremiumReelDynamics.stopTime(reel, anticipation, reducedMotion);
            stopPlayed[reel] = false;
        }
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
        if (roundRecord != null && !roundRecord.payoutCommitted) {
            credits += lastWin;
            roundRecord = roundRepository.commitPayout(roundRecord, credits);
        }
        if (current.mode == GameMode.FREE_SPINS) {
            if (roundRecord != null && !roundRecord.featureSettled) {
                lastRetriggerAdded = featureController.settleSpin(roundRecord.roundId,
                        lastWin, current.featureTrigger);
                featureRepository.save(featureController.snapshot());
                roundRecord = roundRepository.markFeatureSettled(roundRecord);
            }
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
                long sessionId = roundRecord == null ? ROUND_IDS.incrementAndGet() : roundRecord.roundId;
                featureController.beginFeature(current.featureTrigger, betPerLine, sessionId);
                featureRepository.save(featureController.snapshot());
                phase = Phase.FEATURE_INTRO;
                phaseStart = now;
                message = "ROYAL FEATURE · 30 JUEGOS GRATIS";
                audio.playFeatureIntro(current.featureTrigger.highestWildCount);
                haptics.symbolWin(StakeSlotEngine.WILD, 100d);
            } else {
                phase = Phase.IDLE;
                phaseStart = now;
                message = lastWin > 0 ? "Ganaste " + numbers.format(lastWin) + " CR"
                        : "3 WILD en línea activan 30 juegos gratis";
            }
        } else if (phase == Phase.FEATURE_REVEALING) {
            if (lastRetriggerAdded > 0) {
                phase = Phase.RETRIGGER;
                phaseStart = now;
                message = "+" + lastRetriggerAdded + " JUEGOS GRATIS";
                audio.playRetrigger(lastRetriggerAdded);
                haptics.symbolWin(StakeSlotEngine.WILD, 25d);
            } else if (featureController.shouldFinish()) {
                lastWin = featureController.totalFeatureWin();
                shownWin = lastWin;
                phase = Phase.FEATURE_SUMMARY;
                phaseStart = now;
                message = "BONUS TOTAL · " + numbers.format(lastWin) + " CR";
                double ratio = lastWin / (double) Math.max(1,
                        featureController.lockedBetPerLine() * StakeSlotEngine.LINE_COUNT);
                audio.playFeatureSummary(ratio);
            } else {
                phase = Phase.FEATURE_READY;
                phaseStart = now;
                message = featureController.spinsRemaining() + " juegos gratis restantes";
            }
            featureRepository.save(featureController.snapshot());
        }
        if (roundRecord != null) {
            roundRepository.complete(roundRecord);
            roundRecord = null;
        }
        saveAll();
    }

    private StakeSlotEngine.SpinResult createShowcase(String requested, GameMode mode) {
        String key = requested == null ? "FEATURE" : requested.trim().toUpperCase(Locale.US);
        String symbol = StakeSlotEngine.WILD;
        if (key.contains("BELL") || key.contains("CAMPANA")) symbol = StakeSlotEngine.BELL;
        else if (key.contains("BAR")) symbol = StakeSlotEngine.BAR;
        else if (key.contains("SEVEN") || key.equals("7")) symbol = StakeSlotEngine.SEVEN;
        else if (key.contains("DIAMOND") || key.contains("GEMA")) symbol = StakeSlotEngine.DIAMOND;
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
        if (StakeSlotEngine.WILD.equals(symbol)) {
            int wilds = key.contains("FIVE") ? 5 : key.contains("FOUR") ? 4 : 3;
            for (int reel = wilds; reel < 5; reel++) board[reel][0] = middle[reel];
        }
        return engine.evaluate(board, new int[5],
                mode == GameMode.FREE_SPINS ? featureController.lockedBetPerLine() : betPerLine,
                mode);
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

        float zoom = isRevealing() ? RoyalVfxDirector.cameraZoom(currentMultiplier(),
                now - phaseStart, reducedMotion) : 1f;
        float shake = isRevealing() ? RoyalVfxDirector.cameraShake(currentMultiplier(),
                now - phaseStart, reducedMotion) : 0f;
        canvas.save();
        canvas.translate(shake, shake * .35f);
        canvas.scale(zoom, zoom, 180, 345);
        drawMachine(canvas, now);
        drawAdvancedWinVfx(canvas, now);
        canvas.restore();

        drawControls(canvas, now);
        drawSceneOverlay(canvas, now);
        canvas.restore();
    }

    private boolean featureVisualActive() {
        return featureController.isActive() || phase == Phase.FEATURE_INTRO
                || phase == Phase.RETRIGGER || phase == Phase.FEATURE_SUMMARY;
    }

    private void drawBackground(Canvas c, long now) {
        boolean feature = featureVisualActive();
        p.setShader(new LinearGradient(0, 0, 0, H,
                feature ? new int[]{0xFF01050E, 0xFF072441, 0xFF1A1038, 0xFF02040A}
                        : new int[]{0xFF010205, 0xFF0A0717, 0xFF180A18, 0xFF020205},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
        int stars = reducedMotion ? 20 : qualityTier == 2 ? 68 : qualityTier == 1 ? 42 : 24;
        for (int i = 0; i < stars; i++) {
            float x = (i * 83.7f + (float) Math.sin(now * .00015f + i) * 22f + 400f) % W;
            float y = (i * 137.3f + (float) Math.cos(now * .00011f + i) * 30f + 900f) % H;
            int rgb = feature ? (i % 3 == 0 ? 0x58DFFF : 0xF6C453)
                    : (i % 3 == 0 ? 0x9C62FF : 0xF6C453);
            p.setColor(((18 + i % 5 * 8) << 24) | rgb);
            c.drawCircle(x, y, i % 8 == 0 ? 1.7f : .8f, p);
        }
        p.setShader(new RadialGradient(180, 320, 290,
                new int[]{feature ? 0x3C40CFFF : 0x354E2EFF, 0x00000000},
                null, Shader.TileMode.CLAMP));
        c.drawCircle(180, 320, 290, p);
        p.setShader(null);
    }

    private void drawHeader(Canvas c, long now) {
        float pulse = reducedMotion ? 1f : 1f + .045f * (float) Math.sin(now * .003f);
        c.save();
        c.scale(pulse, pulse, 180, 28);
        crown(c, 180, 28, 20, 0xFFF6C453);
        c.restore();
        goldText(c, "ROYAL SPIN", 180, 61, 25, Paint.Align.CENTER);
        text(c, "v2.0 · ROYAL FREE SPINS", 180, 79, 7.2f,
                0xFFFFE7A0, true, Paint.Align.CENTER);
        button(c, 10, 21, 75, 51, "RESET", false, now);
        button(c, 285, 21, 350, 51, soundEnabled ? "SFX ON" : "SFX OFF", soundEnabled, now);

        panel(c, 14, 92, 346, 160, 18, 0xE9080B12,
                featureVisualActive() ? 0xFF55DFFF : 0xFF9D7427);
        text(c, "SALDO", 30, 114, 8, 0xFF9FA7B7, true, Paint.Align.LEFT);
        text(c, numbers.format(credits) + " CR", 30, 142, 21, Color.WHITE, true, Paint.Align.LEFT);
        text(c, "RTP TOTAL", 231, 114, 8, 0xFF9FA7B7, true, Paint.Align.CENTER);
        text(c, "95,48%", 231, 141, 15, 0xFFFFCF67, true, Paint.Align.CENTER);
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
        p.setShadowLayer(25, 0, 11, 0xDD000000);
        p.setShader(new LinearGradient(9, top - 20, 351, top + 285,
                new int[]{0xFF241305, 0xFFFFE291, 0xFF875211, 0xFFFFD36A, 0xFF211205},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(9, top - 20, 351, top + CELL_H * 3 + 31), 28, 28, p);
        p.clearShadowLayer(); p.setShader(null);
        panel(c, 14, top - 14, 346, top + CELL_H * 3 + 25, 23, 0xFF05070C, 0xFFFFD76A);

        StakeSlotEngine.SpinResult source = pending != null ? pending : current;
        long elapsed = isSpinning() ? now - phaseStart : Long.MAX_VALUE;
        for (int reel = 0; reel < 5; reel++) {
            float left = REEL_LEFT + reel * (REEL_W + REEL_GAP);
            RectF clip = new RectF(left, top, left + REEL_W, top + CELL_H * 3);
            c.save();
            path.reset(); path.addRoundRect(clip, 8, 8, Path.Direction.CW); c.clipPath(path);
            if (isSpinning() && elapsed < stopTimes[reel]) drawSpinningReel(c, reel, left, top, elapsed);
            else drawStoppedReel(c, source, reel, left, top, now);
            drawGlass(c, clip, now, reel);
            c.restore();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(reel == 2 ? 2.3f : 1.1f);
            p.setColor(reel == 2 ? 0xFFFFD76A : 0xFF8E8A7A);
            c.drawRoundRect(clip, 8, 8, p);
            p.setStyle(Paint.Style.FILL);
        }
        drawAnticipation(c, now, top);
        drawWinLine(c, now, top);
        text(c, featureController.isActive()
                        ? "MODO BONUS · APUESTA BLOQUEADA " + featureController.lockedBetPerLine() * 20 + " CR"
                        : "20 LÍNEAS · 3 WILD = 30 JUEGOS GRATIS",
                180, top + CELL_H * 3 + 17, 7, 0xFFBBC0CA, true, Paint.Align.CENTER);
    }

    private void drawSpinningReel(Canvas c, int reel, float left, float top, long elapsed) {
        long stop = stopTimes[reel];
        float velocity = PremiumReelDynamics.velocity(elapsed, stop);
        float travel = (elapsed * (18f + velocity * 28f) + reel * 47f) % CELL_H;
        int base = (int) (elapsed / Math.max(25f, 66f - velocity * 14f)) + reel * 9;
        for (int item = -2; item <= 4; item++) {
            String symbol = StakeSlotEngine.SYMBOLS[Math.floorMod(base + item,
                    StakeSlotEngine.SYMBOLS.length)];
            float centerY = top + item * CELL_H + travel + CELL_H / 2f;
            float normalized = (centerY - (top + CELL_H * 1.5f)) / (CELL_H * 1.5f);
            float sy = PremiumReelDynamics.cylinderScale(normalized);
            float alpha = PremiumReelDynamics.cylinderAlpha(normalized);
            c.save(); c.scale(1f, sy, left + REEL_W / 2f, centerY);
            drawSymbol(c, left + 2, centerY - CELL_H / 2f + 2,
                    REEL_W - 4, CELL_H - 4, symbol, false, elapsed, alpha);
            c.restore();
        }
        int streaks = qualityTier == 2 ? 10 : qualityTier == 1 ? 6 : 3;
        for (int i = 0; i < streaks; i++) {
            float y = top + ((elapsed * (1.7f + i * .13f) + i * 41f) % (CELL_H * 3));
            int alpha = Math.min(125, Math.round(32 + velocity * 35));
            p.setShader(new LinearGradient(left + 3, y, left + REEL_W - 3, y,
                    new int[]{0x00FFFFFF, (alpha << 24) | 0x00FFFFFF, 0x00FFFFFF},
                    null, Shader.TileMode.CLAMP));
            c.drawRoundRect(new RectF(left + 4, y, left + REEL_W - 4,
                    y + 2f + velocity), 2, 2, p);
            p.setShader(null);
        }
    }

    private void drawStoppedReel(Canvas c, StakeSlotEngine.SpinResult result, int reel,
                                 float left, float top, long now) {
        if (result == null) return;
        long d = now - phaseStart - stopTimes[reel];
        float bounce = (isSpinning() || isRevealing())
                ? PremiumReelDynamics.landingOffset(d, reducedMotion) : 0f;
        for (int row = 0; row < 3; row++) {
            drawSymbol(c, left + 2, top + row * CELL_H + 2 + bounce,
                    REEL_W - 4, CELL_H - 4, result.board[reel][row],
                    winningCell(reel, row, now), now, 1f);
        }
    }

    private void drawSymbol(Canvas c, float left, float top, float width, float height,
                            String symbol, boolean winning, long now, float alpha) {
        int color = StakeSlotEngine.symbolColor(symbol);
        float cx = left + width / 2f, cy = top + height / 2f;
        SymbolAnimationDirector.Frame frame = winning
                ? SymbolAnimationDirector.frame(symbol, featuredCount(), currentMultiplier(),
                Math.max(0L, now - phaseStart))
                : SymbolAnimationDirector.frame("", 0, 0d, 0L);
        c.save();
        if (winning && !reducedMotion) {
            c.translate(0, frame.lift);
            c.rotate(frame.rotation, cx, cy);
            c.scale(frame.scale, frame.scale, cx, cy);
        }
        if (winning) {
            p.setColor(0x66000000 | (color & 0x00FFFFFF));
            c.drawRoundRect(new RectF(left - 3, top - 3, left + width + 3, top + height + 3), 11, 11, p);
        }
        p.setAlpha(Math.round(255 * alpha));
        p.setShader(new LinearGradient(left, top, left, top + height,
                new int[]{0xFF5B554B, 0xFF17191E, 0xFF07080A, 0xFF41382D},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(left, top, left + width, top + height), 8, 8, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1f); p.setColor(0x77FFF0BE);
        c.drawRoundRect(new RectF(left + 1, top + 1, left + width - 1, top + height - 1), 7, 7, p);
        p.setStyle(Paint.Style.FILL);
        p.setShadowLayer(winning ? 12 : 4, 0, 2, winning ? color : 0x99000000);
        if (StakeSlotEngine.WILD.equals(symbol)) {
            crown(c, cx, cy - 5, 23, color);
            text(c, "WILD", cx, top + height - 9, 7.5f, 0xFFFFE7A0, true, Paint.Align.CENTER);
        } else if (StakeSlotEngine.DIAMOND.equals(symbol)) diamond(c, cx, cy, 20, color);
        else if (StakeSlotEngine.BELL.equals(symbol)) bell(c, cx, cy, 21, color);
        else if (StakeSlotEngine.BAR.equals(symbol)) {
            panel(c, left + 8, cy - 13, left + width - 8, cy + 13, 5, 0xFFE7E1D2, 0xFFFFD76A);
            text(c, "BAR", cx, cy + 5, 14, 0xFF151515, true, Paint.Align.CENTER);
        } else {
            String label = StakeSlotEngine.displayLabel(symbol);
            text(c, label, cx, cy + 12, label.length() > 1 ? 22 : 34,
                    color, true, Paint.Align.CENTER);
        }
        p.clearShadowLayer(); p.setAlpha(255); c.restore();
    }

    private void drawGlass(Canvas c, RectF clip, long now, int reel) {
        p.setShader(new LinearGradient(0, clip.top, 0, clip.bottom,
                new int[]{0xCC000000, 0x10000000, 0x00000000, 0x10000000, 0xCC000000},
                new float[]{0f, .16f, .5f, .84f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(clip, p); p.setShader(null);
        float x = clip.left + ((now * (.025f + reel * .002f) + reel * 23f) % REEL_W);
        p.setShader(new LinearGradient(x - 10, clip.top, x + 10, clip.bottom,
                new int[]{0x00FFFFFF, 0x32FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRect(clip, p); p.setShader(null);
    }

    private void drawAnticipation(Canvas c, long now, float top) {
        if (!isSpinning() || !anticipation || reducedMotion) return;
        long elapsed = now - phaseStart;
        float pulse = PremiumReelDynamics.anticipationPulse(elapsed, stopTimes[4]);
        if (pulse <= 0f) return;
        float left = REEL_LEFT + 4 * (REEL_W + REEL_GAP);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f + pulse * 5f);
        p.setColor(((60 + Math.round(pulse * 170)) << 24) | 0x00FFD76A);
        c.drawRoundRect(new RectF(left - 5, top - 7, left + REEL_W + 5,
                top + CELL_H * 3 + 7), 12, 12, p);
        p.setStyle(Paint.Style.FILL);
        panel(c, 215, top - 30, 343, top - 8, 10, 0xE3130C04, 0xFFFFD76A);
        text(c, "ANTICIPACIÓN · ÚLTIMO REEL", 279, top - 15, 6.6f,
                0xFFFFEAB0, true, Paint.Align.CENTER);
    }

    private void drawWinLine(Canvas c, long now, float top) {
        StakeSlotEngine.LineWin win = rotatingWin(now);
        if (win == null) return;
        path.reset();
        for (int reel = 0; reel < 5; reel++) {
            float x = REEL_LEFT + reel * (REEL_W + REEL_GAP) + REEL_W / 2f;
            float y = top + win.rows[reel] * CELL_H + CELL_H / 2f;
            if (reel == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        int accent = StakeSlotEngine.symbolColor(win.symbol);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(9); p.setColor(0x33000000 | (accent & 0x00FFFFFF));
        c.drawPath(path, p); p.setStrokeWidth(2.5f); p.setColor(accent); c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawAdvancedWinVfx(Canvas c, long now) {
        if (!isRevealing() || current == null || current.lineWins.isEmpty()) return;
        StakeSlotEngine.LineWin win = rotatingWin(now);
        if (win == null) return;
        float top = featureController.isActive() ? REEL_TOP : REEL_TOP - 25f;
        long elapsed = Math.max(0L, now - phaseStart);
        for (int reel = 0; reel < win.count; reel++) {
            float cx = REEL_LEFT + reel * (REEL_W + REEL_GAP) + REEL_W / 2f;
            float cy = top + win.rows[reel] * CELL_H + CELL_H / 2f;
            RoyalVfxDirector.drawSymbol(c, p, path, win.symbol, cx, cy,
                    win.count, currentMultiplier(), elapsed, reducedMotion);
        }
        String tier = currentMultiplier() >= 50 ? "ROYAL WIN"
                : currentMultiplier() >= 15 ? "MEGA WIN"
                : currentMultiplier() >= 5 ? "BIG WIN" : "PREMIO";
        if (currentMultiplier() >= 5) {
            p.setShadowLayer(16, 0, 4, StakeSlotEngine.symbolColor(win.symbol));
            goldText(c, tier, 180, 175, currentMultiplier() >= 50 ? 25 : 21, Paint.Align.CENTER);
            p.clearShadowLayer();
        }
    }

    private void drawControls(Canvas c, long now) {
        panel(c, 15, 510, 345, 675, 22, 0xF0080B12,
                featureVisualActive() ? 0xFF397E9D : 0xFF73551E);
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
        float slow = totalFrames == 0 ? 0f : slowFrames * 100f / totalFrames;
        String quality = qualityTier == 2 ? "ULTRA" : qualityTier == 1 ? "ALTA" : "LITE";
        text(c, "FPS " + fps + " · lentos " + String.format(Locale.US, "%.1f", slow)
                        + "% · " + quality + " · ficticio",
                180, 790, 6.7f, 0xFF7D8492, false, Paint.Align.CENTER);
    }

    private void drawMainButton(Canvas c, long now) {
        boolean ready = phase == Phase.IDLE || phase == Phase.FEATURE_READY;
        float pulse = ready && !reducedMotion ? .5f + .5f * (float) Math.sin(now * .0045f) : 0f;
        float press = buttonPressed ? .955f : 1f;
        c.save(); c.scale(press, press, 180, 742);
        p.setShadowLayer(13 + pulse * 9, 0, 5, ready ? 0xAAF6C453 : 0xAA5A48B0);
        p.setShader(new LinearGradient(34, 713, 326, 768,
                ready ? new int[]{0xFFFFF1A8, 0xFFF6B83F, 0xFFFFD96C}
                        : new int[]{0xFF8B65D8, 0xFF513381, 0xFF8B65D8},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(34, 713, 326, 768), 28, 28, p);
        p.clearShadowLayer(); p.setShader(null);
        String label = phase == Phase.IDLE ? "GIRAR"
                : phase == Phase.FEATURE_READY ? "GIRO GRATIS" : "OMITIR ANIMACIÓN";
        text(c, label, 180, 749, ready ? 18 : 13.5f,
                ready ? 0xFF1B1003 : Color.WHITE, true, Paint.Align.CENTER);
        c.restore();
    }

    private void drawSceneOverlay(Canvas c, long now) {
        RoyalVfxDirector.Scene scene = RoyalVfxDirector.Scene.NONE;
        int counter = 0;
        if (phase == Phase.FEATURE_INTRO) {
            scene = RoyalVfxDirector.Scene.FEATURE_INTRO;
            counter = Math.min(30, Math.round(30 * clamp((now - phaseStart) / 2600f)));
        } else if (phase == Phase.RETRIGGER) {
            scene = RoyalVfxDirector.Scene.RETRIGGER;
            counter = lastRetriggerAdded;
        } else if (phase == Phase.FEATURE_SUMMARY) {
            scene = RoyalVfxDirector.Scene.FEATURE_SUMMARY;
        }
        RoyalVfxDirector.drawScene(c, p, path, scene, now - phaseStart, reducedMotion,
                counter, featureController.totalFeatureWin(),
                featureController.snapshot().triggerWildCount);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = (event.getX() - offsetX) / Math.max(.0001f, scale);
        float y = (event.getY() - offsetY) / Math.max(.0001f, scale);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            buttonPressed = y > 700; invalidate(); return true;
        }
        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            buttonPressed = false; invalidate(); return true;
        }
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        buttonPressed = false;
        if (x > 280 && y < 65) {
            soundEnabled = !soundEnabled; audio.setEnabled(soundEnabled); audio.playTap(); saveAll(); return true;
        }
        if (x > 82 && x < 278 && y > 62 && y < 90 && phase == Phase.IDLE) {
            reducedMotion = !reducedMotion;
            message = reducedMotion ? "Movimiento reducido activado" : "Cinemática completa activada";
            audio.playTap(); haptics.tap(); saveAll(); return true;
        }
        if (x < 82 && y < 65 && phase == Phase.IDLE) {
            credits = 5000; rounds = lastWin = shownWin = 0; roundRepository.clear();
            message = "Saldo demo reiniciado"; saveAll(); return true;
        }
        if (phase == Phase.IDLE) {
            if (y > 535 && y < 615 && x < 100) {
                betPerLine = StakeSlotEngine.clampBet(betPerLine - 1); audio.playTap(); saveAll();
            } else if (y > 535 && y < 615 && x > 260) {
                betPerLine = StakeSlotEngine.clampBet(betPerLine + 1); audio.playTap(); saveAll();
            } else if (y > 700) startBaseSpin(null);
        } else if (phase == Phase.FEATURE_READY && y > 700) startFreeSpin(null);
        else if (y > 700) skipAnimation();
        return true;
    }

    private void skipAnimation() {
        long now = SystemClock.uptimeMillis();
        if (isSpinning()) settleRound(now);
        if (isRevealing()) completeReveal(now);
        if (phase == Phase.FEATURE_INTRO || phase == Phase.RETRIGGER) {
            phase = Phase.FEATURE_READY; phaseStart = now;
        } else if (phase == Phase.FEATURE_SUMMARY) {
            featureController.finishFeature(); featureRepository.clear();
            phase = Phase.IDLE; phaseStart = now;
        }
        audio.stopAll(); haptics.cancel(); saveAll();
    }

    private boolean isSpinning() {
        return phase == Phase.BASE_SPINNING || phase == Phase.FEATURE_SPINNING;
    }
    private boolean isRevealing() {
        return phase == Phase.BASE_REVEALING || phase == Phase.FEATURE_REVEALING;
    }
    private StakeSlotEngine.LineWin rotatingWin(long now) {
        if (!isRevealing() || current == null || current.lineWins.isEmpty()) return null;
        return current.lineWins.get((int) (((now - phaseStart) / 760L) % current.lineWins.size()));
    }
    private StakeSlotEngine.LineWin featuredWin() {
        return current == null ? null : SymbolAnimationDirector.primaryWin(current.lineWins);
    }
    private int featuredCount() {
        StakeSlotEngine.LineWin win = featuredWin(); return win == null ? 0 : win.count;
    }
    private boolean winningCell(int reel, int row, long now) {
        StakeSlotEngine.LineWin win = rotatingWin(now);
        return win != null && reel < win.count && win.rows[reel] == row;
    }
    private double currentMultiplier() {
        return current == null ? 0d : current.payoutMultiplier();
    }

    private void savePreferences() {
        boolean ok = prefs.edit().putInt("credits", credits).putInt("bet", betPerLine)
                .putInt("rounds", rounds).putBoolean("sound", soundEnabled)
                .putBoolean("haptic", hapticEnabled)
                .putBoolean("reduced_motion", reducedMotion).commit();
        if (!ok) throw new IllegalStateException("Could not persist game preferences");
    }

    private void saveAll() {
        savePreferences();
        if (featureController.isActive()) featureRepository.save(featureController.snapshot());
    }

    public void onHostPause() {
        if (isSpinning()) settleRound(SystemClock.uptimeMillis());
        if (isRevealing()) completeReveal(SystemClock.uptimeMillis());
        saveAll();
    }

    public void release() {
        frameLoop = false;
        Choreographer.getInstance().removeFrameCallback(this);
        audio.release(); haptics.release();
    }

    private void panel(Canvas c, float l, float t, float r, float b, float radius,
                       int fill, int line) {
        p.setStyle(Paint.Style.FILL); p.setColor(fill);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.25f); p.setColor(line);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void button(Canvas c, float l, float t, float r, float b, String label,
                        boolean active, long now) {
        float pulse = active && !reducedMotion ? .5f + .5f * (float) Math.sin(now * .005f) : 0f;
        p.setShadowLayer(6 + pulse * 4, 0, 3, active ? 0x99F6C453 : 0x66000000);
        p.setShader(new LinearGradient(l, t, r, b,
                active ? new int[]{0xFF3A2308, 0xFF8A5918, 0xFF3A2308}
                        : new int[]{0xFF262B38, 0xFF11151E, 0xFF262B38},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(l, t, r, b), (b - t) / 2f, (b - t) / 2f, p);
        p.clearShadowLayer(); p.setShader(null);
        text(c, label, (l + r) / 2f, (t + b) / 2f + 3,
                label.length() > 6 ? 7 : 9,
                active ? 0xFFFFE39A : 0xFFE7E9EF, true, Paint.Align.CENTER);
    }

    private void goldText(Canvas c, String value, float x, float y, float size, Paint.Align align) {
        p.setShader(new LinearGradient(x - size * 2, y - size, x + size * 2, y,
                new int[]{0xFFC78320, 0xFFFFF0AA, 0xFFF6B83F, 0xFFFFE58B},
                null, Shader.TileMode.MIRROR));
        text(c, value, x, y, size, Color.WHITE, true, align); p.setShader(null);
    }

    private void text(Canvas c, String value, float x, float y, float size, int color,
                      boolean bold, Paint.Align align) {
        p.setTextSize(size); p.setColor(color); p.setTextAlign(align);
        p.setTypeface(bold ? Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                : Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        c.drawText(value, x, y, p);
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
        path.close(); p.setColor(color); c.drawPath(path, p);
        c.drawRoundRect(new RectF(cx - size * .57f, cy + size * .36f,
                cx + size * .57f, cy + size * .48f), size * .05f, size * .05f, p);
    }

    private void diamond(Canvas c, float cx, float cy, float size, int color) {
        path.reset(); path.moveTo(cx, cy - size);
        path.lineTo(cx + size * .78f, cy - size * .22f);
        path.lineTo(cx + size * .48f, cy + size);
        path.lineTo(cx - size * .48f, cy + size);
        path.lineTo(cx - size * .78f, cy - size * .22f); path.close();
        p.setShader(new LinearGradient(cx - size, cy - size, cx + size, cy + size,
                new int[]{0xFFFFFFFF, color, 0xFF3977FF}, null, Shader.TileMode.CLAMP));
        c.drawPath(path, p); p.setShader(null);
    }

    private void bell(Canvas c, float cx, float cy, float size, int color) {
        p.setColor(color);
        c.drawArc(new RectF(cx - size * .65f, cy - size * .58f,
                cx + size * .65f, cy + size * .55f), 180, 180, true, p);
        c.drawRoundRect(new RectF(cx - size * .75f, cy + size * .35f,
                cx + size * .75f, cy + size * .58f), size * .08f, size * .08f, p);
        p.setColor(0xFF8D5510); c.drawCircle(cx, cy + size * .72f, size * .16f, p);
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
    private static float easeOut(float value) {
        float x = clamp(value); return 1f - (1f - x) * (1f - x) * (1f - x);
    }
}
