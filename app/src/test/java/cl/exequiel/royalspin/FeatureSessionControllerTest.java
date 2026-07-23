package cl.exequiel.royalspin;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FeatureSessionControllerTest {
    private static FeatureTrigger initial(int wilds) {
        return new FeatureTrigger(true, wilds, 30, 0, Collections.singletonList(0));
    }

    private static FeatureTrigger retrigger(int wilds) {
        return new FeatureTrigger(true, wilds, 0, 10, Collections.singletonList(0));
    }

    @Test
    public void featureStartsWithThirtyAndLocksBet() {
        FeatureSessionController controller = new FeatureSessionController();
        controller.beginFeature(initial(3), 4);
        assertTrue(controller.isActive());
        assertEquals(30, controller.spinsRemaining());
        assertEquals(4, controller.lockedBetPerLine());
    }

    @Test
    public void consumingSpinAndSettlingWinAreIndependent() {
        FeatureSessionController controller = new FeatureSessionController();
        controller.beginFeature(initial(3), 2);
        controller.consumeNextSpin();
        controller.settleSpin(180, FeatureTrigger.NONE);
        assertEquals(29, controller.spinsRemaining());
        assertEquals(1, controller.spinsPlayed());
        assertEquals(180, controller.totalFeatureWin());
    }

    @Test
    public void retriggerAddsTenAndNeverExceedsNinety() {
        FeatureSessionController controller = new FeatureSessionController();
        controller.beginFeature(initial(5), 1);
        for (int i = 0; i < 6; i++) {
            controller.consumeNextSpin();
            controller.settleSpin(0, retrigger(3));
        }
        FeatureState state = controller.snapshot();
        assertEquals(90, state.totalAwardedSpins);
        assertEquals(84, state.spinsRemaining);
        assertEquals(6, state.retriggerCount);

        controller.consumeNextSpin();
        assertEquals(0, controller.settleSpin(0, retrigger(5)));
        assertEquals(90, controller.snapshot().totalAwardedSpins);
    }

    @Test
    public void featureFinishesOnlyAfterLastSettledSpin() {
        FeatureState restored = new FeatureState();
        restored.active = true;
        restored.totalAwardedSpins = 30;
        restored.spinsRemaining = 1;
        restored.lockedBetPerLine = 2;
        FeatureSessionController controller = new FeatureSessionController(restored);
        controller.consumeNextSpin();
        controller.settleSpin(25, FeatureTrigger.NONE);
        assertTrue(controller.shouldFinish());
        assertEquals(25, controller.finishFeature());
        assertFalse(controller.isActive());
    }
}
