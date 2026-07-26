package cl.exequiel.royalspin.landscape;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Startup-safe activity. Android View instances are created exclusively on the main thread.
 */
public final class LandscapeMainActivity extends Activity {
    private LandscapeSlotView gameView;
    private boolean destroyed;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().setBackgroundDrawableResource(android.R.color.black);
        } catch (Throwable ignored) {
            // Vendor window implementations must never abort startup.
        }

        final View loading = createLoadingView();
        setContentView(loading);

        // Let Android present at least one simple frame first. The renderer is then constructed
        // on the UI thread, as required by the Android View contract.
        loading.post(() -> initializeGameOnMainThread(readDemo()));
    }

    private String readDemo() {
        try {
            return getIntent() == null ? null : getIntent().getStringExtra("demo");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private View createLoadingView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF030409);

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setPadding(48, 32, 48, 32);

        TextView title = new TextView(this);
        title.setText("ROYAL SPIN");
        title.setTextColor(0xFFFFD76A);
        title.setTextSize(34f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText("INICIANDO MODO HORIZONTAL…");
        subtitle.setTextColor(0xFFD8DDEA);
        subtitle.setTextSize(15f);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 18, 0, 28);

        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);

        column.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        column.addView(subtitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        column.addView(progress, new LinearLayout.LayoutParams(64, 64));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(column, params);
        return root;
    }

    private void initializeGameOnMainThread(String demo) {
        if (destroyed || isFinishing()) return;

        try {
            gameView = new LandscapeSlotView(this, demo);
            // Avoid an additional vendor-specific RenderNode. Canvas animations still run.
            gameView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

            FrameLayout root = new FrameLayout(this);
            root.setBackgroundColor(Color.BLACK);
            root.addView(gameView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            setContentView(root);
            applyImmersiveCompat();
            gameView.onHostResume();
        } catch (Throwable failure) {
            showStartupError(failure);
        }
    }

    private void showStartupError(Throwable failure) {
        try {
            TextView message = new TextView(this);
            String detail = failure == null
                    ? "desconocido"
                    : failure.getClass().getSimpleName() + ": " + safeMessage(failure);
            message.setText("ROYAL SPIN · MODO DIAGNÓSTICO\n\n"
                    + "No se pudo iniciar el renderer.\n"
                    + "Detalle: " + detail);
            message.setTextColor(Color.WHITE);
            message.setTextSize(17f);
            message.setGravity(Gravity.CENTER);
            message.setPadding(48, 48, 48, 48);
            message.setBackgroundColor(0xFF090B12);
            setContentView(message);
        } catch (Throwable ignored) {
            // At this point there is deliberately no finish() call: keep the process visible.
        }
    }

    private static String safeMessage(Throwable failure) {
        String value = failure.getMessage();
        if (value == null || value.trim().isEmpty()) return "sin mensaje";
        return value.length() > 160 ? value.substring(0, 160) : value;
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveCompat();
    }

    @Override protected void onResume() {
        super.onResume();
        applyImmersiveCompat();
        if (gameView != null) gameView.onHostResume();
    }

    @Override protected void onPause() {
        if (gameView != null) gameView.onHostPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (gameView != null) {
            try { gameView.release(); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void applyImmersiveCompat() {
        try {
            // Legacy flags are intentionally used here because they are stable from API 24
            // through current Android and avoid OEM WindowInsetsController startup defects.
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } catch (Throwable ignored) {
            // Fullscreen is cosmetic; failure must not close the game.
        }
    }
}
