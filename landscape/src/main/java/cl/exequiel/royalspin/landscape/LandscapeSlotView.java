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
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import java.text.NumberFormat;
import java.util.Locale;

public final class LandscapeSlotView extends View implements Choreographer.FrameCallback {
    private static final float W = 1280f;
    private static final float H = 720f;
    private static final float REEL_LEFT = 248f;
    private static final float REEL_TOP = 155f;
    private static final float REEL_WIDTH = 142f;
    private static final float REEL_GAP = 7f;
    private static final float CELL_HEIGHT = 132f;
    private static final long[] REEL_STOPS = {920L, 1180L, 1450L, 1740L, 2220L};
    private static final String[] CYCLE = {LandscapeGameEngine.Q, LandscapeGameEngine.WILD,
            LandscapeGameEngine.J, LandscapeGameEngine.BELL, LandscapeGameEngine.DIAMOND,
            LandscapeGameEngine.BAR, LandscapeGameEngine.SEVEN, LandscapeGameEngine.K,
            LandscapeGameEngine.A};
    private enum Phase { IDLE, SPINNING, REVEALING, FEATURE_INTRO, FEATURE_SUMMARY }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final BitmapAssets assets;
    private final LandscapeGameEngine engine;
    private final PremiumSoundEngine sound = new PremiumSoundEngine();
    private final NumberFormat numbers = NumberFormat.getIntegerInstance(new Locale("es", "CL"));
    private final Vibrator vibrator;
    private final FloatValueHolder buttonHolder = new FloatValueHolder(1f);
    private final SpringAnimation buttonSpring = new SpringAnimation(buttonHolder);

    private Phase phase = Phase.IDLE;
    private LandscapeGameEngine.SpinResult current;
    private LandscapeGameEngine.SpinResult pending;
    private long phaseStart;
    private long idleSince;
    private long lastFrameNanos;
    private long qualityWindowStart;
    private int qualityFrames;
    private int qualitySlowFrames;
    private int qualityTier = 2;
    private boolean running;
    private boolean pressedSpin;
    private boolean[] reelStopPlayed = new boolean[LandscapeGameEngine.REELS];
    private long credits = 5_000;
    private int betPerLine = 5;
    private int round;
    private int freeSpins;
    private int featureWin;
    private boolean featureActive;
    private boolean featureRetrigger;
    private int introAward;
    private int shownWin;
    private int countTick;
    private String message = "LISTO PARA GIRAR";
    private final String demoMode;
    private float scale = 1f;
    private float offsetX;
    private float offsetY;

    public LandscapeSlotView(Context context, String demoMode) {
        super(context);
        this.demoMode = demoMode;
        setFocusable(true);
        setClickable(true);
        setLayerType(View.LAYER_TYPE_NONE, null);
        assets = new BitmapAssets(context);
        engine = new LandscapeGameEngine(0x524F59414C5F5350L);
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        buttonSpring.setSpring(new SpringForce(1f).setDampingRatio(.58f).setStiffness(520f));
        buttonSpring.addUpdateListener((animation, value, velocity) -> invalidate());
        current = engine.scripted("idle", betPerLine, false);
        phaseStart = idleSince = SystemClock.uptimeMillis();
        configureDemo(demoMode);
    }

    private void configureDemo(String demo) {
        if (demo == null || demo.isEmpty() || "idle".equals(demo)) return;
        long now = SystemClock.uptimeMillis();
        if ("feature".equals(demo)) {
            featureActive = true;
            freeSpins = 29;
            featureWin = 1_240;
            current = engine.scripted("wild", betPerLine, true);
            message = "ROYAL FREE SPINS";
            phase = Phase.IDLE;
            idleSince = now;
            return;
        }
        current = engine.scripted(demo, betPerLine, false);
        phase = Phase.REVEALING;
        phaseStart = now - 850L;
        shownWin = current.payout;
        message = demo.toUpperCase(Locale.ROOT) + " CELEBRATION";
        if ("wild".equals(demo)) { featureRetrigger = false; introAward = 30; }
    }

    public void resume() { if (!running) { running = true; lastFrameNanos = 0L; Choreographer.getInstance().postFrameCallback(this); } }
    public void pause() { running = false; Choreographer.getInstance().removeFrameCallback(this); }
    public void release() { pause(); buttonSpring.cancel(); sound.release(); assets.recycle(); }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        updateQuality(frameTimeNanos);
        updateState(SystemClock.uptimeMillis());
        invalidate();
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void updateQuality(long frameTimeNanos) {
        if (lastFrameNanos != 0L) {
            long delta = frameTimeNanos - lastFrameNanos;
            qualityFrames++;
            if (delta > 25_000_000L) qualitySlowFrames++;
        }
        lastFrameNanos = frameTimeNanos;
        if (qualityWindowStart == 0L) qualityWindowStart = frameTimeNanos;
        if (frameTimeNanos - qualityWindowStart >= 1_500_000_000L) {
            float ratio = qualityFrames == 0 ? 0f : qualitySlowFrames / (float) qualityFrames;
            if (ratio > .32f) qualityTier = Math.max(0, qualityTier - 1);
            else if (ratio < .09f) qualityTier = Math.min(2, qualityTier + 1);
            qualityWindowStart = frameTimeNanos;
            qualityFrames = qualitySlowFrames = 0;
        }
    }

