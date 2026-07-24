package cl.exequiel.royalspin.landscape;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Vector recreation of the approved jewel symbols. Every icon is generated at runtime as a
 * transparent layered bitmap, so the APK remains self-contained and each material can be animated.
 */
public final class IconAtlas {
    private static final int S = 256;
    private IconAtlas() {}

    public static Map<Integer, Bitmap> loadSymbols() {
        Map<Integer, Bitmap> result = new HashMap<>();
        result.put(LandscapeSlotEngine.A, letter("A", 0xFFE34B31, 0xFFFFC246));
        result.put(LandscapeSlotEngine.K, letter("K", 0xFF28B84E, 0xFFFFD565));
        result.put(LandscapeSlotEngine.Q, letter("Q", 0xFFD637DC, 0xFFFFCB58));
        result.put(LandscapeSlotEngine.J, letter("J", 0xFF2E79ED, 0xFFFFD363));
        result.put(LandscapeSlotEngine.SEVEN, seven());
        result.put(LandscapeSlotEngine.BAR, bar());
        result.put(LandscapeSlotEngine.BELL, bell());
        result.put(LandscapeSlotEngine.DIAMOND, diamond());
        result.put(LandscapeSlotEngine.WILD, wild());
        return Collections.unmodifiableMap(result);
    }

