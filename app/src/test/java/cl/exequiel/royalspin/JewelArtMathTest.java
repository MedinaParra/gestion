package cl.exequiel.royalspin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JewelArtMathTest {
    @Test public void cycleAndShimmerLoopDeterministically() {
        for (int seed = -9; seed <= 9; seed++) {
            assertEquals(JewelArtMath.cycle(330L, seed, 1900L),
                    JewelArtMath.cycle(2230L, seed, 1900L), .00001f);
            assertEquals(JewelArtMath.shimmer(170L, seed, 800L),
                    JewelArtMath.shimmer(970L, seed, 800L), .00001f);
        }
    }

    @Test public void breathingRemainsInsideAmplitude() {
        for (long t = 0; t < 40_000; t += 29L) {
            float value = JewelArtMath.breathe(t, -17, 3100L, .07f);
            assertTrue(value >= .92999f && value <= 1.07001f);
        }
    }

    @Test public void landingSettlesAndIsReelStaggered() {
        assertEquals(0f, JewelArtMath.landing(0L, 0, false), .00001f);
        assertEquals(0f, JewelArtMath.landing(60L, 1, false), .00001f);
        assertEquals(0f, JewelArtMath.landing(900L, 0, false), .00001f);
        assertEquals(0f, JewelArtMath.landing(220L, 0, true), .00001f);
        assertTrue(Math.abs(JewelArtMath.landing(180L, 0, false)) > .01f);
    }

    @Test public void qualityBudgetsKeepPremiumCoreVisible() {
        assertEquals(8, JewelArtMath.facetCount(2, false, false));
        assertEquals(5, JewelArtMath.facetCount(1, false, false));
        assertEquals(3, JewelArtMath.facetCount(0, false, false));
        assertEquals(3, JewelArtMath.facetCount(2, false, true));
        assertEquals(3, JewelArtMath.facetCount(2, true, false));

        assertEquals(12, JewelArtMath.particleCount(2, false, false, 12));
        assertEquals(6, JewelArtMath.particleCount(1, false, false, 12));
        assertEquals(3, JewelArtMath.particleCount(0, false, false, 12));
        assertEquals(2, JewelArtMath.particleCount(2, false, true, 12));
    }

    @Test public void sparkAndWinPulseAreBounded() {
        for (long t = 0; t < 30_000; t += 31L) {
            float spark = JewelArtMath.jewelSpark(t, 27);
            float idle = JewelArtMath.winPulse(t, 27, false);
            float win = JewelArtMath.winPulse(t, 27, true);
            assertTrue(spark >= 0f && spark <= 1f);
            assertTrue(idle >= 0f && idle <= 1f);
            assertTrue(win >= 0f && win <= 1f);
        }
    }
}
