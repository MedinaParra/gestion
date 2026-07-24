package cl.exequiel.royalspin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LivingTypographyMathTest {
    @Test public void breatheRemainsCenteredAroundOne() {
        assertEquals(1f, LivingTypographyMath.breathe(0L, 4000L, .05f), .0001f);
        float quarter = LivingTypographyMath.breathe(1000L, 4000L, .05f);
        assertTrue(quarter > 1.049f && quarter < 1.051f);
    }

    @Test public void shimmerAlwaysStaysNormalized() {
        for (long time = 0L; time < 20_000L; time += 137L) {
            float q = LivingTypographyMath.shimmer(time, 4300L);
            assertTrue(q >= 0f && q < 1f);
        }
    }

    @Test public void staggerStartsHiddenAndSettlesAtOne() {
        assertEquals(0f, LivingTypographyMath.stagger(0L, 4, 50L, 500L), .0001f);
        assertEquals(1f, LivingTypographyMath.stagger(2000L, 4, 50L, 500L), .0001f);
    }

    @Test public void flashIsZeroOutsideItsWindow() {
        assertEquals(0f, LivingTypographyMath.flash(-1L, 600L), .0001f);
        assertEquals(0f, LivingTypographyMath.flash(600L, 600L), .0001f);
        assertTrue(LivingTypographyMath.flash(300L, 600L) > .99f);
    }
}
