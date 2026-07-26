package cl.exequiel.royalspin.landscape;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AnimationProfileTest {
    @Test public void stopTimesAreStrictlyIncreasing() {
        assertIncreasing(AnimationProfile.stopTimes(false));
        assertIncreasing(AnimationProfile.stopTimes(true));
    }

    @Test public void anticipationExtendsFinalReels() {
        long[] normal = AnimationProfile.stopTimes(false);
        long[] anticipation = AnimationProfile.stopTimes(true);
        assertTrue(anticipation[3] > normal[3]);
        assertTrue(anticipation[4] > normal[4]);
    }

    @Test public void reelDistanceNeverRunsBackward() {
        long stop = AnimationProfile.stopTimes(false)[4];
        float previous = 0f;
        for (long elapsed = 0L; elapsed <= stop; elapsed += 16L) {
            float distance = AnimationProfile.reelDistanceCells(elapsed, stop);
            assertTrue("distance reversed at " + elapsed, distance + 0.0001f >= previous);
            previous = distance;
        }
        assertEquals(0f, AnimationProfile.reelVelocity(stop, stop), 0.0001f);
    }

    @Test public void stopBounceSettlesToZero() {
        assertEquals(0f, AnimationProfile.stopBounce(-1L), 0.0001f);
        assertTrue(Math.abs(AnimationProfile.stopBounce(80L)) > 0.1f);
        assertEquals(0f, AnimationProfile.stopBounce(600L), 0.0001f);
    }

    private static void assertIncreasing(long[] values) {
        for (int i = 1; i < values.length; i++) {
            assertTrue(values[i] > values[i - 1]);
        }
    }
}
