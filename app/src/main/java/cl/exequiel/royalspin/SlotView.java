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
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Random;

/** Single-game audiovisual slot prototype backed by StakeSlotEngine. */
public final class SlotView extends View {
    private enum Phase { IDLE, SPINNING, REVEALING }

    private static final float DESIGN_WIDTH = 360f;
    private static final float DESIGN_HEIGHT = 800f;
    private static final float REEL_LEFT = 20f;
    private static final float REEL_TOP = 176f;
    private static final float REEL_WIDTH = 60f;
    private static final float REEL_GAP = 4f;
    private static final float CELL_HEIGHT = 86f;
    private static final long[] REEL_STOP_MS = {850L, 1100L, 1370L, 1660L, 1980L};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Random random = new Random();
    private final StakeSlotEngine engine = new StakeSlotEngine();
    private final SharedPreferences preferences;
    private final NumberFormat numberFormat = NumberFormat.getIntegerInstance(new Locale("es", "CL"));
    private final CasinoAudio audio;

    private StakeSlotEngine.SpinResult currentResult;
    private StakeSlotEngine.SpinResult pendingResult;
    private Phase phase = Phase.IDLE;
    private long phaseStartedAt;
    private final boolean[] reelStopNotified = new boolean[StakeSlotEngine.REEL_COUNT];
    private boolean resultSoundPlayed;

    private int credits;
    private int betPerLine;
    private int lastWin;
    private int displayedWin;
    private String message = "20 líneas activas · toca GIRAR";

    private float scale = 1f;
    private float offsetX;
    private float offsetY;

