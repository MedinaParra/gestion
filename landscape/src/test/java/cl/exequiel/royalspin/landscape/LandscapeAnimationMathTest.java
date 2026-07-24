package cl.exequiel.royalspin.landscape;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LandscapeAnimationMathTest {
    @Test public void smoothstepIsBoundedAndMonotonic() {
        float previous = 0f;
        for (int i = 0; i <= 100; i++) {
            float value = LandscapeAnimationMath.smoothstep(i / 100f);
            assertTrue(value >= 0f && value <= 1f);
            assertTrue(value + .00001f >= previous);
            previous = value;
        }
    }

    @Test public void breatheRemainsInsideAmplitude() {
        for (long t = 0; t < 20_000; t += 37) {
            float value = LandscapeAnimationMath.breathe(t, 4, 2600, .08f);
            assertTrue(value >= .9199f && value <= 1.0801f);
        }
    }

    @Test public void shimmerLoops() {
        assertEquals(LandscapeAnimationMath.shimmer(400, 3, 1800),
                LandscapeAnimationMath.shimmer(2200, 3, 1800), .00001f);
    }

    @Test public void liteBudgetKeepsCoreEffect() {
        assertEquals(20, LandscapeAnimationMath.particleBudget(2, false, 20));
        assertEquals(10, LandscapeAnimationMath.particleBudget(1, false, 20));
        assertEquals(5, LandscapeAnimationMath.particleBudget(0, false, 20));
        assertEquals(4, LandscapeAnimationMath.particleBudget(2, true, 20));
    }
}
