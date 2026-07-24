package cl.exequiel.royalspin;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StakeSlotEngineTest {
    @Test
    public void deterministicSeedProducesSameBoardAndPayout() {
        StakeSlotEngine engine = new StakeSlotEngine();
        StakeSlotEngine.SpinResult first = engine.spin(new Random(77L), 2);
        StakeSlotEngine.SpinResult second = engine.spin(new Random(77L), 2);
        assertEquals(first.totalBet, second.totalBet);
        assertEquals(first.totalPayout, second.totalPayout);
        for (int reel = 0; reel < StakeSlotEngine.REEL_COUNT; reel++) {
            assertArrayEquals(first.board[reel], second.board[reel]);
        }
    }

    @Test
    public void wildSubstitutesFromLeft() {
        int payout = StakeSlotEngine.evaluateLineMultiplierForTest(
                new String[]{"W", "W", "7", "Q", "J"});
        assertEquals(63, payout);
    }

    @Test
    public void allWildsUseFiveWildPayout() {
        int payout = StakeSlotEngine.evaluateLineMultiplierForTest(
                new String[]{"W", "W", "W", "W", "W"});
        assertEquals(1939, payout);
    }

    @Test
    public void betIsAlwaysTwentyLines() {
        StakeSlotEngine.SpinResult spin = new StakeSlotEngine().spin(new Random(1L), 3);
        assertEquals(60, spin.totalBet);
        assertTrue(spin.totalPayout >= 0);
    }

    @Test
    public void completeRtpTargetIsVersionedAndTraceable() {
        assertEquals(0.9548, StakeSlotEngine.THEORETICAL_RTP, 0.000000001);
        assertEquals(StakeSlotEngine.THEORETICAL_RTP,
                StakeSlotEngine.BASE_GAME_RTP_TARGET + StakeSlotEngine.FEATURE_RTP_TARGET,
                0.000000001);
    }
}