    public SlotView(Context context) {
        super(context);
        preferences = context.getSharedPreferences("royal_spin_stake_slot", Context.MODE_PRIVATE);
        credits = preferences.getInt("credits", 2500);
        betPerLine = StakeSlotEngine.clampBet(preferences.getInt("bet_per_line", 1));
        boolean soundEnabled = preferences.getBoolean("sound_enabled", true);
        audio = new CasinoAudio(soundEnabled);
        currentResult = engine.spin(new Random(20260722L), betPerLine);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        paint.setStrokeCap(Paint.Cap.ROUND);
        setFocusable(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        updateAnimation(now);

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
        canvas.restore();

        if (phase != Phase.IDLE) {
            postInvalidateOnAnimation();
        }
    }

    private void updateAnimation(long now) {
        if (phase == Phase.SPINNING) {
            long elapsed = now - phaseStartedAt;
            for (int reel = 0; reel < REEL_STOP_MS.length; reel++) {
                if (!reelStopNotified[reel] && elapsed >= REEL_STOP_MS[reel]) {
                    reelStopNotified[reel] = true;
                    audio.playReelStop(reel);
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }
            }

            if (elapsed >= REEL_STOP_MS[REEL_STOP_MS.length - 1] + 180L) {
                currentResult = pendingResult;
                pendingResult = null;
                lastWin = currentResult.totalPayout;
                credits += lastWin;
                displayedWin = 0;
                resultSoundPlayed = false;
                phase = Phase.REVEALING;
                phaseStartedAt = now;
                saveState();
            }
        } else if (phase == Phase.REVEALING) {
            long elapsed = now - phaseStartedAt;
            displayedWin = lastWin <= 0 ? 0 : Math.min(lastWin, Math.round(lastWin * Math.min(1f, elapsed / 950f)));

            if (!resultSoundPlayed && elapsed >= 120L) {
                resultSoundPlayed = true;
                if (lastWin > 0) {
                    audio.playWin(currentResult.payoutMultiplier());
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } else {
                    audio.playLose();
                }
            }

            long revealDuration = lastWin > 0 ? 2400L : 1050L;
            if (elapsed >= revealDuration) {
                displayedWin = lastWin;
                phase = Phase.IDLE;
                if (lastWin > 0) {
                    message = "Ganaste " + numberFormat.format(lastWin) + " CR · "
                            + formatMultiplier(currentResult.payoutMultiplier()) + "x";
                } else {
                    message = "Sin premio · cada giro es independiente";
                }
            }
        }
    }

    private void drawBackground(Canvas canvas, long now) {
        paint.setShader(new LinearGradient(0, 0, 360, 800,
                new int[]{0xFF03060C, 0xFF10102A, 0xFF05070D},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 360, 800, paint);
        paint.setShader(null);

        float movement = phase == Phase.IDLE ? 0f : (now - phaseStartedAt) / 70f;
        for (int i = 0; i < 32; i++) {
            float x = (i * 83f + movement * (1 + i % 3)) % 360f;
            float y = (i * 131f + movement * (1 + i % 4) * 0.45f) % 800f;
            paint.setColor(i % 4 == 0 ? 0x66F6C453 : 0x335D70FF);
            canvas.drawCircle(x, y, i % 5 == 0 ? 1.7f : 1f, paint);
        }
    }

    private void drawHeader(Canvas canvas) {
        crown(canvas, 180, 28, 22, 0xFFF6C453);
        text(canvas, "ROYAL SPIN", 180, 65, 27, 0xFFF6C453, true, Paint.Align.CENTER);
        text(canvas, "STAKE-STYLE MATH · DEMO SIN DINERO REAL", 180, 82, 8, 0xFF9CA4B7, true, Paint.Align.CENTER);

        button(canvas, 14, 24, 78, 55, "RESET", 0xFF242A38, 0xFFE8EAF0, 8);
        button(canvas, 282, 24, 346, 55, audio.isEnabled() ? "SFX ON" : "SFX OFF",
                audio.isEnabled() ? 0xFF4A3413 : 0xFF242A38,
                audio.isEnabled() ? 0xFFF6C453 : 0xFF9097A6, 8);

        panel(canvas, 16, 96, 344, 151, 17, 0xE80A0E18, 0xFF8B651D);
        text(canvas, "SALDO", 34, 118, 8, 0xFF9097A6, true, Paint.Align.LEFT);
        text(canvas, numberFormat.format(credits) + " CR", 34, 141, 20, Color.WHITE, true, Paint.Align.LEFT);
        text(canvas, "RTP TEÓRICO", 251, 118, 8, 0xFF9097A6, true, Paint.Align.CENTER);
        text(canvas, "95,48%", 251, 141, 16, 0xFFF6C453, true, Paint.Align.CENTER);
        text(canvas, "5×3 · 20 LÍNEAS", 331, 141, 7, 0xFF81899A, true, Paint.Align.RIGHT);
    }

    private void drawReelMachine(Canvas canvas, long now) {
        panel(canvas, 14, 162, 346, 478, 22, 0xF20A0E18, 0xFF8D6820);
        paint.setShadowLayer(14, 0, 5, 0x99000000);
        paint.setColor(0xFFEEE8D8);
        canvas.drawRoundRect(new RectF(18, 172, 342, 442), 16, 16, paint);
        paint.clearShadowLayer();

        long elapsed = now - phaseStartedAt;
        for (int reel = 0; reel < StakeSlotEngine.REEL_COUNT; reel++) {
            float left = REEL_LEFT + reel * (REEL_WIDTH + REEL_GAP);
            RectF clip = new RectF(left, REEL_TOP, left + REEL_WIDTH, REEL_TOP + CELL_HEIGHT * 3f);
            canvas.save();
            canvas.clipRect(clip);

            boolean spinning = phase == Phase.SPINNING && elapsed < REEL_STOP_MS[reel];
            if (spinning) {
                drawSpinningReel(canvas, reel, left, elapsed);
            } else {
                StakeSlotEngine.SpinResult source = phase == Phase.SPINNING && pendingResult != null
                        ? pendingResult : currentResult;
                drawStoppedReel(canvas, source, reel, left);
            }
            canvas.restore();

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f);
            paint.setColor(reel == 2 ? 0xFFF6C453 : 0xFF6B7180);
            canvas.drawRoundRect(clip, 8, 8, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        drawActiveWinLine(canvas, now);
        text(canvas, "3+ símbolos iguales desde el carrete izquierdo · ♛ sustituye", 180, 463,
                8, 0xFFAEB4C1, false, Paint.Align.CENTER);
    }

    private void drawSpinningReel(Canvas canvas, int reel, float left, long elapsed) {
        float velocity = 0.70f + reel * 0.06f;
        float travel = (elapsed * velocity) % CELL_HEIGHT;
        int baseIndex = (int) (elapsed / 58L) + reel * 3;
        for (int item = -1; item <= 3; item++) {
            int symbolIndex = Math.floorMod(baseIndex + item, StakeSlotEngine.SYMBOLS.length);
            float top = REEL_TOP + item * CELL_HEIGHT + travel;
            drawSymbolCell(canvas, left + 2, top + 2, REEL_WIDTH - 4, CELL_HEIGHT - 4,
                    StakeSlotEngine.SYMBOLS[symbolIndex]);
        }
    }

    private void drawStoppedReel(Canvas canvas, StakeSlotEngine.SpinResult result, int reel, float left) {
        for (int row = 0; row < StakeSlotEngine.ROW_COUNT; row++) {
            String symbol = result.board[reel][row];
            drawSymbolCell(canvas, left + 2, REEL_TOP + row * CELL_HEIGHT + 2,
                    REEL_WIDTH - 4, CELL_HEIGHT - 4, symbol);
        }
    }

    private void drawSymbolCell(Canvas canvas, float left, float top, float width, float height,
                                String symbol) {
        int symbolColor = StakeSlotEngine.symbolColor(symbol);
        paint.setShader(new LinearGradient(left, top, left, top + height,
                new int[]{0xFFFFFFFF, 0xFFF4EEDC, 0xFFE6DFCF}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(left, top, left + width, top + height), 7, 7, paint);
        paint.setShader(null);

        String label = StakeSlotEngine.displayLabel(symbol);
        float size = label.length() >= 4 ? 13f : label.length() >= 3 ? 18f : 31f;
        text(canvas, label, left + width / 2f, top + height / 2f + size * 0.34f,
                size, symbolColor, true, Paint.Align.CENTER);
    }

    private void drawActiveWinLine(Canvas canvas, long now) {
        if (phase != Phase.REVEALING || currentResult.lineWins.isEmpty()) return;
        long elapsed = now - phaseStartedAt;
        int index = (int) ((elapsed / 520L) % currentResult.lineWins.size());
        StakeSlotEngine.LineWin win = currentResult.lineWins.get(index);

        path.reset();
        for (int reel = 0; reel < StakeSlotEngine.REEL_COUNT; reel++) {
            float cx = REEL_LEFT + reel * (REEL_WIDTH + REEL_GAP) + REEL_WIDTH / 2f;
            float cy = REEL_TOP + win.rows[reel] * CELL_HEIGHT + CELL_HEIGHT / 2f;
            if (reel == 0) path.moveTo(cx, cy); else path.lineTo(cx, cy);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4.5f);
        paint.setColor(0xFFF6C453);
        paint.setShadowLayer(9, 0, 0, 0xFFF6C453);
        canvas.drawPath(path, paint);
        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.FILL);

        for (int reel = 0; reel < win.count; reel++) {
            float cx = REEL_LEFT + reel * (REEL_WIDTH + REEL_GAP) + REEL_WIDTH / 2f;
            float cy = REEL_TOP + win.rows[reel] * CELL_HEIGHT + CELL_HEIGHT / 2f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            paint.setColor(0xFFFFF0A8);
            canvas.drawCircle(cx, cy, 24f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        panel(canvas, 105, 405, 255, 435, 15, 0xEE171006, 0xFFF6C453);
        text(canvas, "LÍNEA " + (win.lineIndex + 1) + " · +" + win.payout + " CR",
                180, 425, 10, 0xFFFFE7A0, true, Paint.Align.CENTER);
    }

    private void drawControls(Canvas canvas) {
        panel(canvas, 16, 494, 344, 666, 20, 0xE80A0E18, 0xFF394052);

        text(canvas, "APUESTA POR LÍNEA", 180, 520, 9, 0xFF9CA4B4, true, Paint.Align.CENTER);
        button(canvas, 28, 536, 86, 590, "−", 0xFF252B39, Color.WHITE, 24);
        panel(canvas, 103, 536, 257, 590, 15, 0xFF151B28, 0xFF8D6820);
        text(canvas, numberFormat.format(betPerLine) + " CR", 180, 570, 22, 0xFFF6C453, true, Paint.Align.CENTER);
        button(canvas, 274, 536, 332, 590, "+", 0xFF252B39, Color.WHITE, 24);

        int totalBet = betPerLine * StakeSlotEngine.LINE_COUNT;
        int winToShow = phase == Phase.REVEALING ? displayedWin : lastWin;
        text(canvas, "APUESTA TOTAL", 42, 615, 8, 0xFF8F97A8, true, Paint.Align.LEFT);
        text(canvas, numberFormat.format(totalBet) + " CR", 42, 640, 16, Color.WHITE, true, Paint.Align.LEFT);
        text(canvas, "ÚLTIMO PREMIO", 318, 615, 8, 0xFF8F97A8, true, Paint.Align.RIGHT);
        text(canvas, numberFormat.format(winToShow) + " CR", 318, 640, 16,
                winToShow > 0 ? 0xFFF6C453 : Color.WHITE, true, Paint.Align.RIGHT);

        text(canvas, message, 180, 688, 10, 0xFFD8DBE3, true, Paint.Align.CENTER);
        int buttonColor = phase == Phase.IDLE ? 0xFFF6C453 : 0xFF5D5130;
        String buttonText = phase == Phase.IDLE ? "GIRAR" : phase == Phase.SPINNING ? "GIRANDO…" : "PAGANDO…";
        button(canvas, 36, 704, 324, 762, buttonText, buttonColor,
                phase == Phase.IDLE ? 0xFF161006 : 0xFFB4AA8B, 17);
        text(canvas, "Créditos ficticios · resultados independientes · sin depósitos ni retiros",
                180, 788, 7, 0xFF727A8C, false, Paint.Align.CENTER);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        float x = (event.getX() - offsetX) / scale;
        float y = (event.getY() - offsetY) / scale;

        if (x >= 282 && x <= 346 && y >= 20 && y <= 62) {
            audio.setEnabled(!audio.isEnabled());
            preferences.edit().putBoolean("sound_enabled", audio.isEnabled()).apply();
            if (audio.isEnabled()) audio.playTap();
            invalidate();
            return true;
        }

        if (x >= 14 && x <= 82 && y >= 20 && y <= 62 && phase == Phase.IDLE) {
            credits = 2500;
            lastWin = 0;
            displayedWin = 0;
            message = "Saldo demo reiniciado";
            audio.playTap();
            saveState();
            invalidate();
            return true;
        }

        if (phase != Phase.IDLE) return true;

        if (y >= 526 && y <= 600 && x <= 100) {
            betPerLine = StakeSlotEngine.clampBet(betPerLine - 1);
            message = "Apuesta total: " + (betPerLine * StakeSlotEngine.LINE_COUNT) + " CR";
            audio.playTap();
            saveState();
        } else if (y >= 526 && y <= 600 && x >= 260) {
            betPerLine = StakeSlotEngine.clampBet(betPerLine + 1);
            message = "Apuesta total: " + (betPerLine * StakeSlotEngine.LINE_COUNT) + " CR";
            audio.playTap();
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
            performHapticFeedback(HapticFeedbackConstants.REJECT);
            return;
        }

        credits -= totalBet;
        pendingResult = engine.spin(random, betPerLine);
        phase = Phase.SPINNING;
        phaseStartedAt = SystemClock.uptimeMillis();
        lastWin = 0;
        displayedWin = 0;
        message = "Resultado calculado · animando carretes";
        for (int i = 0; i < reelStopNotified.length; i++) reelStopNotified[i] = false;
        audio.playSpinStart();
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        saveState();
        postInvalidateOnAnimation();
    }

    private void saveState() {
        preferences.edit()
                .putInt("credits", credits)
                .putInt("bet_per_line", betPerLine)
                .putBoolean("sound_enabled", audio.isEnabled())
                .apply();
    }

    private String formatMultiplier(double value) {
        if (value >= 100) return String.format(Locale.US, "%.0f", value);
        if (value >= 10) return String.format(Locale.US, "%.1f", value);
        return String.format(Locale.US, "%.2f", value);
    }

    public void release() {
        audio.release();
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
        paint.setShadowLayer(7, 0, 3, (fill & 0x00FFFFFF) | 0x55000000);
        paint.setColor(fill);
        canvas.drawRoundRect(new RectF(left, top, right, bottom), (bottom - top) / 2f,
                (bottom - top) / 2f, paint);
        paint.clearShadowLayer();
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
