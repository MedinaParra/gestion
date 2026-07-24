package cl.exequiel.royalspin;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private RoyalSpinPremiumShell gameShell;
    private RoyalVfxShowcaseView showcaseView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(0xFF020205);
        applyImmersiveMode();

        String qaScene = getIntent() == null ? null : getIntent().getStringExtra("qa");
        if (qaScene != null && !qaScene.trim().isEmpty()) {
            showcaseView = new RoyalVfxShowcaseView(this, qaScene);
            setContentView(showcaseView);
            return;
        }

        String demoMode = getIntent() == null ? null : getIntent().getStringExtra("demo");
        gameShell = new RoyalSpinPremiumShell(this, demoMode);
        setContentView(gameShell);
    }

    @Override protected void onResume() {
        super.onResume();
        applyImmersiveMode();
        if (gameShell != null) gameShell.onHostResume();
    }

    @Override protected void onPause() {
        if (gameShell != null) gameShell.onHostPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (gameShell != null) gameShell.release();
        if (showcaseView != null) showcaseView.release();
        super.onDestroy();
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
