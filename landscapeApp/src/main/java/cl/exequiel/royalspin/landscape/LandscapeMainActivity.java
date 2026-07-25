package cl.exequiel.royalspin.landscape;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cold-start-safe landscape activity.
 *
 * The original build created nine vector bitmaps and two full-screen hardware layers inside
 * onCreate(). That worked in the CI emulator but could stall or abort startup on some physical
 * GPUs. This activity paints a loading frame immediately, prepares the renderer away from the
 * UI thread and attaches only one interactive view with no forced hardware layer.
 */
public final class LandscapeMainActivity extends Activity {
    private LandscapeSlotView gameView;
    private ExecutorService loader;
    private volatile boolean destroyed;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setBackgroundDrawableResource(android.R.color.black);
        applyImmersive();
        setContentView(createLoadingView());

        String demo = getIntent() == null ? null : getIntent().getStringExtra("demo");
        loader = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "royal-landscape-loader");
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
        loader.execute(() -> prepareRenderer(demo));
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
        subtitle.setText("Preparando experiencia horizontal…");
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

    private void prepareRenderer(String demo) {
        LandscapeSlotView prepared = null;
        Throwable failure = null;
        try {
            // Bitmap generation is the expensive part. The view is not attached or drawn here.
            prepared = new LandscapeSlotView(this, demo);
        } catch (Throwable error) {
            failure = error;
        }

        final LandscapeSlotView ready = prepared;
        final Throwable startupFailure = failure;
        runOnUiThread(() -> {
            if (destroyed) {
                if (ready != null) ready.release();
                return;
            }
            if (startupFailure != null || ready == null) {
                showStartupError(startupFailure);
                return;
            }

            // Do not allocate a second full-screen RenderNode/layer on vendor GPUs.
            ready.setLayerType(View.LAYER_TYPE_NONE, null);
            gameView = ready;

            FrameLayout root = new FrameLayout(this);
            root.setBackgroundColor(Color.BLACK);
            root.addView(gameView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            setContentView(root);
            applyImmersive();
            gameView.onHostResume();
        });
    }

    private void showStartupError(Throwable failure) {
        TextView message = new TextView(this);
        String detail = failure == null ? "desconocido" : failure.getClass().getSimpleName();
        message.setText("Royal Spin no pudo iniciar el renderer.\nModo seguro activo.\nDetalle: " + detail);
        message.setTextColor(Color.WHITE);
        message.setTextSize(18f);
        message.setGravity(Gravity.CENTER);
        message.setPadding(48, 48, 48, 48);
        message.setBackgroundColor(0xFF090B12);
        setContentView(message);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    @Override protected void onResume() {
        super.onResume();
        applyImmersive();
        if (gameView != null) gameView.onHostResume();
    }

    @Override protected void onPause() {
        if (gameView != null) gameView.onHostPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        if (loader != null) loader.shutdownNow();
        if (gameView != null) gameView.release();
        super.onDestroy();
    }

    private void applyImmersive() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
}
