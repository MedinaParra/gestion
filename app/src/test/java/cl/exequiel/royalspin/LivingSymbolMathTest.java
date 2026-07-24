package cl.exequiel.royalspin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LivingSymbolMathTest {
    @Test public void pulseRemainsInsideRequestedRange() {
        for (long t = 0; t < 20_000; t += 37) {
            float value = LivingSymbolMath.pulse(t, 4, 2300L, .35f, .92f);
            assertTrue(value >= .35f && value <= .92f);
        }
    }

    @Test public void shimmerLoopsDeterministically() {
        assertEquals(LivingSymbolMath.shimmer(250L, 2, 1200L),
                LivingSymbolMath.shimmer(1450L, 2, 1200L), .00001f);
    }

    @Test public void anticipationIsMonotonic() {
        float previous = 0f;
        for (long t = 100; t <= 1000; t += 25) {
            float value = LivingSymbolMath.anticipation(t, 100L, 1000L);
            assertTrue(value >= previous - .00001f);
            previous = value;
        }
        assertEquals(0f, LivingSymbolMath.anticipation(0L, 100L, 1000L), .00001f);
        assertEquals(1f, LivingSymbolMath.anticipation(1200L, 100L, 1000L), .00001f);
    }

    @Test public void liteAndReducedModesReduceParticles() {
        assertEquals(12, LivingSymbolMath.particleCount(2, false, 12));
        assertEquals(6, LivingSymbolMath.particleCount(1, false, 12));
        assertEquals(3, LivingSymbolMath.particleCount(0, false, 12));
        assertEquals(2, LivingSymbolMath.particleCount(2, true, 12));
    }

    @Test public void orbitIsBoundedByRadius() {
        for (long t = 0; t < 12_000; t += 83) {
            float x = LivingSymbolMath.orbitX(t, 3, 17f, 2400L);
            float y = LivingSymbolMath.orbitY(t, 3, 17f, 2400L);
            assertTrue(Math.sqrt(x * x + y * y) <= 17.001);
        }
    }
}