    private void updateState(long now) {
        long elapsed = now - phaseStart;
        if (phase == Phase.SPINNING) {
            for (int reel = 0; reel < REEL_STOPS.length; reel++) {
                if (!reelStopPlayed[reel] && elapsed >= REEL_STOPS[reel]) {
                    reelStopPlayed[reel] = true;
                    sound.playReelStop(reel);
                    vibrate(18 + reel * 3);
                }
            }
            if (elapsed >= REEL_STOPS[REEL_STOPS.length - 1] + 300L) {
                current = pending;
                pending = null;
                shownWin = 0;
                countTick = 0;
                credits += current.payout;
                if (current.freeSpin) featureWin += current.payout;
                if (current.freeSpin && current.awardedFreeSpins > 0) {
                    freeSpins = Math.min(90, freeSpins + current.awardedFreeSpins);
                    featureRetrigger = true;
                }
                phase = Phase.REVEALING;
                phaseStart = now;
                message = current.payout > 0 ? "PREMIO " + numbers.format(current.payout) + " CR" : "SIGUE GIRANDO";
                if (!current.wins.isEmpty()) sound.playSymbol(current.wins.get(0).symbol);
            }
        } else if (phase == Phase.REVEALING) {
            int target = current == null ? 0 : current.payout;
            float progress = LandscapeAnimationMath.smoothstep(elapsed / 1500f);
            shownWin = Math.round(target * progress);
            int tick = shownWin / Math.max(1, Math.max(1, target / 10));
            if (tick > countTick && target > 0) { countTick = tick; sound.playCountTick(tick); }
            long duration = target > 0 ? 2800L : 1100L;
            if (elapsed >= duration) finishReveal(now);
        } else if (phase == Phase.FEATURE_INTRO) {
            if (elapsed >= 4300L) {
                if (!featureActive) { featureActive = true; freeSpins = Math.min(90, freeSpins + introAward); featureWin = 0; }
                featureRetrigger = false;
                introAward = 0;
                phase = Phase.IDLE;
                phaseStart = idleSince = now;
                message = "ROYAL FREE SPINS";
            }
        } else if (phase == Phase.FEATURE_SUMMARY) {
            if (elapsed >= 4600L) {
                featureActive = false;
                featureWin = 0;
                phase = Phase.IDLE;
                phaseStart = idleSince = now;
                message = "LISTO PARA GIRAR";
            }
        } else if (phase == Phase.IDLE && featureActive && freeSpins > 0 && demoMode == null && now - idleSince >= 900L) {
            startSpin(now);
        }
    }

    private void finishReveal(long now) {
        if (current != null && current.awardedFreeSpins > 0) {
            introAward = current.awardedFreeSpins;
            if (featureActive) { featureRetrigger = true; sound.playRetrigger(); }
            else sound.playFeature();
            phase = Phase.FEATURE_INTRO;
            phaseStart = now;
            message = featureRetrigger ? "+10 FREE SPINS" : "30 FREE SPINS";
            vibrate(110);
            return;
        }
        if (featureActive && freeSpins <= 0) {
            phase = Phase.FEATURE_SUMMARY;
            phaseStart = now;
            message = "TOTAL BONUS " + numbers.format(featureWin) + " CR";
            sound.playFeature();
            return;
        }
        phase = Phase.IDLE;
        phaseStart = idleSince = now;
        message = featureActive ? "SIGUIENTE GIRO GRATIS" : "LISTO PARA GIRAR";
    }

