package cl.exequiel.royalspin;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/** Stateless drawing math for symbol wins and Royal Feature cinematics. */
public final class RoyalVfxDirector {
    public enum Scene { NONE, FEATURE_INTRO, RETRIGGER, FEATURE_SUMMARY }

    private RoyalVfxDirector() { }

    public static void drawSymbol(Canvas c, Paint p, Path path, String symbol,
                                  float cx, float cy, int count, double multiplier,
                                  long elapsed, boolean reduced) {
        if (symbol == null || symbol.isEmpty()) return;
        SymbolAnimationDirector.Frame frame = SymbolAnimationDirector.frame(
                symbol, count, multiplier, elapsed);
        float intensity = reduced ? 0.45f : frame.intensity;
        switch (frame.style) {
            case BELL: drawBell(c, p, cx, cy, frame, count, intensity); break;
            case BAR: drawBar(c, p, cx, cy, frame, count, intensity); break;
            case SEVEN: drawSeven(c, p, cx, cy, frame, count, elapsed, intensity); break;
            case DIAMOND: drawDiamond(c, p, path, cx, cy, frame, count, elapsed, intensity); break;
            case WILD: drawWild(c, p, path, cx, cy, frame, count, elapsed, intensity); break;
            case CARD: drawCard(c, p, cx, cy, frame, intensity); break;
            default: break;
        }
    }

