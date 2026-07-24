package cl.exequiel.royalspin;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** Hosts the validated game renderer and presentation-only premium layers. */
public final class RoyalSpinPremiumShell extends FrameLayout {
    private final RoyalSpinV2View gameView;
    private final PremiumTypographyOverlay typographyOverlay;
    private final PremiumFeatureRevealOverlay featureRevealOverlay;

    public RoyalSpinPremiumShell(Context context, String demoMode) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        gameView = new RoyalSpinV2View(context, demoMode);
        typographyOverlay = new PremiumTypographyOverlay(context, gameView);
        featureRevealOverlay = new PremiumFeatureRevealOverlay(context, gameView);

        // Override the conservative software fallback: both overlays are composed on the GPU.
        typographyOverlay.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        featureRevealOverlay.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        addView(gameView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        addView(typographyOverlay, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        addView(featureRevealOverlay, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void onHostPause() {
        gameView.onHostPause();
    }

    public void release() {
        featureRevealOverlay.release();
        typographyOverlay.release();
        gameView.release();
    }
}