    public static Bitmap loadLogo() {
        Bitmap bitmap = Bitmap.createBitmap(620, 150, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint p = paint();
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        p.setTextSize(74);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(12);
        p.setColor(0xFF5B2B08);
        c.drawText("ROYAL SPIN", 310, 110, p);
        p.setStrokeWidth(6);
        p.setColor(0xFFFFDA72);
        c.drawText("ROYAL SPIN", 310, 110, p);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 35, 0, 120,
                new int[]{0xFFFFF1B0, 0xFFFFB532, 0xFFB65F08},
                null, Shader.TileMode.CLAMP));
        c.drawText("ROYAL SPIN", 310, 110, p);
        p.setShader(null);
        drawCrown(c, 310, 26, 38, p);
        return bitmap;
    }

    private static Bitmap letter(String text, int enamel, int gold) {
        Bitmap bitmap = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint p = paint();
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        p.setTextSize(178);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(23);
        p.setColor(0xFF4B2507);
        c.drawText(text, 128, 194, p);
        p.setStrokeWidth(13);
        p.setColor(gold);
        c.drawText(text, 128, 194, p);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 48, 0, 210,
                new int[]{lighten(enamel, 1.35f), enamel, darken(enamel, .52f)},
                null, Shader.TileMode.CLAMP));
        c.drawText(text, 128, 194, p);
        p.setShader(null);
        p.setColor(0xBBFFFFFF);
        c.drawCircle(150, 82, 5, p);
        p.setColor(0xFFFFF2A5);
        diamondShape(c, 91, 111, 7, p);
        return bitmap;
    }

    private static Bitmap seven() {
        Bitmap bitmap = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint p = paint();
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC));
        p.setTextSize(205);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(26);
        p.setColor(0xFF4B1308);
        c.drawText("7", 126, 208, p);
        p.setStrokeWidth(14);
        p.setColor(0xFFFFCD55);
        c.drawText("7", 126, 208, p);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 40, 0, 220,
                new int[]{0xFFFF6855, 0xFFE91E28, 0xFF7A080E},
                null, Shader.TileMode.CLAMP));
        c.drawText("7", 126, 208, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4);
        p.setColor(0xDDFFFFFF);
        c.drawLine(62, 85, 177, 72, p);
        p.setStyle(Paint.Style.FILL);
        return bitmap;
    }

    private static Bitmap bar() {
        Bitmap bitmap = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint p = paint();
        RectF r = new RectF(20, 64, 236, 192);
        p.setShader(new LinearGradient(20, 64, 236, 192,
                new int[]{0xFF3A3B40, 0xFFF8E0A2, 0xFF7B4B14, 0xFFE7C378},
                null, Shader.TileMode.MIRROR));
        c.drawRoundRect(r, 18, 18, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(10);
        p.setColor(0xFF5B2B08);
        c.drawRoundRect(r, 18, 18, p);
        p.setStrokeWidth(5);
        p.setColor(0xFFFFD66C);
        r.inset(6, 6);
        c.drawRoundRect(r, 13, 13, p);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(75);
        p.setColor(0xFF4D2B0C);
        c.drawText("BAR", 128, 153, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        p.setColor(0xFFFFF0B0);
        c.drawText("BAR", 128, 153, p);
        p.setStyle(Paint.Style.FILL);
        for (float x : new float[]{36, 220}) {
            p.setColor(0xFFD7DCE5);
            c.drawCircle(x, 128, 8, p);
            p.setColor(0xFF5D350D);
            c.drawCircle(x, 128, 3, p);
        }
        return bitmap;
    }

    private static Bitmap bell() {
        Bitmap bitmap = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint p = paint();
        Path bell = new Path();
        bell.moveTo(60, 170);
        bell.quadTo(78, 145, 82, 92);
        bell.quadTo(88, 42, 128, 38);
        bell.quadTo(168, 42, 174, 92);
        bell.quadTo(178, 145, 196, 170);
        bell.quadTo(128, 202, 60, 170);
        bell.close();
        p.setShader(new LinearGradient(55, 40, 205, 198,
                new int[]{0xFFFFF1A4, 0xFFFFB82D, 0xFF9A4B04, 0xFFFFD76A},
                null, Shader.TileMode.CLAMP));
        c.drawPath(bell, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(8);
        p.setColor(0xFF6E3507);
        c.drawPath(bell, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFE92335);
        diamondShape(c, 128, 88, 18, p);
        p.setColor(0xFFFFD768);
        c.drawRoundRect(new RectF(53, 160, 203, 184), 12, 12, p);
        p.setColor(0xFFB25307);
        c.drawCircle(128, 202, 18, p);
        p.setColor(0xFFFFCE55);
        c.drawCircle(128, 200, 10, p);
        return bitmap;
    }

    private static Bitmap diamond() {
        Bitmap bitmap = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint p = paint();
        Path outer = new Path();
        outer.moveTo(128, 30);
        outer.lineTo(216, 91);
        outer.lineTo(188, 184);
        outer.lineTo(128, 228);
        outer.lineTo(68, 184);
        outer.lineTo(40, 91);
        outer.close();
        p.setShader(new LinearGradient(40, 30, 216, 228,
                new int[]{0xFFD6FAFF, 0xFF36C7FF, 0xFF1764DD, 0xFF62E9FF},
                null, Shader.TileMode.MIRROR));
        c.drawPath(outer, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(8);
        p.setColor(0xFFFFD873);
        c.drawPath(outer, p);
        p.setStrokeWidth(3);
        p.setColor(0xCCFFFFFF);
        c.drawLine(40, 91, 216, 91, p);
        c.drawLine(68, 184, 188, 184, p);
        c.drawLine(128, 30, 68, 184, p);
        c.drawLine(128, 30, 188, 184, p);
        c.drawLine(40, 91, 128, 228, p);
        c.drawLine(216, 91, 128, 228, p);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new RadialGradient(104, 72, 45,
                new int[]{0xCCFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        c.drawCircle(104, 72, 44, p);
        p.setShader(null);
        return bitmap;
    }

    private static Bitmap wild() {
        Bitmap bitmap = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint p = paint();
        p.setColor(0xFF4E0612);
        c.drawRoundRect(new RectF(18, 22, 238, 235), 45, 45, p);
        drawCrown(c, 128, 91, 82, p);
        p.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(59);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(9);
        p.setColor(0xFF5A2A05);
        c.drawText("WILD", 128, 210, p);
        p.setStrokeWidth(4);
        p.setColor(0xFFFFDA67);
        c.drawText("WILD", 128, 210, p);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 160, 0, 218,
                new int[]{0xFFFFF1A6, 0xFFFFA91E, 0xFF9F5004},
                null, Shader.TileMode.CLAMP));
        c.drawText("WILD", 128, 210, p);
        p.setShader(null);
        return bitmap;
    }

    private static void drawCrown(Canvas c, float cx, float cy, float width, Paint p) {
        float h = width * .72f;
        Path crown = new Path();
        crown.moveTo(cx - width / 2, cy + h / 2);
        crown.lineTo(cx - width * .42f, cy - h * .15f);
        crown.lineTo(cx - width * .18f, cy + h * .06f);
        crown.lineTo(cx, cy - h / 2);
        crown.lineTo(cx + width * .18f, cy + h * .06f);
        crown.lineTo(cx + width * .42f, cy - h * .15f);
        crown.lineTo(cx + width / 2, cy + h / 2);
        crown.close();
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(cx - width / 2, cy - h / 2,
                cx + width / 2, cy + h / 2,
                new int[]{0xFFFFF1A4, 0xFFFFB425, 0xFFB65C05},
                null, Shader.TileMode.CLAMP));
        c.drawPath(crown, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(3f, width * .06f));
        p.setColor(0xFF6B3105);
        c.drawPath(crown, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFEE263A);
        diamondShape(c, cx, cy + h * .18f, width * .08f, p);
        p.setColor(0xFF43BFFF);
        c.drawCircle(cx - width * .24f, cy + h * .18f, width * .055f, p);
        c.drawCircle(cx + width * .24f, cy + h * .18f, width * .055f, p);
    }

    private static void diamondShape(Canvas c, float cx, float cy, float size, Paint p) {
        Path path = new Path();
        path.moveTo(cx, cy - size);
        path.lineTo(cx + size, cy);
        path.lineTo(cx, cy + size);
        path.lineTo(cx - size, cy);
        path.close();
        c.drawPath(path, p);
    }

    private static Paint paint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }

    private static int lighten(int color, float amount) {
        return Color.rgb(
                Math.min(255, Math.round(Color.red(color) * amount)),
                Math.min(255, Math.round(Color.green(color) * amount)),
                Math.min(255, Math.round(Color.blue(color) * amount)));
    }

    private static int darken(int color, float amount) {
        return Color.rgb(
                Math.max(0, Math.round(Color.red(color) * amount)),
                Math.max(0, Math.round(Color.green(color) * amount)),
                Math.max(0, Math.round(Color.blue(color) * amount)));
    }
}