    private void startSpin(long now) {
        if (phase != Phase.IDLE) return;
        boolean free = featureActive && freeSpins > 0;
        int totalBet = betPerLine * LandscapeGameEngine.LINES;
        if (!free && credits < totalBet) { message = "CRÉDITOS INSUFICIENTES"; vibrate(40); return; }
        if (!free) credits -= totalBet; else freeSpins--;
        pending = engine.spin(betPerLine, free);
        phase = Phase.SPINNING;
        phaseStart = now;
        round++;
        reelStopPlayed = new boolean[LandscapeGameEngine.REELS];
        message = free ? "GIRO GRATIS" : "GIRANDO";
        sound.playSpin();
        vibrate(24);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = (event.getX() - offsetX) / Math.max(.0001f, scale);
        float y = (event.getY() - offsetY) / Math.max(.0001f, scale);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (inside(x, y, 1050, 455, 1240, 650)) { pressedSpin = true; buttonSpring.animateToFinalPosition(.92f); invalidate(); return true; }
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            boolean wasSpin = pressedSpin;
            pressedSpin = false;
            buttonSpring.animateToFinalPosition(1f);
            long now = SystemClock.uptimeMillis();
            if (wasSpin && inside(x, y, 1050, 455, 1240, 650)) {
                if (phase == Phase.IDLE) startSpin(now);
                else if (phase == Phase.REVEALING) finishReveal(now);
                else if (phase == Phase.FEATURE_INTRO) phaseStart = now - 4300L;
                else if (phase == Phase.FEATURE_SUMMARY) phaseStart = now - 4600L;
                return true;
            }
            if (inside(x, y, 1065, 125, 1120, 175) && phase == Phase.IDLE && !featureActive) { betPerLine = Math.max(1, betPerLine - 1); vibrate(15); }
            else if (inside(x, y, 1170, 125, 1225, 175) && phase == Phase.IDLE && !featureActive) { betPerLine = Math.min(10, betPerLine + 1); vibrate(15); }
            else if (inside(x, y, 1110, 30, 1240, 80)) { sound.setEnabled(!sound.isEnabled()); vibrate(15); }
            else if (inside(x, y, 30, 30, 160, 78) && phase == Phase.IDLE && !featureActive) { credits = 5_000; round = 0; message = "SALDO REINICIADO"; }
            invalidate();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_CANCEL) { pressedSpin = false; buttonSpring.animateToFinalPosition(1f); }
        return true;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        scale = Math.min(getWidth() / W, getHeight() / H);
        offsetX = (getWidth() - W * scale) * .5f;
        offsetY = (getHeight() - H * scale) * .5f;
        canvas.drawColor(0xFF020307);
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        drawBackground(canvas, now);
        drawHeader(canvas, now);
        drawLeftPanel(canvas, now);
        drawReelCabinet(canvas, now);
        drawRightPanel(canvas, now);
        drawBottomStatus(canvas);
        if (phase == Phase.FEATURE_INTRO) drawFeatureIntro(canvas, now);
        if (phase == Phase.FEATURE_SUMMARY) drawFeatureSummary(canvas, now);
        canvas.restore();
    }

    private void drawBackground(Canvas c, long now) {
        int accent = featureActive ? 0xFFFF3E55 : 0xFFFFB93A;
        p.setShader(new RadialGradient(640, 330, 720,
                featureActive ? new int[]{0xFF230510, 0xFF08040A, 0xFF010205} : new int[]{0xFF17101D, 0xFF050711, 0xFF010205},
                new float[]{0f, .58f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
        int stars = LandscapeAnimationMath.particleBudget(qualityTier, false, 62);
        for (int i = 0; i < stars; i++) {
            float px = (i * 173f + 91f) % W;
            float py = (i * 97f + 47f) % H;
            float pulse = LandscapeAnimationMath.breathe(now, i, 2300L + i * 23L, .35f);
            p.setColor(withAlpha(i % 4 == 0 ? accent : 0xFF5B8DFF, Math.round(32 + pulse * 34)));
            c.drawCircle(px, py, i % 7 == 0 ? 1.7f : .8f, p);
        }
    }

    private void drawHeader(Canvas c, long now) {
        float breathe = LandscapeAnimationMath.breathe(now, 5, 4200L, .018f);
        float logoW = 470f * breathe;
        float logoH = 126f * breathe;
        rect.set(640 - logoW / 2f, 18, 640 + logoW / 2f, 18 + logoH);
        p.setAlpha(255);
        c.drawBitmap(assets.logo, null, rect, p);
        float sweep = LandscapeAnimationMath.shimmer(now, 8, 3600L);
        float x = 430 + sweep * 420;
        p.setShader(new LinearGradient(x - 70, 25, x + 70, 112, new int[]{0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(405, 20, 875, 126), 22, 22, p);
        p.setShader(null);
        drawPill(c, 30, 30, 160, 78, "RESET", 0xFFFFD76A, 10);
        drawPill(c, 1110, 30, 1240, 80, sound.isEnabled() ? "SFX ON" : "SFX OFF", sound.isEnabled() ? 0xFFFFD76A : 0xFF9D87BB, 10);
    }

    private void drawLeftPanel(Canvas c, long now) {
        panel(c, 20, 105, 220, 650, featureActive ? 0xFF43101C : 0xFF16111C);
        text(c, "SALDO", 48, 160, 18, 0xFFAEB7C8, Paint.Align.LEFT, false);
        text(c, numbers.format(credits), 48, 205, 36, Color.WHITE, Paint.Align.LEFT, true);
        text(c, "CR", 48, 234, 18, 0xFFFFD76A, Paint.Align.LEFT, true);
        line(c, 45, 270, 195, 270, 0x55FFD76A, 1);
        text(c, "RTP TOTAL", 48, 312, 17, 0xFFAEB7C8, Paint.Align.LEFT, false);
        text(c, "95,48%", 48, 350, 29, 0xFFFFD76A, Paint.Align.LEFT, true);
        text(c, "RONDA " + round, 48, 405, 18, 0xFFAEB7C8, Paint.Align.LEFT, false);
        if (featureActive) {
            float pulse = LandscapeAnimationMath.breathe(now, 22, 1800L, .045f);
            p.setShader(new LinearGradient(42, 455, 198, 570, new int[]{0xFF8D1025, 0xFFFF3E55, 0xFF8D1025}, null, Shader.TileMode.MIRROR));
            c.drawRoundRect(new RectF(40, 455, 200, 570), 20, 20, p);
            p.setShader(null);
            text(c, "FREE SPINS", 120, 490, 18, Color.WHITE, Paint.Align.CENTER, true);
            c.save(); c.scale(pulse, pulse, 120, 535);
            text(c, String.valueOf(freeSpins), 120, 548, 50, 0xFFFFE27A, Paint.Align.CENTER, true);
            c.restore();
            text(c, "BONUS " + numbers.format(featureWin) + " CR", 120, 610, 17, 0xFFFFD76A, Paint.Align.CENTER, true);
        } else {
            text(c, "20 LÍNEAS", 48, 490, 18, 0xFFAEB7C8, Paint.Align.LEFT, false);
            text(c, "WILD ACTIVA", 48, 535, 18, 0xFFAEB7C8, Paint.Align.LEFT, false);
            text(c, "30 FREE SPINS", 48, 570, 19, 0xFFFFD76A, Paint.Align.LEFT, true);
        }
    }

    private void drawReelCabinet(Canvas c, long now) {
        int accent = featureActive ? 0xFFFF435B : 0xFFFFC54F;
        p.setShadowLayer(24, 0, 7, withAlpha(accent, 120));
        p.setShader(new LinearGradient(230, 125, 1005, 605, new int[]{0xFFFFE18B, 0xFF7D4613, 0xFFFFD76A, 0xFF5F3110}, null, Shader.TileMode.MIRROR));
        c.drawRoundRect(new RectF(232, 125, 1002, 592), 28, 28, p);
        p.clearShadowLayer(); p.setShader(null);
        p.setShader(new LinearGradient(242, 135, 992, 582, featureActive ? new int[]{0xFF25050D, 0xFF09040A, 0xFF26050D} : new int[]{0xFF080A11, 0xFF020306, 0xFF0A0710}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(240, 133, 994, 584), 24, 24, p);
        p.setShader(null);
        long elapsed = SystemClock.uptimeMillis() - phaseStart;
        LandscapeGameEngine.LineWin activeWin = activeWin(elapsed);
        for (int reel = 0; reel < LandscapeGameEngine.REELS; reel++) for (int row = 0; row < LandscapeGameEngine.ROWS; row++) drawCell(c, reel, row, now, elapsed, activeWin);
        if (phase == Phase.REVEALING && current != null && !current.wins.isEmpty()) {
            drawWinningLine(c, activeWin, now); drawWinBanner(c, now); drawConfetti(c, now, current.wins.get(0).symbol);
        }
    }

    private void drawCell(Canvas c, int reel, int row, long now, long elapsed, LandscapeGameEngine.LineWin activeWin) {
        float left = REEL_LEFT + reel * (REEL_WIDTH + REEL_GAP);
        float top = REEL_TOP + row * CELL_HEIGHT;
        boolean spinning = phase == Phase.SPINNING && elapsed < REEL_STOPS[reel];
        String symbol;
        float spinOffset = 0f;
        if (spinning) {
            int frame = (int) ((elapsed / 72L + reel * 3L + row) % CYCLE.length);
            symbol = CYCLE[frame];
            spinOffset = (elapsed * (.42f + reel * .035f)) % CELL_HEIGHT;
            spinOffset = (spinOffset - CELL_HEIGHT * .5f) * .28f;
        } else {
            LandscapeGameEngine.SpinResult source = phase == Phase.SPINNING && pending != null ? pending : current;
            symbol = source == null ? CYCLE[(reel * 3 + row) % CYCLE.length] : source.board[reel][row];
        }
        boolean winning = activeWin != null && reel < activeWin.count && activeWin.rows[reel] == row;
        float landing = phase == Phase.SPINNING && elapsed >= REEL_STOPS[reel] ? LandscapeAnimationMath.dampedLanding(elapsed - REEL_STOPS[reel], 13f) : 0f;
        float cx = left + REEL_WIDTH / 2f;
        float cy = top + CELL_HEIGHT / 2f + spinOffset + landing;
        p.setShader(new LinearGradient(left, top, left + REEL_WIDTH, top + CELL_HEIGHT, new int[]{0xFF03050A, 0xFF121018, 0xFF020307}, null, Shader.TileMode.CLAMP));
        rect.set(left + 2, top + 2, left + REEL_WIDTH - 2, top + CELL_HEIGHT - 2);
        c.drawRoundRect(rect, 12, 12, p); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(winning ? 3.4f : 1.2f); p.setColor(winning ? 0xFFFFE486 : 0x664F391B); c.drawRoundRect(rect, 12, 12, p); p.setStyle(Paint.Style.FILL);
        if (spinning) {
            p.setShader(new LinearGradient(left, top, left, top + CELL_HEIGHT, new int[]{0x00FFFFFF, 0x55FFD76A, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
            for (int i = 0; i < 4; i++) {
                float sy = top + ((elapsed * (.25f + i * .07f) + i * 29f) % CELL_HEIGHT);
                c.drawRoundRect(new RectF(left + 20, sy, left + REEL_WIDTH - 20, sy + 7), 4, 4, p);
            }
            p.setShader(null);
        }
        drawSymbol(c, symbol, cx, cy, left, top, reel, row, now, winning, spinning);
    }

    private void drawSymbol(Canvas c, String symbol, float cx, float cy, float left, float top, int reel, int row, long now, boolean winning, boolean spinning) {
        Bitmap bitmap = assets.symbol(symbol);
        int seed = reel * 31 + row * 17 + symbol.hashCode();
        float idleScale = LandscapeAnimationMath.breathe(now, seed, 2800L + reel * 130L, winning ? .07f : .025f);
        float rotation = 0f;
        if (LandscapeGameEngine.BELL.equals(symbol)) { rotation = (float) Math.sin((now + seed * 33L) * .0042) * (winning ? 10f : 3.2f); drawBellRings(c, cx, cy, now, seed, winning); }
        else if (LandscapeGameEngine.DIAMOND.equals(symbol)) { rotation = (float) Math.sin((now + seed * 47L) * .0019) * (winning ? 8f : 3f); drawDiamondRays(c, cx, cy, now, seed, winning); }
        else if (LandscapeGameEngine.SEVEN.equals(symbol)) { rotation = winning ? (float) Math.sin(now * .01) * 2.5f : 0f; drawSevenEnergy(c, cx, cy, now, seed, winning); }
        else if (LandscapeGameEngine.BAR.equals(symbol)) drawBarScan(c, left, top, now, seed, winning);
        else if (LandscapeGameEngine.WILD.equals(symbol)) drawWildAura(c, cx, cy, now, seed, winning);
        float size = LandscapeGameEngine.WILD.equals(symbol) ? 116f : LandscapeGameEngine.BAR.equals(symbol) ? 116f : 106f;
        if (spinning) idleScale *= .90f;
        c.save(); c.translate(cx, cy); c.rotate(rotation); c.scale(idleScale, idleScale);
        if (winning) p.setShadowLayer(22f, 0, 0, symbolGlow(symbol));
        rect.set(-size / 2f, -size / 2f, size / 2f, size / 2f);
        p.setAlpha(spinning ? 205 : 255); c.drawBitmap(bitmap, null, rect, p); p.setAlpha(255); p.clearShadowLayer();
        float sweep = LandscapeAnimationMath.shimmer(now, seed, winning ? 980L : 3000L);
        float sx = -size * .65f + sweep * size * 1.3f;
        p.setShader(new LinearGradient(sx - 16, -size / 2f, sx + 16, size / 2f, new int[]{0x00FFFFFF, winning ? 0xAAFFFFFF : 0x44FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(-size / 2f, -size / 2f, size / 2f, size / 2f), 16, 16, p); p.setShader(null); c.restore();
    }

    private void drawBellRings(Canvas c, float cx, float cy, long now, int seed, boolean winning) {
        int rings = winning ? 4 : 1; p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < rings; i++) {
            float t = LandscapeAnimationMath.shimmer(now, seed + i * 11, 1200L);
            p.setStrokeWidth(winning ? 2.6f : 1.2f); p.setColor(withAlpha(0xFFFFD76A, Math.round((1f - t) * (winning ? 180 : 65))));
            c.drawCircle(cx, cy, 45 + t * 45, p);
        }
        p.setStyle(Paint.Style.FILL);
    }
    private void drawDiamondRays(Canvas c, float cx, float cy, long now, int seed, boolean winning) {
        int rays = LandscapeAnimationMath.particleBudget(qualityTier, false, winning ? 14 : 6); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(winning ? 2.2f : 1f);
        for (int i = 0; i < rays; i++) {
            double angle = Math.PI * 2 * i / Math.max(1, rays) + now * .00035;
            float inner = 45f, outer = winning ? 78f : 58f; p.setColor(withAlpha(i % 2 == 0 ? 0xFF55DFFF : 0xFFD986FF, winning ? 165 : 72));
            c.drawLine(cx + (float) Math.cos(angle) * inner, cy + (float) Math.sin(angle) * inner, cx + (float) Math.cos(angle) * outer, cy + (float) Math.sin(angle) * outer, p);
        }
        p.setStyle(Paint.Style.FILL);
    }
    private void drawBarScan(Canvas c, float left, float top, long now, int seed, boolean winning) {
        float sweep = LandscapeAnimationMath.shimmer(now, seed, winning ? 650L : 2100L); float x = left + 12 + sweep * (REEL_WIDTH - 24);
        p.setShader(new LinearGradient(x - 22, top, x + 22, top + CELL_HEIGHT, new int[]{0x00FFFFFF, winning ? 0xAAFFFFFF : 0x55FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(left + 7, top + 14, left + REEL_WIDTH - 7, top + CELL_HEIGHT - 14), 12, 12, p); p.setShader(null);
        if (winning) { p.setColor(0xAAFFD76A); c.drawRect(left + 7, top + CELL_HEIGHT / 2f - 2, left + REEL_WIDTH - 7, top + CELL_HEIGHT / 2f + 2, p); }
    }
    private void drawSevenEnergy(Canvas c, float cx, float cy, long now, int seed, boolean winning) {
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(winning ? 4f : 1.4f); p.setColor(withAlpha(0xFFFF324B, winning ? 220 : 78)); float drift = (float) Math.sin((now + seed) * .006) * 8f;
        c.drawLine(cx - 58 + drift, cy + 55, cx + 57 - drift, cy - 55, p); p.setStrokeWidth(winning ? 2.2f : .8f); p.setColor(withAlpha(0xFFFFD76A, winning ? 190 : 65)); c.drawLine(cx - 48 - drift, cy + 58, cx + 63 + drift, cy - 48, p); p.setStyle(Paint.Style.FILL);
    }
    private void drawWildAura(Canvas c, float cx, float cy, long now, int seed, boolean winning) {
        int jewels = LandscapeAnimationMath.particleBudget(qualityTier, false, winning ? 12 : 6); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(winning ? 3f : 1.3f); p.setColor(withAlpha(0xFFFFD76A, winning ? 210 : 90)); c.drawCircle(cx, cy, winning ? 69 : 58, p); p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < jewels; i++) { double angle = Math.PI * 2 * i / jewels + now * (winning ? .0015 : .00055); float radius = winning ? 72f : 60f; float x = cx + (float) Math.cos(angle) * radius; float y = cy + (float) Math.sin(angle) * radius * .65f; p.setColor(i % 2 == 0 ? 0xFFFFE78B : 0xFF62DFFF); c.drawCircle(x, y, winning ? 3.2f : 1.8f, p); }
    }
    private void drawWinningLine(Canvas c, LandscapeGameEngine.LineWin win, long now) {
        if (win == null) return; p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5f); p.setColor(withAlpha(symbolGlow(win.symbol), Math.round(145 + 85 * Math.abs(Math.sin(now * .006))))); path.reset();
        for (int reel = 0; reel < win.count; reel++) { float x = REEL_LEFT + reel * (REEL_WIDTH + REEL_GAP) + REEL_WIDTH / 2f; float y = REEL_TOP + win.rows[reel] * CELL_HEIGHT + CELL_HEIGHT / 2f; if (reel == 0) path.moveTo(x, y); else path.lineTo(x, y); }
        c.drawPath(path, p); p.setStyle(Paint.Style.FILL);
    }
    private void drawWinBanner(Canvas c, long now) {
        float pulse = LandscapeAnimationMath.breathe(now, 44, 1400L, .035f); c.save(); c.scale(pulse, pulse, 620, 615); p.setShadowLayer(22, 0, 4, 0xAAFFD76A); p.setShader(new LinearGradient(430, 590, 810, 650, new int[]{0xFF5B2108, 0xFFFFC84E, 0xFF7A2B08}, null, Shader.TileMode.MIRROR)); c.drawRoundRect(new RectF(430, 590, 810, 650), 24, 24, p); p.clearShadowLayer(); p.setShader(null); text(c, numbers.format(shownWin) + " CR", 620, 633, 39, 0xFF210B03, Paint.Align.CENTER, true); c.restore();
    }
    private void drawConfetti(Canvas c, long now, String symbol) {
        int count = LandscapeAnimationMath.particleBudget(qualityTier, false, 50); int color = symbolGlow(symbol);
        for (int i = 0; i < count; i++) { float t = LandscapeAnimationMath.shimmer(now, i * 7 + symbol.hashCode(), 2200L); float x = 250 + ((i * 137f) % 740); float y = 130 + t * 500; float sway = (float) Math.sin(now * .004 + i) * 18f; p.setColor(withAlpha(i % 3 == 0 ? color : 0xFFFFD76A, Math.round((1f - t) * 210))); c.save(); c.rotate((now * .08f + i * 37f) % 360f, x + sway, y); c.drawRoundRect(new RectF(x + sway - 5, y - 2, x + sway + 5, y + 2), 2, 2, p); c.restore(); }
    }
    private LandscapeGameEngine.LineWin activeWin(long elapsed) { if (phase != Phase.REVEALING || current == null || current.wins.isEmpty()) return null; return current.wins.get((int) ((Math.max(0L, elapsed) / 720L) % current.wins.size())); }

    private void drawRightPanel(Canvas c, long now) {
        panel(c, 1020, 105, 1260, 670, featureActive ? 0xFF43101C : 0xFF16111C);
        text(c, "APUESTA POR LÍNEA", 1140, 145, 17, 0xFFAEB7C8, Paint.Align.CENTER, false); drawCircleButton(c, 1092, 164, 25, "−"); drawCircleButton(c, 1198, 164, 25, "+"); text(c, betPerLine + " CR", 1145, 172, 27, 0xFFFFD76A, Paint.Align.CENTER, true);
        text(c, "APUESTA TOTAL", 1055, 235, 15, 0xFFAEB7C8, Paint.Align.LEFT, false); text(c, numbers.format(betPerLine * LandscapeGameEngine.LINES) + " CR", 1055, 268, 24, Color.WHITE, Paint.Align.LEFT, true);
        text(c, "ÚLTIMO PREMIO", 1225, 235, 15, 0xFFAEB7C8, Paint.Align.RIGHT, false); text(c, numbers.format(current == null ? 0 : current.payout) + " CR", 1225, 268, 24, 0xFFFFD76A, Paint.Align.RIGHT, true);
        line(c, 1045, 315, 1235, 315, 0x55FFD76A, 1); text(c, featureActive ? "BONUS ACTIVO" : "PREMIO", 1140, 355, 17, featureActive ? 0xFFFF6A76 : 0xFFAEB7C8, Paint.Align.CENTER, true); text(c, numbers.format(featureActive ? featureWin : shownWin) + " CR", 1140, 398, 31, Color.WHITE, Paint.Align.CENTER, true);
        drawSpinButton(c, now); text(c, phase == Phase.IDLE ? (featureActive ? "GIRO GRATIS" : "TOCA PARA GIRAR") : "TOCA PARA OMITIR", 1140, 681, 14, 0xFFAEB7C8, Paint.Align.CENTER, false);
    }
    private void drawSpinButton(Canvas c, long now) {
        boolean ready = phase == Phase.IDLE; float procedural = ready ? LandscapeAnimationMath.breathe(now, 3, 2200L, .025f) : 1f; float total = procedural * buttonHolder.getValue(); c.save(); c.scale(total, total, 1145, 555);
        p.setShadowLayer(30, 0, 8, ready ? 0xCCFFD76A : 0x996B47C5); p.setShader(new RadialGradient(1145, 535, 115, ready ? new int[]{0xFFFFFFC0, 0xFFFFB632, 0xFF8C4A0E} : new int[]{0xFFB28AFF, 0xFF57308C, 0xFF26123E}, null, Shader.TileMode.CLAMP)); c.drawCircle(1145, 555, 102, p); p.clearShadowLayer(); p.setShader(null);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(5); p.setColor(0xFFFFE8A0); c.drawCircle(1145, 555, 90, p); p.setStrokeWidth(2); p.setColor(0xAA6D3E0E); c.drawCircle(1145, 555, 78, p); p.setStyle(Paint.Style.FILL);
        float sweep = LandscapeAnimationMath.shimmer(now, 9, 2600L); c.save(); c.rotate(sweep * 360f, 1145, 555); p.setShader(new LinearGradient(1055, 555, 1235, 555, new int[]{0x00FFFFFF, 0x88FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP)); c.drawOval(new RectF(1070, 500, 1220, 535), p); p.setShader(null); c.restore();
        text(c, phase == Phase.IDLE ? (featureActive ? "GRATIS" : "GIRAR") : "OMITIR", 1145, 573, phase == Phase.IDLE ? 34 : 25, 0xFF3A1707, Paint.Align.CENTER, true); c.restore();
    }
    private void drawBottomStatus(Canvas c) {
        p.setShader(new LinearGradient(235, 675, 1000, 710, new int[]{0xFF03050A, 0xFF181020, 0xFF03050A}, null, Shader.TileMode.CLAMP)); c.drawRoundRect(new RectF(235, 665, 1000, 708), 16, 16, p); p.setShader(null); text(c, message, 617, 693, 17, phase == Phase.FEATURE_INTRO ? 0xFFFF5B68 : 0xFFFFD76A, Paint.Align.CENTER, true); text(c, "CRÉDITOS FICTICIOS · RESULTADO PRECALCULADO · 20 LÍNEAS", 617, 713, 11, 0xFF81889A, Paint.Align.CENTER, false);
    }
    private void drawFeatureIntro(Canvas c, long now) {
        long elapsed = now - phaseStart; float t = LandscapeAnimationMath.smoothstep(elapsed / 1100f); p.setColor(withAlpha(0xFF020206, Math.round(205 * t))); c.drawRect(0, 0, W, H, p); int accent = featureRetrigger ? 0xFF58DFFF : 0xFFFF3E55; int particles = LandscapeAnimationMath.particleBudget(qualityTier, false, 80);
        for (int i = 0; i < particles; i++) { float angle = (float) (Math.PI * 2 * i / particles + elapsed * .0012); float radius = 80 + t * (100 + (i % 9) * 18); float x = 640 + (float) Math.cos(angle) * radius; float y = 360 + (float) Math.sin(angle) * radius * .58f; p.setColor(withAlpha(i % 3 == 0 ? accent : 0xFFFFD76A, Math.round(210 * (1f - Math.abs(.5f - t))))); c.drawCircle(x, y, 2 + (i % 4), p); }
        float scaleIn = LandscapeAnimationMath.easeOutBack(Math.min(1f, elapsed / 900f)); c.save(); c.scale(scaleIn, scaleIn, 640, 355); p.setShadowLayer(42, 0, 8, withAlpha(accent, 220)); p.setShader(new LinearGradient(340, 270, 940, 460, featureRetrigger ? new int[]{0xFF06243B, 0xFF38CFFF, 0xFF07243A} : new int[]{0xFF5B0715, 0xFFFF344F, 0xFF5B0715}, null, Shader.TileMode.MIRROR)); c.drawRoundRect(new RectF(330, 250, 950, 485), 34, 34, p); p.clearShadowLayer(); p.setShader(null); text(c, featureRetrigger ? "WILD RETRIGGER" : "ROYAL FEATURE", 640, 330, 43, Color.WHITE, Paint.Align.CENTER, true); text(c, featureRetrigger ? "+10 FREE SPINS" : "30 FREE SPINS", 640, 415, 66, 0xFFFFE177, Paint.Align.CENTER, true); text(c, featureRetrigger ? "EL BONUS CONTINÚA" : "ENTRA AL PALACIO REAL", 640, 460, 20, Color.WHITE, Paint.Align.CENTER, false); c.restore();
    }
    private void drawFeatureSummary(Canvas c, long now) {
        long elapsed = now - phaseStart; float t = LandscapeAnimationMath.smoothstep(elapsed / 900f); p.setColor(withAlpha(0xFF020206, Math.round(220 * t))); c.drawRect(0, 0, W, H, p); float pulse = LandscapeAnimationMath.breathe(now, 71, 1800L, .025f); c.save(); c.scale(pulse, pulse, 640, 360); p.setShader(new LinearGradient(360, 220, 920, 500, new int[]{0xFF151022, 0xFF6A2A12, 0xFF151022}, null, Shader.TileMode.MIRROR)); c.drawRoundRect(new RectF(350, 210, 930, 510), 34, 34, p); p.setShader(null); text(c, "TOTAL BONUS", 640, 310, 42, 0xFFFFD76A, Paint.Align.CENTER, true); text(c, numbers.format(featureWin) + " CR", 640, 405, 68, Color.WHITE, Paint.Align.CENTER, true); text(c, "ROYAL FREE SPINS COMPLETADOS", 640, 465, 21, 0xFFFFD76A, Paint.Align.CENTER, false); c.restore();
    }
    private void panel(Canvas c, float left, float top, float right, float bottom, int centerColor) { p.setShadowLayer(18, 0, 5, 0x66000000); p.setShader(new LinearGradient(left, top, right, bottom, new int[]{0xFF020307, centerColor, 0xFF020307}, null, Shader.TileMode.CLAMP)); rect.set(left, top, right, bottom); c.drawRoundRect(rect, 24, 24, p); p.clearShadowLayer(); p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0xAA9E6322); c.drawRoundRect(rect, 24, 24, p); p.setStyle(Paint.Style.FILL); }
    private void drawPill(Canvas c, float left, float top, float right, float bottom, String label, int color, float textSize) { p.setShader(new LinearGradient(left, top, right, bottom, new int[]{0xFF05070C, 0xFF211323, 0xFF05070C}, null, Shader.TileMode.CLAMP)); rect.set(left, top, right, bottom); c.drawRoundRect(rect, 22, 22, p); p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(color); c.drawRoundRect(rect, 22, 22, p); p.setStyle(Paint.Style.FILL); text(c, label, (left + right) / 2f, (top + bottom) / 2f + 5, textSize, color, Paint.Align.CENTER, true); }
    private void drawCircleButton(Canvas c, float cx, float cy, float radius, String label) { p.setShader(new RadialGradient(cx - 6, cy - 7, radius * 1.4f, new int[]{0xFF29303A, 0xFF080A0F}, null, Shader.TileMode.CLAMP)); c.drawCircle(cx, cy, radius, p); p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0xFF8B7650); c.drawCircle(cx, cy, radius, p); p.setStyle(Paint.Style.FILL); text(c, label, cx, cy + 8, 27, Color.WHITE, Paint.Align.CENTER, true); }
    private void text(Canvas c, String value, float x, float y, float size, int color, Paint.Align align, boolean bold) { p.setShader(null); p.setStyle(Paint.Style.FILL); p.setTextAlign(align); p.setTextSize(size); p.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL)); p.setColor(color); p.setAlpha(Color.alpha(color)); c.drawText(value, x, y, p); p.setAlpha(255); }
    private void line(Canvas c, float x1, float y1, float x2, float y2, int color, float width) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(width); p.setColor(color); c.drawLine(x1, y1, x2, y2, p); p.setStyle(Paint.Style.FILL); }
    private void vibrate(long milliseconds) { if (vibrator == null || !vibrator.hasVibrator()) return; try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)); else vibrator.vibrate(milliseconds); } catch (SecurityException ignored) {} }
    private static boolean inside(float x, float y, float left, float top, float right, float bottom) { return x >= left && x <= right && y >= top && y <= bottom; }
    private static int symbolGlow(String symbol) { if (LandscapeGameEngine.WILD.equals(symbol)) return 0xFFFFD76A; if (LandscapeGameEngine.DIAMOND.equals(symbol)) return 0xFF55DFFF; if (LandscapeGameEngine.SEVEN.equals(symbol)) return 0xFFFF304C; if (LandscapeGameEngine.BELL.equals(symbol)) return 0xFFFFB33A; if (LandscapeGameEngine.BAR.equals(symbol)) return 0xFFFFE4A8; if (LandscapeGameEngine.K.equals(symbol)) return 0xFF48E16E; if (LandscapeGameEngine.Q.equals(symbol)) return 0xFFE44DFF; if (LandscapeGameEngine.J.equals(symbol)) return 0xFF4D8EFF; return 0xFFFF7B42; }
    private static int withAlpha(int color, int alpha) { int safe = Math.max(0, Math.min(255, alpha)); return (safe << 24) | (color & 0x00FFFFFF); }
}
