package cl.exequiel.royalspin.landscape;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

public final class LandscapeMainActivity extends Activity {
    private LandscapeSlotView gameView;
    private LandscapeSpectacleOverlay spectacle;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersive();

        String demo = getIntent() == null ? null : getIntent().getStringExtra("demo");
        gameView = new LandscapeSlotView(this, demo);
        spectacle = new LandscapeSpectacleOverlay(this);

        // LandscapeRootLayout dispatches every pointer event directly to gameView,
        // so cinematic overlays can never block the GIRAR control.
        setContentView(new LandscapeRootLayout(this, gameView, spectacle));
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
        if (spectacle != null) spectacle.release();
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
