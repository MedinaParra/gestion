package cl.exequiel.royalspin.landscape;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

final class BitmapAssets {
    private final Map<String, Bitmap> symbols = new HashMap<>();
    final Bitmap logo;

    BitmapAssets(Context context) {
        symbols.put(LandscapeGameEngine.Q, decode(context, R.raw.icon_q));
        symbols.put(LandscapeGameEngine.WILD, decode(context, R.raw.icon_wild));
        symbols.put(LandscapeGameEngine.J, decode(context, R.raw.icon_j));
        symbols.put(LandscapeGameEngine.BELL, decode(context, R.raw.icon_bell));
        symbols.put(LandscapeGameEngine.DIAMOND, decode(context, R.raw.icon_diamond));
        symbols.put(LandscapeGameEngine.BAR, decode(context, R.raw.icon_bar));
        symbols.put(LandscapeGameEngine.SEVEN, decode(context, R.raw.icon_seven));
        symbols.put(LandscapeGameEngine.K, decode(context, R.raw.icon_k));
        symbols.put(LandscapeGameEngine.A, decode(context, R.raw.icon_a));
        logo = decode(context, R.raw.icon_logo);
    }

    Bitmap symbol(String key) {
        Bitmap bitmap = symbols.get(key);
        return bitmap != null ? bitmap : symbols.get(LandscapeGameEngine.Q);
    }

    void recycle() {
        for (Bitmap bitmap : symbols.values()) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        if (logo != null && !logo.isRecycled()) logo.recycle();
    }

    private static Bitmap decode(Context context, int rawId) {
        try (InputStream in = context.getResources().openRawResource(rawId);
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) >= 0) buffer.write(chunk, 0, read);
            byte[] encoded = buffer.toByteArray();
            byte[] decoded = Base64.decode(encoded, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
            if (bitmap == null) throw new IOException("Bitmap inválido: " + rawId);
            return bitmap;
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo cargar arte premium", error);
        }
    }
}