    public static void drawScene(Canvas c, Paint p, Path path, Scene scene,
                                 long elapsed, boolean reduced, int counter,
                                 int totalWin, int wildCount) {
        if (scene == null || scene == Scene.NONE) return;
        float enter = easeOutBack(window(elapsed, 0, reduced ? 320 : 650));
        float pulse = 0.5f + 0.5f * (float) Math.sin(elapsed * 0.0105f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(scene == Scene.RETRIGGER ? 0xD8070610 : 0xE0040610);
        c.drawRect(0, 0, 360, 800, p);

        drawPortal(c, p, path, elapsed, enter, pulse, reduced,
                scene == Scene.RETRIGGER ? 0xFF62DDFF : 0xFFFFD66B);
        drawCrown(c, p, path, 180, 235 - 18 * enter, 70 + 10 * pulse,
                scene == Scene.RETRIGGER ? 0xFF65DFFF : 0xFFFFD86F);
        if (!reduced) drawFlyingCoins(c, p, elapsed, scene == Scene.FEATURE_SUMMARY ? 54 : 34);

        if (scene == Scene.FEATURE_INTRO) {
            goldText(c, p, "ROYAL FEATURE", 180, 355, 30);
            int shown = Math.max(0, Math.min(30, counter));
            glowText(c, p, String.valueOf(shown), 180, 458, 72, Color.WHITE, 0xFF58DFFF);
            goldText(c, p, "JUEGOS GRATIS", 180, 505, 19);
            text(c, p, wildCount >= 5 ? "5 WILD · ROYAL TRIGGER"
                    : wildCount == 4 ? "4 WILD · MEGA TRIGGER"
                    : "3 WILD EN LÍNEA", 180, 540, 10, 0xFFBDEEFF);
        } else if (scene == Scene.RETRIGGER) {
            glowText(c, p, "WILD RETRIGGER", 180, 370, 28,
                    0xFFFFE49A, 0xFF58DFFF);
            glowText(c, p, "+" + Math.max(0, counter), 180, 465, 70,
                    Color.WHITE, 0xFFFFD66B);
            goldText(c, p, "JUEGOS GRATIS", 180, 512, 18);
        } else {
            goldText(c, p, "BONUS COMPLETADO", 180, 365, 27);
            glowText(c, p, formatCredits(totalWin), 180, 452, 45,
                    Color.WHITE, 0xFFFFD66B);
            text(c, p, "TOTAL GANADO EN FREE SPINS", 180, 495, 10, 0xFFBDEEFF);
            text(c, p, "RESULTADO MATEMÁTICO CERRADO", 180, 530, 8, 0xFF8CA6BE);
        }
    }

    public static float cameraZoom(double multiplier, long elapsed, boolean reduced) {
        if (reduced || multiplier < 5d) return 1f;
        float enter = easeOut(window(elapsed, 80, 520));
        float settle = 1f - easeOut(window(elapsed, 900, 900));
        float strength = multiplier >= 50 ? 0.055f : multiplier >= 15 ? 0.038f : 0.022f;
        return 1f + strength * enter * settle;
    }

    public static float cameraShake(double multiplier, long elapsed, boolean reduced) {
        if (reduced || multiplier < 5d || elapsed > 850) return 0f;
        float decay = (float) Math.exp(-elapsed / 420f);
        float strength = multiplier >= 50 ? 4.0f : multiplier >= 15 ? 2.7f : 1.5f;
        return (float) Math.sin(elapsed * 0.13f) * decay * strength;
    }

    private static void drawBell(Canvas c, Paint p, float cx, float cy,
                                 SymbolAnimationDirector.Frame f, int count, float intensity) {
        int rings = Math.max(2, Math.min(5, count));
        p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < rings; i++) {
            float q = (f.ring + i * 0.19f) % 1f;
            float radius = 25 + q * 60 * intensity;
            p.setStrokeWidth(1.2f + (1f - q) * 2.4f);
            p.setColor((Math.round(150 * (1f - q)) << 24) | 0x00FFD76A);
            c.drawArc(new RectF(cx - radius * 1.45f, cy - radius,
                    cx + radius * 1.45f, cy + radius), -40, 80, false, p);
            c.drawArc(new RectF(cx - radius * 1.45f, cy - radius,
                    cx + radius * 1.45f, cy + radius), 140, 80, false, p);
        }
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10 + f.energy;
            float r = 35 + 12 * f.energy;
            p.setColor(i % 2 == 0 ? 0xCCFFF0A0 : 0xAAFFB62E);
            c.drawCircle(cx + (float) Math.cos(a) * r,
                    cy + (float) Math.sin(a) * r, 1.5f + f.energy, p);
        }
    }

    private static void drawBar(Canvas c, Paint p, float cx, float cy,
                                SymbolAnimationDirector.Frame f, int count, float intensity) {
        float lock = easeOut(f.enter);
        float gap = 45 * (1f - lock);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(cx - 65, cy, cx + 65, cy,
                new int[]{0xFF1A0E03, 0xFFFFE59B, 0xFF895412, 0xFFFFE59B, 0xFF1A0E03},
                null, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(cx - 72 - gap, cy - 10, cx - 34 - gap, cy + 10), 4, 4, p);
        c.drawRoundRect(new RectF(cx + 34 + gap, cy - 10, cx + 72 + gap, cy + 10), 4, 4, p);
        p.setShader(null);
        int sparks = Math.max(6, count * 3);
        p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < sparks; i++) {
            double a = i * 2.39996 + f.energy;
            float len = 12 + (i % 4) * 5 * intensity;
            p.setStrokeWidth(i % 3 == 0 ? 2f : 1f);
            p.setColor(i % 2 == 0 ? 0xDFFFF0A0 : 0xAAFF8D30);
            c.drawLine(cx + (float) Math.cos(a) * 34, cy + (float) Math.sin(a) * 12,
                    cx + (float) Math.cos(a) * (34 + len),
                    cy + (float) Math.sin(a) * (12 + len * 0.45f), p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private static void drawSeven(Canvas c, Paint p, float cx, float cy,
                                  SymbolAnimationDirector.Frame f, int count,
                                  long elapsed, float intensity) {
        float slash = easeOut(window(elapsed, 80, 520));
        float x = cx - 150 + slash * 300;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(18 * intensity);
        p.setShader(new LinearGradient(cx - 130, cy + 130, cx + 130, cy - 130,
                new int[]{0x00FF2148, 0xEFFF2148, 0xFFFFD76A, 0x00FFFFFF},
                null, Shader.TileMode.CLAMP));
        c.drawLine(x - 100, cy + 135, x + 80, cy - 135, p);
        p.setShader(null);
        p.setStrokeWidth(3f);
        p.setColor(0xFFFFE7A0);
        c.drawLine(x - 96, cy + 135, x + 84, cy - 135, p);
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14 + elapsed * 0.0014;
            float r = 35 + 12 * f.energy;
            p.setColor(i % 2 == 0 ? 0xCCFF244E : 0xAAFFD76A);
            c.drawCircle(cx + (float) Math.cos(a) * r,
                    cy + (float) Math.sin(a) * r, 1.2f + intensity, p);
        }
    }

    private static void drawDiamond(Canvas c, Paint p, Path path, float cx, float cy,
                                    SymbolAnimationDirector.Frame f, int count,
                                    long elapsed, float intensity) {
        int rays = Math.max(8, count * 3);
        p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < rays; i++) {
            double a = i * Math.PI * 2 / rays + elapsed * 0.0018;
            float inner = 22;
            float outer = 52 + 28 * f.energy * intensity;
            p.setStrokeWidth(i % 3 == 0 ? 2.5f : 1f);
            p.setColor(i % 3 == 0 ? 0xCCFFFFFF
                    : i % 3 == 1 ? 0xAA58DFFF : 0x999A68FF);
            c.drawLine(cx + (float) Math.cos(a) * inner,
                    cy + (float) Math.sin(a) * inner,
                    cx + (float) Math.cos(a) * outer,
                    cy + (float) Math.sin(a) * outer, p);
        }
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI * 2 / 8 - elapsed * 0.0011;
            float r = 45 + (i % 2) * 10;
            shard(c, p, path, cx + (float) Math.cos(a) * r,
                    cy + (float) Math.sin(a) * r, 4 + f.energy * 3,
                    i % 2 == 0 ? 0xDD70E6FF : 0xCCBDA7FF);
        }
    }

    private static void drawWild(Canvas c, Paint p, Path path, float cx, float cy,
                                 SymbolAnimationDirector.Frame f, int count,
                                 long elapsed, float intensity) {
        int rays = Math.max(14, count * 5);
        p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < rays; i++) {
            double a = i * Math.PI * 2 / rays + elapsed * 0.0009;
            float inner = 28;
            float outer = 65 + 18 * f.energy * intensity;
            p.setStrokeWidth(i % 2 == 0 ? 2.2f : 1f);
            p.setColor(i % 3 == 0 ? 0xCCFFD76A
                    : i % 3 == 1 ? 0xAA5DDFFF : 0x999A68FF);
            c.drawLine(cx + (float) Math.cos(a) * inner,
                    cy + (float) Math.sin(a) * inner,
                    cx + (float) Math.cos(a) * outer,
                    cy + (float) Math.sin(a) * outer, p);
        }
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12 + elapsed * 0.0017;
            p.setColor(i % 3 == 0 ? 0xFFFF5DA8 : i % 3 == 1 ? 0xFF65DFFF : 0xFFFFD76A);
            c.drawCircle(cx + (float) Math.cos(a) * 58,
                    cy + (float) Math.sin(a) * 30, 2.2f + f.energy * 1.4f, p);
        }
        drawCrown(c, p, path, cx, cy - 48 - 8 * f.enter, 25 + 4 * f.energy, 0xFFFFD76A);
    }

    private static void drawCard(Canvas c, Paint p, float cx, float cy,
                                 SymbolAnimationDirector.Frame f, float intensity) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f + intensity);
        for (int i = 0; i < 3; i++) {
            float r = 28 + i * 8 + f.energy * 3;
            p.setColor((Math.round(85f / (i + 1)) << 24) | 0x007BDFFF);
            c.drawRoundRect(new RectF(cx - r, cy - r, cx + r, cy + r), 9, 9, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private static void drawPortal(Canvas c, Paint p, Path path, long elapsed,
                                   float enter, float pulse, boolean reduced, int accent) {
        p.setShader(new RadialGradient(180, 300, 210,
                new int[]{0x5546CFFF, 0x332D7FFF, 0x00000000}, null, Shader.TileMode.CLAMP));
        c.drawCircle(180, 300, 210, p);
        p.setShader(null);
        int rings = reduced ? 3 : 7;
        p.setStyle(Paint.Style.STROKE);
        for (int ring = 0; ring < rings; ring++) {
            float q = (elapsed * 0.00032f + ring / (float) rings) % 1f;
            float radius = 55 + q * 155;
            p.setStrokeWidth(1f + (1f - q) * 4f);
            p.setColor((Math.round(145 * (1f - q)) << 24) | (accent & 0x00FFFFFF));
            c.drawCircle(180, 300, radius * enter, p);
        }
        int rays = reduced ? 10 : 28;
        for (int i = 0; i < rays; i++) {
            double a = i * Math.PI * 2 / rays + elapsed * 0.00075;
            float inner = 65;
            float outer = 160 + pulse * 45;
            p.setStrokeWidth(i % 3 == 0 ? 2.4f : 1f);
            p.setColor(i % 2 == 0 ? 0x99FFD76A : 0x7758DFFF);
            c.drawLine(180 + (float) Math.cos(a) * inner,
                    300 + (float) Math.sin(a) * inner,
                    180 + (float) Math.cos(a) * outer,
                    300 + (float) Math.sin(a) * outer, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private static void drawFlyingCoins(Canvas c, Paint p, long elapsed, int amount) {
        for (int i = 0; i < amount; i++) {
            float life = ((elapsed * 0.00018f) + i * 0.071f) % 1f;
            float x = 18 + ((i * 83.7f + life * 265f) % 324f);
            float y = 790 - life * 710 + (float) Math.sin(i * 1.7 + life * 8) * 28;
            float size = 2.5f + (i % 5) * 0.65f;
            p.setColor((Math.round(220 * (1f - life * 0.65f)) << 24)
                    | (i % 3 == 0 ? 0x00FFFFFF : 0x00FFD76A));
            c.save();
            c.rotate(elapsed * 0.12f + i * 37f, x, y);
            c.drawOval(new RectF(x - size * 1.8f, y - size, x + size * 1.8f, y + size), p);
            c.restore();
        }
    }

    private static void drawCrown(Canvas c, Paint p, Path path, float cx, float cy,
                                  float size, int color) {
        path.reset();
        path.moveTo(cx - size * 0.58f, cy + size * 0.30f);
        path.lineTo(cx - size * 0.50f, cy - size * 0.28f);
        path.lineTo(cx - size * 0.15f, cy + size * 0.02f);
        path.lineTo(cx, cy - size * 0.55f);
        path.lineTo(cx + size * 0.16f, cy + size * 0.02f);
        path.lineTo(cx + size * 0.51f, cy - size * 0.28f);
        path.lineTo(cx + size * 0.58f, cy + size * 0.30f);
        path.close();
        p.setShader(new LinearGradient(cx - size, cy - size, cx + size, cy + size,
                new int[]{0xFFC78320, 0xFFFFF0AA, color, 0xFFFFD76A},
                null, Shader.TileMode.MIRROR));
        p.setShadowLayer(18, 0, 4, 0xCCF6B83F);
        c.drawPath(path, p);
        c.drawRoundRect(new RectF(cx - size * 0.60f, cy + size * 0.35f,
                cx + size * 0.60f, cy + size * 0.50f), 4, 4, p);
        p.clearShadowLayer();
        p.setShader(null);
    }

    private static void shard(Canvas c, Paint p, Path path, float cx, float cy,
                              float size, int color) {
        path.reset();
        path.moveTo(cx, cy - size);
        path.lineTo(cx + size * 0.65f, cy);
        path.lineTo(cx, cy + size);
        path.lineTo(cx - size * 0.65f, cy);
        path.close();
        p.setColor(color);
        c.drawPath(path, p);
    }

    private static void goldText(Canvas c, Paint p, String value, float x, float y, float size) {
        p.setShader(new LinearGradient(x - size * 2, y - size, x + size * 2, y,
                new int[]{0xFFC78320, 0xFFFFF0AA, 0xFFF6B83F, 0xFFFFE58B},
                null, Shader.TileMode.MIRROR));
        text(c, p, value, x, y, size, Color.WHITE);
        p.setShader(null);
    }

    private static void glowText(Canvas c, Paint p, String value, float x, float y,
                                 float size, int color, int glow) {
        p.setShadowLayer(22, 0, 5, glow);
        text(c, p, value, x, y, size, color);
        p.clearShadowLayer();
    }

    private static void text(Canvas c, Paint p, String value, float x, float y,
                             float size, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        p.setTextSize(size);
        p.setColor(color);
        c.drawText(value, x, y, p);
    }

    private static String formatCredits(int value) {
        return java.text.NumberFormat.getIntegerInstance(new java.util.Locale("es", "CL"))
                .format(Math.max(0, value)) + " CR";
    }

    private static float window(long elapsed, long start, long duration) {
        if (duration <= 0) return elapsed >= start ? 1f : 0f;
        return clamp((elapsed - start) / (float) duration);
    }

    private static float easeOut(float x) {
        float q = clamp(x);
        return 1f - (1f - q) * (1f - q) * (1f - q);
    }

    private static float easeOutBack(float x) {
        float q = clamp(x) - 1f;
        return 1f + 2.70158f * q * q * q + 1.70158f * q * q;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
