package cl.exequiel.royalspin;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** Hosts the validated game renderer and all presentation-only roadmap layers. */
public final class RoyalSpinPremiumShell extends FrameLayout {
    private final RoyalSpinV2View gameView;
    private final PremiumTypographyOverlay typographyOverlay;
    private final PremiumSymbolOverlay symbolOverlay;
    private final PremiumFeatureRevealOverlay featureRevealOverlay;
    private final PremiumAudioConductor audioConductor;

    public RoyalSpinPremiumShell(Context context, String demoMode) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        gameView = new RoyalSpinV2View(context, demoMode);
        typographyOverlay = new PremiumTypographyOverlay(context, gameView);
        symbolOverlay = new PremiumSymbolOverlay(context, gameView);
        featureRevealOverlay = new PremiumFeatureRevealOverlay(context, gameView);
        audioConductor = new PremiumAudioConductor(gameView);

        // Keep every layer on the activity's normal hardware-accelerated canvas.
        typographyOverlay.setLayerType(View.LAYER_TYPE_NONE, null);
        symbolOverlay.setLayerType(View.LAYER_TYPE_NONE, null);
        featureRevealOverlay.setLayerType(View.LAYER_TYPE_NONE, null);

        addView(gameView, matchParent());
        addView(typographyOverlay, matchParent());
        addView(symbolOverlay, matchParent());
        addView(featureRevealOverlay, matchParent());
    }

    private static LayoutParams matchParent() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        audioConductor.start();
    }

    @Override protected void onDetachedFromWindow() {
        audioConductor.suspend();
        super.onDetachedFromWindow();
    }

    public void onHostResume() {
        audioConductor.resume();
    }

    public void onHostPause() {
        audioConductor.suspend();
        gameView.onHostPause();
    }

    public void release() {
        audioConductor.release();
        featureRevealOverlay.release();
        symbolOverlay.release();
        typographyOverlay.release();
        gameView.release();
    }
}
