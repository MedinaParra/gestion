package cl.exequiel.royalspin;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class PremiumReelDynamicsTest {
    @Test public void stopTimesAreOrderedAndAnticipationOnlyExtendsLastReel() {
        long previous = -1;
        for (int i = 0; i < 5; i++) {
            long stop = PremiumReelDynamics.stopTime(i, true, false);
            assertTrue(stop > previous);
            if (i < 4) assertEquals(PremiumReelDynamics.BASE_STOPS[i], stop);
            previous = stop;
        }
        assertEquals(PremiumReelDynamics.BASE_STOPS[4]
                + PremiumReelDynamics.ANTICIPATION_EXTRA_MS,
                PremiumReelDynamics.stopTime(4, true, false));
    }

    @Test public void curvesStayFiniteAndBounded() {
        for (int i = -100; i <= 1200; i++) {
            float d = i / 1000f;
            float scale = PremiumReelDynamics.cylinderScale(d);
            float alpha = PremiumReelDynamics.cylinderAlpha(d);
            assertTrue(Float.isFinite(scale));
            assertTrue(Float.isFinite(alpha));
            assertTrue(scale >= .58f && scale <= 1.001f);
            assertTrue(alpha >= .28f && alpha <= 1.001f);
        }
    }

    @Test public void landingReturnsToZero() {
        assertEquals(0f, PremiumReelDynamics.landingOffset(-1, false), 0f);
        assertEquals(0f, PremiumReelDynamics.landingOffset(701, false), 0f);
        assertTrue(Math.abs(PremiumReelDynamics.landingOffset(80, false)) > 0.1f);
    }

    @Test public void reducedMotionAlwaysUsesSmallerBudget() {
        for (double multiplier : new double[]{0.5, 5, 25, 100}) {
            int full = PremiumReelDynamics.particleBudget(multiplier, false, 2);
            int reduced = PremiumReelDynamics.particleBudget(multiplier, true, 2);
            assertTrue(reduced < full);
            assertTrue(reduced <= 36);
        }
    }

    @Test public void stressOneHundredThousandRoundsPreservesMathInvariants() {
        StakeSlotEngine engine = new StakeSlotEngine();
        Random random = new Random(20260723L);
        long totalBet = 0L;
        long totalPayout = 0L;
        for (int i = 0; i < 100_000; i++) {
            int bet = 1 + i % 5;
            StakeSlotEngine.SpinResult result = engine.spin(random, bet);
            assertNotNull(result);
            assertEquals(5, result.board.length);
            for (String[] reel : result.board) assertEquals(3, reel.length);
            assertTrue(result.totalPayout >= 0);
            totalBet += (long) StakeSlotEngine.LINE_COUNT * bet;
            totalPayout += result.totalPayout;
            boolean anticipate = PremiumReelDynamics.shouldAnticipate(result);
            assertTrue(PremiumReelDynamics.stopTime(4, anticipate, false)
                    >= PremiumReelDynamics.BASE_STOPS[4]);
        }
        assertTrue(totalBet > 0L);
        assertTrue(totalPayout >= 0L);
    }
}