package cl.exequiel.royalspin.landscape;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class LandscapeSlotEngineTest {
    @Test public void generatedBoardsAlwaysHaveExpectedShape() {
        Random random = new Random(42);
        for (int i = 0; i < 10000; i++) {
            LandscapeSlotEngine.SpinResult result = LandscapeSlotEngine.spin(random, 5);
            assertEquals(5, result.board.length);
            for (int reel = 0; reel < 5; reel++) {
                assertEquals(3, result.board[reel].length);
                for (int row = 0; row < 3; row++) {
                    assertTrue(result.board[reel][row] >= LandscapeSlotEngine.A);
                    assertTrue(result.board[reel][row] <= LandscapeSlotEngine.WILD);
                }
            }
            assertTrue(result.payout >= 0);
        }
    }

    @Test public void threeLeadingWildsTriggerFeatureOnce() {
        LandscapeSlotEngine.SpinResult result = LandscapeSlotEngine.demo("feature", 5);
        assertTrue(result.freeSpinsTriggered);
        assertTrue(result.triggerWildCount >= 3);
    }

    @Test public void deterministicCaptureScenesCoverAllRewardLevels() {
        LandscapeSlotEngine.SpinResult normal = LandscapeSlotEngine.demo("normal", 5);
        LandscapeSlotEngine.SpinResult big = LandscapeSlotEngine.demo("big", 5);
        LandscapeSlotEngine.SpinResult royal = LandscapeSlotEngine.demo("royal", 5);
        LandscapeSlotEngine.SpinResult free = LandscapeSlotEngine.demo("free", 5);
        assertTrue(normal.payout > 0);
        assertTrue(big.payout > normal.payout);
        assertTrue(royal.payout > big.payout);
        assertTrue(free.freeSpinsTriggered);
    }

    @Test public void bellBarSevenAndDiamondScenesProduceWins() {
        assertFalse(LandscapeSlotEngine.demo("bell", 5).wins.isEmpty());
        assertFalse(LandscapeSlotEngine.demo("bar", 5).wins.isEmpty());
        assertFalse(LandscapeSlotEngine.demo("seven", 5).wins.isEmpty());
        assertFalse(LandscapeSlotEngine.demo("diamond", 5).wins.isEmpty());
    }

    @Test public void payoutScalesWithBet() {
        int[][] board = LandscapeSlotEngine.demo("diamond", 1).board;
        int low = LandscapeSlotEngine.evaluate(board, 1).payout;
        int high = LandscapeSlotEngine.evaluate(board, 5).payout;
        assertEquals(low * 5, high);
    }
}
