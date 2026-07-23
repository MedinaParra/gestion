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
import android.view.MotionEvent;
import android.view.View;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Random;

public class CasinoView extends View {
    private static final int HOME = 0, SLOTS = 1, BLACKJACK = 2, ROULETTE = 3, POKER = 4;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final SharedPreferences prefs;
    private final NumberFormat format = NumberFormat.getIntegerInstance(new Locale("es", "CL"));
    private final String[] symbols = {"7", "BAR", "A", "K", "★"};
    private final String[] reels = {"7", "★", "BAR"};
    private int screen = HOME;
    private int credits;
    private int rouletteChoice = -1;
    private boolean dailyClaimed;
    private String message = "Elige un juego";
    private float scale = 1f, offsetX = 0f, offsetY = 0f;

    public CasinoView(Context context) {
        super(context);
        prefs = context.getSharedPreferences("royal_spin_demo", Context.MODE_PRIVATE);
        credits = prefs.getInt("credits", 2500);
        dailyClaimed = prefs.getBoolean("daily_claimed", false);
        paint.setStrokeCap(Paint.Cap.ROUND);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        scale = Math.min(getWidth() / 360f, getHeight() / 800f);
        offsetX = (getWidth() - 360f * scale) / 2f;
        offsetY = (getHeight() - 800f * scale) / 2f;
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);
        drawBackground(canvas);
        if (screen == HOME) drawHome(canvas); else drawGame(canvas);
        canvas.restore();
        postInvalidateDelayed(50);
    }

    private void drawBackground(Canvas c) {
        paint.setShader(new LinearGradient(0, 0, 360, 800,
                new int[]{0xFF03060C, 0xFF0A0D1C, 0xFF05070D}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 360, 800, paint);
        paint.setShader(null);
        long t = System.currentTimeMillis() / 55L;
        for (int i = 0; i < 28; i++) {
            float x = (i * 79 + t * (i % 3 + 1)) % 360;
            float y = (i * 137 + t * (i % 4 + 1)) % 800;
            paint.setColor(i % 4 == 0 ? 0x66F6C453 : 0x337E64D8);
            c.drawCircle(x, y, i % 4 == 0 ? 1.6f : 1f, paint);
        }
    }

    private void drawHome(Canvas c) {
        crown(c, 180, 25, 23, 0xFFF6C453);
        text(c, "ROYAL SPIN", 180, 62, 27, 0xFFF6C453, true, Paint.Align.CENTER);
        text(c, "DEMO SOCIAL · SIN DINERO REAL", 180, 79, 8, 0xFF8E94A3, true, Paint.Align.CENTER);

        panel(c, 16, 94, 344, 162, 18, 0xFF0B0F18, 0xFF9B6A18);
        chip(c, 49, 128, 20, 0xFFF6C453);
        text(c, "SALDO DE DEMOSTRACIÓN", 78, 117, 9, 0xFF9DA3B1, true, Paint.Align.LEFT);
        text(c, format.format(credits) + " CR", 78, 143, 24, Color.WHITE, true, Paint.Align.LEFT);
        button(c, 257, 111, 329, 145, "REINICIAR", 0xFF6E4A13, 0xFFF6C453, 9);

        paint.setShader(new LinearGradient(16, 176, 344, 316,
                new int[]{0xFF311054, 0xFF12152D, 0xFF2B0E49}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(16, 176, 344, 316), 20, 20, paint);
        paint.setShader(null);
        stroke(c, 16, 176, 344, 316, 20, dailyClaimed ? 0xFF4A4E5D : 0xFF9A45E0);
        text(c, dailyClaimed ? "BONO COBRADO" : "BONO DIARIO", 36, 208, 15, 0xFFF8D778, true, Paint.Align.LEFT);
        text(c, dailyClaimed ? "Vuelve mañana" : "+250 CR", 36, 251, 33, Color.WHITE, true, Paint.Align.LEFT);
        text(c, "Créditos ficticios para probar la app", 36, 274, 10, 0xFFCAC6DB, false, Paint.Align.LEFT);
        button(c, 34, 286, 142, 309, dailyClaimed ? "COBRADO" : "COBRAR", dailyClaimed ? 0xFF343744 : 0xFFF6C453, dailyClaimed ? 0xFF8F94A1 : 0xFF171007, 10);
        chip(c, 278, 244, 43, 0xFFF6C453);
        crown(c, 278, 236, 31, 0xFF171007);

        text(c, "JUEGOS DESTACADOS", 18, 348, 15, 0xFFF0F1F5, true, Paint.Align.LEFT);
        gameCard(c, 16, 365, 174, 476, "TRAGAMONEDAS", "7  ★  BAR", 0xFF9745FF);
        gameCard(c, 186, 365, 344, 476, "BLACKJACK", "A  ♠  K", 0xFF27C978);
        gameCard(c, 16, 489, 174, 600, "RULETA", "0  •  36", 0xFFE33A4A);
        gameCard(c, 186, 489, 344, 600, "PÓKER", "A  K  Q  J", 0xFF378AFF);

        panel(c, 16, 616, 344, 704, 17, 0xFF0B0E15, 0xFF9B6A18);
        text(c, "JACKPOT VISUAL", 35, 645, 12, 0xFFF6C453, true, Paint.Align.LEFT);
        text(c, "18.756.829 CR", 35, 681, 27, 0xFFFFE5A0, true, Paint.Align.LEFT);
        crown(c, 302, 658, 34, 0xFFF6C453);

        paint.setColor(0xF20A0D14);
        c.drawRect(0, 724, 360, 800, paint);
        String[] nav = {"♛\nINICIO", "♠\nJUEGOS", "★\nPREMIOS", "▣\nSALDO", "●\nPERFIL"};
        for (int i = 0; i < nav.length; i++) {
            String[] parts = nav[i].split("\\n");
            int color = i == 0 ? 0xFFF6C453 : 0xFF8D93A0;
            text(c, parts[0], 36 + i * 72, 754, 18, color, true, Paint.Align.CENTER);
            text(c, parts[1], 36 + i * 72, 777, 8, color, true, Paint.Align.CENTER);
        }
    }

    private void gameCard(Canvas c, float l, float t, float r, float b, String title, String glyph, int accent) {
        paint.setShader(new LinearGradient(l, t, r, b,
                new int[]{alpha(accent, 0.36f), 0xFF0B101A, alpha(accent, 0.16f)}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(l, t, r, b), 17, 17, paint);
        paint.setShader(null);
        stroke(c, l, t, r, b, 17, accent);
        text(c, glyph, (l + r) / 2, t + 47, 23, 0xFFF7F5EF, true, Paint.Align.CENTER);
        text(c, title, (l + r) / 2, b - 28, 13, Color.WHITE, true, Paint.Align.CENTER);
        text(c, "TOCAR PARA JUGAR", (l + r) / 2, b - 11, 7, accent, true, Paint.Align.CENTER);
    }

    private void drawGame(Canvas c) {
        button(c, 16, 20, 83, 54, "‹ ATRÁS", 0xFF242936, 0xFFF0F1F4, 9);
        text(c, title(), 180, 87, 27, 0xFFF6C453, true, Paint.Align.CENTER);
        text(c, format.format(credits) + " CR", 344, 45, 13, Color.WHITE, true, Paint.Align.RIGHT);
        text(c, "CRÉDITOS FICTICIOS", 180, 107, 8, 0xFF8D93A1, true, Paint.Align.CENTER);
        panel(c, 16, 128, 344, 638, 23, 0xE60B0F19, accent());

        if (screen == SLOTS) drawSlots(c);
        if (screen == BLACKJACK) drawBlackjack(c);
        if (screen == ROULETTE) drawRoulette(c);
        if (screen == POKER) drawPoker(c);

        text(c, message, 180, 673, 13, 0xFFE8E9EE, true, Paint.Align.CENTER);
        button(c, 45, 696, 315, 752, playLabel(), accent(), 0xFF090B10, 15);
        text(c, costLabel(), 180, 777, 9, 0xFF7E8492, false, Paint.Align.CENTER);
    }

    private void drawSlots(Canvas c) {
        text(c, "PREMIO MÁXIMO 500 CR", 180, 165, 11, 0xFFB7A568, true, Paint.Align.CENTER);
        for (int i = 0; i < 3; i++) {
            float l = 40 + i * 98;
            panel(c, l, 220, l + 84, 356, 15, 0xFFF4F0E7, i == 1 ? 0xFFF6C453 : 0xFF5F6675);
            text(c, reels[i], l + 42, 302, reels[i].equals("BAR") ? 25 : 39, 0xFF17110A, true, Paint.Align.CENTER);
        }
        chip(c, 180, 492, 42, 0xFFF6C453);
        text(c, "25", 180, 501, 20, 0xFF17110A, true, Paint.Align.CENTER);
    }

    private void drawBlackjack(Canvas c) {
        text(c, "CASA", 180, 163, 11, 0xFFA2A8B5, true, Paint.Align.CENTER);
        card(c, 106, 188, "K", "♠", -7);
        card(c, 180, 188, "?", "", 7);
        paint.setColor(0xFF343A48);
        c.drawRect(44, 363, 316, 365, paint);
        text(c, "JUGADOR", 180, 405, 11, 0xFFF6C453, true, Paint.Align.CENTER);
        card(c, 106, 432, "A", "♥", -7);
        card(c, 180, 432, "J", "♣", 7);
    }

    private void drawRoulette(Canvas c) {
        float cx = 180, cy = 330, radius = 116;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(20);
        for (int i = 0; i < 18; i++) {
            paint.setColor(i % 2 == 0 ? 0xFFCD2E3F : 0xFF12151C);
            c.drawArc(new RectF(cx - radius, cy - radius, cx + radius, cy + radius), i * 20 - 90, 18, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF6C453);
        c.drawCircle(cx, cy, 52, paint);
        paint.setColor(0xFF34230D);
        c.drawCircle(cx, cy, 17, paint);
        Path pointer = new Path();
        pointer.moveTo(cx, cy - radius - 24);
        pointer.lineTo(cx - 13, cy - radius + 5);
        pointer.lineTo(cx + 13, cy - radius + 5);
        pointer.close();
        paint.setColor(Color.WHITE);
        c.drawPath(pointer, paint);
        button(c, 42, 520, 172, 570, rouletteChoice == 0 ? "ROJO ✓" : "ROJO", 0xFFCA3040, Color.WHITE, 13);
        button(c, 188, 520, 318, 570, rouletteChoice == 1 ? "NEGRO ✓" : "NEGRO", 0xFF232731, Color.WHITE, 13);
    }

    private void drawPoker(Canvas c) {
        text(c, "MANO DE CINCO CARTAS", 180, 166, 11, 0xFFA8AEBB, true, Paint.Align.CENTER);
        String[] ranks = {"A", "K", "Q", "J", "10"};
        String[] suits = {"♠", "♥", "♦", "♣", "♠"};
        for (int i = 0; i < 5; i++) card(c, 37 + i * 57, 225 + Math.abs(2 - i) * 9, ranks[i], suits[i], (i - 2) * 4);
        text(c, "PAGOS", 180, 493, 12, 0xFFF6C453, true, Paint.Align.CENTER);
        text(c, "Pareja 45 · Trío 120 · Escalera 250", 180, 522, 10, 0xFFD5D7DE, false, Paint.Align.CENTER);
        text(c, "Escalera real 750 CR", 180, 542, 10, 0xFFD5D7DE, false, Paint.Align.CENTER);
        chip(c, 180, 589, 27, 0xFF378AFF);
    }

    private void card(Canvas c, float x, float y, String rank, String suit, float rotation) {
        c.save();
        c.rotate(rotation, x + 34, y + 49);
        paint.setShadowLayer(10, 0, 5, 0x99000000);
        paint.setColor(0xFFF7F3EA);
        c.drawRoundRect(new RectF(x, y, x + 68, y + 98), 8, 8, paint);
        paint.clearShadowLayer();
        int col = suit.equals("♥") || suit.equals("♦") ? 0xFFC72034 : 0xFF14171D;
        text(c, rank, x + 10, y + 24, 18, col, true, Paint.Align.LEFT);
        text(c, suit, x + 34, y + 65, 25, col, true, Paint.Align.CENTER);
        c.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) return true;
        float x = (event.getX() - offsetX) / scale;
        float y = (event.getY() - offsetY) / scale;
        if (screen == HOME) {
            if (y >= 94 && y <= 162 && x >= 244) {
                credits = 2500;
                message = "Saldo reiniciado";
                save();
            } else if (y >= 176 && y <= 316 && x < 160 && !dailyClaimed) {
                credits += 250;
                dailyClaimed = true;
                message = "Bono diario cobrado";
                save();
            } else if (y >= 365 && y <= 476) {
                screen = x < 180 ? SLOTS : BLACKJACK;
                message = "Toca " + playLabel();
            } else if (y >= 489 && y <= 600) {
                screen = x < 180 ? ROULETTE : POKER;
                message = screen == ROULETTE ? "Elige un color" : "Toca REPARTIR MANO";
            }
        } else {
            if (x <= 100 && y <= 70) {
                screen = HOME;
                message = "Elige un juego";
            } else if (screen == ROULETTE && y >= 500 && y <= 590) {
                rouletteChoice = x < 180 ? 0 : 1;
                message = rouletteChoice == 0 ? "Apuesta al ROJO" : "Apuesta al NEGRO";
            } else if (y >= 680 && y <= 765) {
                play();
            }
        }
        invalidate();
        return true;
    }

    private void play() {
        if (screen == SLOTS) playSlots();
        if (screen == BLACKJACK) playBlackjack();
        if (screen == ROULETTE) playRoulette();
        if (screen == POKER) playPoker();
        save();
    }

    private boolean charge(int value) {
        if (credits < value) {
            message = "Saldo insuficiente: vuelve a Inicio y reinicia";
            return false;
        }
        credits -= value;
        return true;
    }

    private void playSlots() {
        if (!charge(25)) return;
        for (int i = 0; i < 3; i++) reels[i] = symbols[random.nextInt(symbols.length)];
        int prize = 0;
        if (reels[0].equals(reels[1]) && reels[1].equals(reels[2])) prize = 500;
        else if (reels[0].equals(reels[1]) || reels[1].equals(reels[2]) || reels[0].equals(reels[2])) prize = 75;
        credits += prize;
        message = prize > 0 ? "¡Ganaste " + prize + " CR!" : "Sin premio. Prueba otra vez";
    }

    private void playBlackjack() {
        if (!charge(25)) return;
        int player = 14 + random.nextInt(8);
        int dealer = 14 + random.nextInt(9);
        if (dealer > 21 || (player <= 21 && player > dealer)) {
            credits += 50;
            message = "Jugador " + player + " · Casa " + dealer + " — ganas 50 CR";
        } else if (player == dealer) {
            credits += 25;
            message = "Empate a " + player + " — apuesta devuelta";
        } else {
            message = "Jugador " + player + " · Casa " + dealer + " — gana la casa";
        }
    }

    private void playRoulette() {
        if (rouletteChoice < 0) {
            message = "Primero elige ROJO o NEGRO";
            return;
        }
        if (!charge(20)) return;
        int n = random.nextInt(37);
        int color = n == 0 ? -1 : n % 2;
        if (color == rouletteChoice) {
            credits += 40;
            message = "Salió " + n + " — ¡ganaste 40 CR!";
        } else {
            message = "Salió " + n + (n == 0 ? " VERDE" : color == 0 ? " ROJO" : " NEGRO");
        }
    }

    private void playPoker() {
        if (!charge(30)) return;
        int roll = random.nextInt(100), prize;
        String hand;
        if (roll < 2) { hand = "Escalera real"; prize = 750; }
        else if (roll < 10) { hand = "Escalera"; prize = 250; }
        else if (roll < 25) { hand = "Trío"; prize = 120; }
        else if (roll < 55) { hand = "Pareja"; prize = 45; }
        else { hand = "Carta alta"; prize = 0; }
        credits += prize;
        message = hand + (prize > 0 ? " — premio " + prize + " CR" : " — sin premio");
    }

    private String title() {
        if (screen == SLOTS) return "TRAGAMONEDAS";
        if (screen == BLACKJACK) return "BLACKJACK";
        if (screen == ROULETTE) return "RULETA";
        return "PÓKER";
    }

    private String playLabel() {
        if (screen == SLOTS) return "GIRAR";
        if (screen == BLACKJACK) return "REPARTIR";
        if (screen == ROULETTE) return "GIRAR RULETA";
        return "REPARTIR MANO";
    }

    private String costLabel() {
        if (screen == SLOTS || screen == BLACKJACK) return "Costo: 25 créditos ficticios";
        if (screen == ROULETTE) return "Costo: 20 créditos ficticios";
        return "Costo: 30 créditos ficticios";
    }

    private int accent() {
        if (screen == SLOTS) return 0xFF9B48FF;
        if (screen == BLACKJACK) return 0xFF29D17C;
        if (screen == ROULETTE) return 0xFFE53A4B;
        return 0xFF3B8EFF;
    }

    private void save() {
        prefs.edit().putInt("credits", credits).putBoolean("daily_claimed", dailyClaimed).apply();
    }

    private void panel(Canvas c, float l, float t, float r, float b, float radius, int fill, int line) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
        stroke(c, l, t, r, b, radius, line);
    }

    private void stroke(Canvas c, float l, float t, float r, float b, float radius, int color) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.3f);
        paint.setColor(color);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void button(Canvas c, float l, float t, float r, float b, String label, int fill, int color, float size) {
        paint.setShadowLayer(7, 0, 3, alpha(fill, 0.45f));
        paint.setColor(fill);
        c.drawRoundRect(new RectF(l, t, r, b), (b - t) / 2f, (b - t) / 2f, paint);
        paint.clearShadowLayer();
        text(c, label, (l + r) / 2f, (t + b) / 2f + size * 0.34f, size, color, true, Paint.Align.CENTER);
    }

    private void chip(Canvas c, float cx, float cy, float radius, int color) {
        paint.setColor(color);
        c.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setColor(0xFF684514);
        c.drawCircle(cx, cy, radius * 0.76f, paint);
        paint.setStrokeWidth(2);
        paint.setColor(0xFFFFE8A5);
        c.drawCircle(cx, cy, radius * 0.52f, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            paint.setColor(0xFFFFEDB5);
            c.drawCircle(cx + (float) Math.cos(a) * radius * 0.88f,
                    cy + (float) Math.sin(a) * radius * 0.88f, radius * 0.07f, paint);
        }
    }

    private void crown(Canvas c, float cx, float cy, float size, int color) {
        Path path = new Path();
        path.moveTo(cx - size * 0.55f, cy + size * 0.28f);
        path.lineTo(cx - size * 0.45f, cy - size * 0.38f);
        path.lineTo(cx - size * 0.13f, cy - size * 0.02f);
        path.lineTo(cx, cy - size * 0.58f);
        path.lineTo(cx + size * 0.15f, cy - size * 0.02f);
        path.lineTo(cx + size * 0.48f, cy - size * 0.38f);
        path.lineTo(cx + size * 0.55f, cy + size * 0.28f);
        path.close();
        paint.setColor(color);
        c.drawPath(path, paint);
        c.drawRoundRect(new RectF(cx - size * 0.58f, cy + size * 0.34f,
                cx + size * 0.58f, cy + size * 0.48f), 2, 2, paint);
    }

    private void text(Canvas c, String value, float x, float y, float size, int color, boolean bold, Paint.Align align) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTextAlign(align);
        paint.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(value, x, y, paint);
    }

    private int alpha(int color, float amount) {
        int a = Math.max(0, Math.min(255, (int) (255 * amount)));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
