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
        controller.beginFeature(initial(3), 4, 1001L);
        assertTrue(controller.isActive());
        assertEquals(30, controller.spinsRemaining());
        assertEquals(4, controller.lockedBetPerLine());
        assertEquals(1001L, controller.sessionId());
    }

    @Test
    public void consumingSpinAndSettlingWinAreIndependent() {
        FeatureSessionController controller = new FeatureSessionController();
        controller.beginFeature(initial(3), 2, 2001L);
        controller.consumeNextSpin();
        controller.settleSpin(3001L, 180, FeatureTrigger.NONE);
        assertEquals(29, controller.spinsRemaining());
        assertEquals(1, controller.spinsPlayed());
        assertEquals(180, controller.totalFeatureWin());
    }

    @Test
    public void replayingSameRoundNeverDuplicatesPayoutOrRetrigger() {
        FeatureSessionController controller = new FeatureSessionController();
        controller.beginFeature(initial(3), 1, 4001L);
        controller.consumeNextSpin();
        assertEquals(10, controller.settleSpin(5001L, 75, retrigger(3)));
        assertEquals(0, controller.settleSpin(5001L, 75, retrigger(3)));
        assertEquals(75, controller.totalFeatureWin());
        assertEquals(39, controller.spinsRemaining());
        assertEquals(1, controller.snapshot().retriggerCount);
    }

    @Test
    public void retriggerAddsTenAndNeverExceedsNinety() {
        FeatureSessionController controller = new FeatureSessionController();
        controller.beginFeature(initial(5), 1, 6001L);
        for (int i = 0; i < 6; i++) {
            controller.consumeNextSpin();
            controller.settleSpin(7001L + i, 0, retrigger(3));
        }
        FeatureState state = controller.snapshot();
        assertEquals(90, state.totalAwardedSpins);
        assertEquals(84, state.spinsRemaining);
        assertEquals(6, state.retriggerCount);
        controller.consumeNextSpin();
        assertEquals(0, controller.settleSpin(8001L, 0, retrigger(5)));
        assertEquals(90, controller.snapshot().totalAwardedSpins);
    }

    @Test
    public void featureFinishesOnlyAfterLastSettledSpin() {
        FeatureState restored = new FeatureState();
        restored.active = true;
        restored.sessionId = 9001L;
        restored.totalAwardedSpins = 30;
        restored.spinsRemaining = 1;
        restored.lockedBetPerLine = 2;
        FeatureSessionController controller = new FeatureSessionController(restored);
        controller.consumeNextSpin();
        controller.settleSpin(9100L, 25, FeatureTrigger.NONE);
        assertTrue(controller.shouldFinish());
        assertEquals(25, controller.finishFeature());
        assertFalse(controller.isActive());
    }
}
