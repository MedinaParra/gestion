package cl.exequiel.royalspin;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** Hosts the validated game renderer and a presentation-only premium typography layer. */
public final class RoyalSpinPremiumShell extends FrameLayout {
    private final RoyalSpinV2View gameView;
    private final PremiumTypographyOverlay typographyOverlay;

    public RoyalSpinPremiumShell(Context context, String demoMode) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
        gameView = new RoyalSpinV2View(context, demoMode);
        typographyOverlay = new PremiumTypographyOverlay(context, gameView);
        addView(gameView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        addView(typographyOverlay, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void onHostPause() {
        gameView.onHostPause();
    }

    public void release() {
        typographyOverlay.release();
        gameView.release();
    }
}
