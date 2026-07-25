package cl.exequiel.royalspin.landscape;

import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/**
 * Root container that guarantees every pointer event reaches the game view.
 * Decorative overlays never participate in hit testing.
 */
public final class LandscapeRootLayout extends FrameLayout {
    private static final float DESIGN_W = 1280f;
    private static final float DESIGN_H = 720f;
    private static final float SPIN_X = 1136f;
    private static final float SPIN_Y = 440f;
    private static final float SPIN_RADIUS = 118f;

    private final LandscapeSlotView gameView;
    private final LandscapeSpectacleOverlay spectacle;
    private boolean spinPointerDown;

    public LandscapeRootLayout(Context context,
                               LandscapeSlotView gameView,
                               LandscapeSpectacleOverlay spectacle) {
        super(context);
        this.gameView = gameView;
        this.spectacle = spectacle;
        setClipChildren(false);
        setClipToPadding(false);

        addView(gameView, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(spectacle, new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        updateSpectacleTouch(event);

        // The game is the only interactive child. Dispatch directly so a full-screen
        // decorative View can never block the GIRAR button on any Android version.
        return gameView != null && gameView.dispatchTouchEvent(event);
    }

    private void updateSpectacleTouch(MotionEvent event) {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        float scale = Math.min(getWidth() / DESIGN_W, getHeight() / DESIGN_H);
        float offsetX = (getWidth() - DESIGN_W * scale) * .5f;
        float offsetY = (getHeight() - DESIGN_H * scale) * .5f;
        float x = (event.getX() - offsetX) / Math.max(.001f, scale);
        float y = (event.getY() - offsetY) / Math.max(.001f, scale);
        boolean insideSpin = distance(x, y, SPIN_X, SPIN_Y) <= SPIN_RADIUS;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                spinPointerDown = insideSpin;
                spectacle.setSpinPressed(spinPointerDown);
                if (spinPointerDown) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                break;
            case MotionEvent.ACTION_UP:
                if (spinPointerDown && insideSpin) {
                    spectacle.triggerSpinSequence();
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                }
                spinPointerDown = false;
                spectacle.setSpinPressed(false);
                break;
            case MotionEvent.ACTION_CANCEL:
                spinPointerDown = false;
                spectacle.setSpinPressed(false);
                break;
            default:
                if (spinPointerDown && !insideSpin) {
                    spectacle.setSpinPressed(false);
                }
                break;
        }
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
