package cl.exequiel.royalspin.landscape;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Random;

/**
 * Royal Spin 3.2: local PixiJS/WebGL renderer with a native slot engine,
 * adaptive Canvas spectacle layer and stable Canvas fallback.
 */
public final class LandscapeMainActivity extends Activity {
    private static final String LOCAL_URL =
            "https://appassets.androidplatform.net/assets/royal3/index.html";

    private final Handler main = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private View loading;
    private WebView webView;
    private LandscapeSlotView fallbackView;
    private boolean rendererReady;
    private boolean destroyed;
    private String demoScene = "";
    private boolean forceFallback;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        updateDemoScene(getIntent());
        try {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().setBackgroundDrawableResource(android.R.color.black);
        } catch (Throwable ignored) {}

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        loading = createLoadingView();
        root.addView(loading, matchParent());
        setContentView(root);
        applyImmersiveCompat();

        // All Android views are created on the main thread. Posting only delays initialization
        // until the loading hierarchy is attached; it never creates views from a worker thread.
        if (forceFallback) loading.post(() -> showFallback("Fallback solicitado para validación"));
        else loading.post(this::initializeWebRenderer);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        updateDemoScene(intent);
    }

    private void updateDemoScene(Intent intent) {
        String requested = intent == null ? null : intent.getStringExtra("demo");
        demoScene = requested == null ? "" : requested.trim().toLowerCase(Locale.US);
        forceFallback = intent != null && intent.getBooleanExtra("force_fallback", false);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void initializeWebRenderer() {
        if (destroyed || isFinishing()) return;
        try {
            final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/",
                            new WebViewAssetLoader.AssetsPathHandler(this))
                    .build();

            webView = new WebView(this);
            webView.setBackgroundColor(Color.TRANSPARENT);
            webView.setVisibility(View.INVISIBLE);
            // Do not force an additional Android hardware layer. The application and WebView
            // are hardware accelerated already, which is safer on Samsung GPU drivers.

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setBuiltInZoomControls(false);
            settings.setDisplayZoomControls(false);
            settings.setSupportZoom(false);
            settings.setLoadWithOverviewMode(false);
            settings.setUseWideViewPort(true);
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);

            webView.setWebViewClient(new LocalClient(assetLoader));
            webView.setWebChromeClient(new WebChromeClient());
            webView.addJavascriptInterface(new NativeBridge(), "RoyalNative");
            root.addView(webView, 0, matchParent());
            webView.loadUrl(LOCAL_URL);

            main.postDelayed(() -> {
                if (!rendererReady && !destroyed) {
                    showFallback("Tiempo de inicio WebGL excedido");
                }
            }, 18_000L);
        } catch (Throwable error) {
            showFallback(error.getClass().getSimpleName());
        }
    }

    private FrameLayout.LayoutParams matchParent() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private View createLoadingView() {
        FrameLayout background = new FrameLayout(this);
        background.setBackgroundColor(0xFF02030A);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setPadding(48, 32, 48, 32);

        TextView title = new TextView(this);
        title.setText("ROYAL SPIN 3.2");
        title.setTextColor(0xFFFFDD75);
        title.setTextSize(37f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.SERIF,
                android.graphics.Typeface.BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText("ACTIVANDO ULTRA SPECTACLE");
        subtitle.setTextColor(0xFFBFD8EA);
        subtitle.setTextSize(13f);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLetterSpacing(.16f);
        subtitle.setPadding(0, 18, 0, 26);

        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        column.addView(title);
        column.addView(subtitle);
        column.addView(progress, new LinearLayout.LayoutParams(64, 64));
        background.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        return background;
    }

    private void onRendererReady(String engine) {
        main.post(() -> {
            if (destroyed || webView == null) return;
            rendererReady = true;
            webView.setVisibility(View.VISIBLE);
            if (loading != null) {
                root.removeView(loading);
                loading = null;
            }
            applyImmersiveCompat();
        });
    }

    private void showFallback(String reason) {
        main.post(() -> {
            if (destroyed || fallbackView != null) return;
            rendererReady = false;
            if (webView != null) {
                try {
                    root.removeView(webView);
                    webView.removeJavascriptInterface("RoyalNative");
                    webView.stopLoading();
                    webView.destroy();
                } catch (Throwable ignored) {}
                webView = null;
            }
            try {
                fallbackView = new LandscapeSlotView(this, null);
                fallbackView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
                root.removeAllViews();
                root.addView(fallbackView, matchParent());
                fallbackView.onHostResume();
            } catch (Throwable fatal) {
                TextView message = new TextView(this);
                message.setText("ROYAL SPIN · MODO SEGURO\n\n"
                        + "WebGL no está disponible.\nDetalle: " + reason);
                message.setTextColor(Color.WHITE);
                message.setTextSize(17f);
                message.setGravity(Gravity.CENTER);
                message.setBackgroundColor(0xFF080A12);
                root.removeAllViews();
                root.addView(message, matchParent());
            }
        });
    }

    private final class LocalClient extends WebViewClientCompat {
        private final WebViewAssetLoader loader;
        LocalClient(WebViewAssetLoader loader) { this.loader = loader; }

        @Override public WebResourceResponse shouldInterceptRequest(
                @NonNull WebView view, @NonNull WebResourceRequest request) {
            return loader.shouldInterceptRequest(request.getUrl());
        }

        @SuppressWarnings("deprecation")
        @Override public WebResourceResponse shouldInterceptRequest(
                @NonNull WebView view, @NonNull String url) {
            return loader.shouldInterceptRequest(Uri.parse(url));
        }

        @Override public void onReceivedError(@NonNull WebView view,
                                               @NonNull WebResourceRequest request,
                                               @NonNull androidx.webkit.WebResourceErrorCompat error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) showFallback("Error de contenido local");
        }

        @Override public boolean onRenderProcessGone(
                @NonNull WebView view, @NonNull RenderProcessGoneDetail detail) {
            showFallback(detail.didCrash()
                    ? "Proceso WebView finalizado"
                    : "Proceso WebView recuperado");
            return true;
        }
    }

    private final class NativeBridge {
        private final Random random = new Random();
        private int credits = 5000;
        private int betPerLine = 5;
        private int freeSpins;

        @JavascriptInterface public synchronized String getState() {
            return stateJson().toString();
        }

        @JavascriptInterface public synchronized String changeBet(int delta) {
            if (delta > 0) betPerLine = Math.min(25, betPerLine + 1);
            else if (delta < 0) betPerLine = Math.max(1, betPerLine - 1);
            return stateJson().toString();
        }

        @JavascriptInterface public synchronized String requestSpin() {
            int totalBet = betPerLine * LandscapeSlotEngine.LINES;
            boolean free = freeSpins > 0;
            if (!free && credits < totalBet) {
                JSONObject error = new JSONObject();
                try { error.put("error", "CRÉDITOS INSUFICIENTES"); }
                catch (JSONException ignored) {}
                return error.toString();
            }
            if (free) freeSpins--;
            else credits -= totalBet;

            String scene = demoScene;
            LandscapeSlotEngine.SpinResult result = scene.isEmpty()
                    ? LandscapeSlotEngine.spin(random, betPerLine)
                    : LandscapeSlotEngine.demo(scene, betPerLine);
            credits += result.payout;
            if (result.freeSpinsTriggered) {
                freeSpins = Math.min(90, freeSpins + 12);
            }

            JSONObject json = stateJson();
            try {
                json.put("payout", result.payout);
                json.put("feature", result.freeSpinsTriggered);
                json.put("anticipation", "anticipation".equals(scene)
                        || result.freeSpinsTriggered
                        || result.payout >= totalBet * 5);
                json.put("demo", scene);
                JSONArray board = new JSONArray();
                for (int reel = 0; reel < result.board.length; reel++) {
                    JSONArray column = new JSONArray();
                    for (int row = 0; row < result.board[reel].length; row++) {
                        column.put(result.board[reel][row]);
                    }
                    board.put(column);
                }
                json.put("board", board);
                JSONArray wins = new JSONArray();
                for (LandscapeSlotEngine.LineWin win : result.wins) {
                    JSONObject item = new JSONObject();
                    item.put("line", win.line);
                    item.put("symbol", win.symbol);
                    item.put("count", win.count);
                    item.put("amount", win.amount);
                    JSONArray rows = new JSONArray();
                    for (int row : win.rows) rows.put(row);
                    item.put("rows", rows);
                    wins.put(item);
                }
                json.put("wins", wins);
            } catch (JSONException ignored) {}
            return json.toString();
        }

        private JSONObject stateJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("credits", credits);
                json.put("betPerLine", betPerLine);
                json.put("totalBet", betPerLine * LandscapeSlotEngine.LINES);
                json.put("freeSpins", freeSpins);
            } catch (JSONException ignored) {}
            return json;
        }

        @JavascriptInterface public void reportReady(String engine) {
            onRendererReady(engine);
        }

        @JavascriptInterface public void reportError(String detail) {
            showFallback(detail == null ? "Error JavaScript" : detail);
        }

        @JavascriptInterface public void vibrate(int millis) {
            main.post(() -> {
                try {
                    Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                    if (vibrator == null || !vibrator.hasVibrator()) return;
                    int duration = Math.max(10, Math.min(250, millis));
                    if (Build.VERSION.SDK_INT >= 26) {
                        vibrator.vibrate(VibrationEffect.createOneShot(
                                duration, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        //noinspection deprecation
                        vibrator.vibrate(duration);
                    }
                } catch (Throwable ignored) {}
            });
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveCompat();
    }

    @Override protected void onResume() {
        super.onResume();
        applyImmersiveCompat();
        if (webView != null) {
            webView.onResume();
            try {
                webView.evaluateJavascript(
                        "window.RoyalAudio&&window.RoyalAudio.resume&&window.RoyalAudio.resume();",
                        null);
            } catch (Throwable ignored) {}
        }
        if (fallbackView != null) fallbackView.onHostResume();
    }

    @Override protected void onPause() {
        if (webView != null) {
            try {
                webView.evaluateJavascript(
                        "window.RoyalAudio&&window.RoyalAudio.suspend&&window.RoyalAudio.suspend();",
                        null);
            } catch (Throwable ignored) {}
            webView.onPause();
        }
        if (fallbackView != null) fallbackView.onHostPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        if (webView != null) {
            try {
                webView.removeJavascriptInterface("RoyalNative");
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.clearHistory();
                webView.removeAllViews();
                webView.destroy();
            } catch (Throwable ignored) {}
            webView = null;
        }
        if (fallbackView != null) {
            try { fallbackView.release(); } catch (Throwable ignored) {}
            fallbackView = null;
        }
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private void applyImmersiveCompat() {
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } catch (Throwable ignored) {}
    }
}
