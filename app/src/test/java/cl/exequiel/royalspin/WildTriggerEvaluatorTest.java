package cl.exequiel.royalspin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WildTriggerEvaluatorTest {
    private final StakeSlotEngine engine = new StakeSlotEngine();

    @Test
    public void threeWildsFromLeftActivateThirtyFreeSpins() {
        StakeSlotEngine.SpinResult result = engine.evaluate(boardWithTopWilds(3), new int[5], 1,
                GameMode.BASE_GAME);
        assertTrue(result.featureTrigger.triggered);
        assertEquals(3, result.featureTrigger.highestWildCount);
        assertEquals(30, result.featureTrigger.awardedFreeSpins);
        assertEquals(0, result.featureTrigger.retriggerSpins);
    }

    @Test
    public void twoWildsDoNotActivateFeature() {
        StakeSlotEngine.SpinResult result = engine.evaluate(boardWithTopWilds(2), new int[5], 1,
                GameMode.BASE_GAME);
        assertFalse(result.featureTrigger.triggered);
    }

    @Test
    public void freeSpinTriggerBecomesTenSpinRetrigger() {
        StakeSlotEngine.SpinResult result = engine.evaluate(boardWithTopWilds(5), new int[5], 2,
                GameMode.FREE_SPINS);
        assertTrue(result.featureTrigger.isRetrigger());
        assertEquals(0, result.featureTrigger.awardedFreeSpins);
        assertEquals(10, result.featureTrigger.retriggerSpins);
        assertEquals(5, result.featureTrigger.highestWildCount);
    }

    @Test
    public void multipleLinesStillAwardOneFeature() {
        String[][] board = filled(StakeSlotEngine.JACK);
        for (int reel = 0; reel < 3; reel++) {
            board[reel][0] = StakeSlotEngine.WILD;
            board[reel][1] = StakeSlotEngine.WILD;
            board[reel][2] = StakeSlotEngine.WILD;
        }
        StakeSlotEngine.SpinResult result = engine.evaluate(board, new int[5], 1,
                GameMode.BASE_GAME);
        assertTrue(result.featureTrigger.triggeringLines.size() > 1);
        assertEquals(30, result.featureTrigger.awardedFreeSpins);
    }

    private static String[][] boardWithTopWilds(int count) {
        String[][] board = filled(StakeSlotEngine.JACK);
        for (int reel = 0; reel < count; reel++) board[reel][0] = StakeSlotEngine.WILD;
        return board;
    }

    private static String[][] filled(String symbol) {
        String[][] board = new String[5][3];
        for (int reel = 0; reel < 5; reel++) {
            for (int row = 0; row < 3; row++) board[reel][row] = symbol;
        }
        return board;
    }
}
