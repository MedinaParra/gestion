package cl.exequiel.royalspin.landscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LandscapeGameEngineTest {
    @Test public void randomRoundsAlwaysHaveFiveByThreeBoard() {
        LandscapeGameEngine engine = new LandscapeGameEngine(1234);
        for (int i = 0; i < 10_000; i++) {
            LandscapeGameEngine.SpinResult result = engine.spin(5, false);
            assertEquals(5, result.board.length);
            for (String[] reel : result.board) assertEquals(3, reel.length);
            assertTrue(result.payout >= 0);
        }
    }

    @Test public void threeWildsFromFirstReelTriggerThirtyFreeSpins() {
        LandscapeGameEngine engine = new LandscapeGameEngine(1);
        LandscapeGameEngine.SpinResult result = engine.scripted("wild", 5, false);
        assertTrue(result.featureTriggered());
        assertEquals(30, result.awardedFreeSpins);
        assertTrue(result.wildPrefix >= 3);
    }

    @Test public void freeSpinRetriggerAwardsTen() {
        LandscapeGameEngine engine = new LandscapeGameEngine(1);
        LandscapeGameEngine.SpinResult result = engine.scripted("wild", 5, true);
        assertEquals(10, result.awardedFreeSpins);
    }

    @Test public void idleBoardDoesNotNecessarilyTriggerFeature() {
        LandscapeGameEngine engine = new LandscapeGameEngine(1);
        LandscapeGameEngine.SpinResult result = engine.scripted("idle", 5, false);
        assertFalse(result.featureTriggered());
    }

    @Test public void scriptedBellCreatesVisibleWin() {
        LandscapeGameEngine engine = new LandscapeGameEngine(1);
        LandscapeGameEngine.SpinResult result = engine.scripted("bell", 5, false);
        assertTrue(result.payout > 0);
        assertFalse(result.wins.isEmpty());
        assertEquals(LandscapeGameEngine.BELL, result.wins.get(0).symbol);
    }
}
