package cl.exequiel.royalspin.landscape;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public final class LandscapeMainActivity extends Activity {
    private LandscapeSlotView slotView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        hideSystemUi();
        String demo = getIntent() == null ? null : getIntent().getStringExtra("demo");
        slotView = new LandscapeSlotView(this, demo);
        setContentView(slotView);
    }

    @Override protected void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override protected void onResume() {
        super.onResume();
        if (slotView != null) slotView.resume();
    }

    @Override protected void onPause() {
        if (slotView != null) slotView.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (slotView != null) slotView.release();
        super.onDestroy();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
