package cl.exequiel.royalspin;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SymbolAnimationDirectorTest {
    @Test public void mapsSymbolsToDistinctStyles() {
        assertEquals(SymbolAnimationDirector.Style.BELL,
                SymbolAnimationDirector.styleFor(StakeSlotEngine.BELL));
        assertEquals(SymbolAnimationDirector.Style.BAR,
                SymbolAnimationDirector.styleFor(StakeSlotEngine.BAR));
        assertEquals(SymbolAnimationDirector.Style.SEVEN,
                SymbolAnimationDirector.styleFor(StakeSlotEngine.SEVEN));
        assertEquals(SymbolAnimationDirector.Style.DIAMOND,
                SymbolAnimationDirector.styleFor(StakeSlotEngine.DIAMOND));
        assertEquals(SymbolAnimationDirector.Style.WILD,
                SymbolAnimationDirector.styleFor(StakeSlotEngine.WILD));
        assertEquals(SymbolAnimationDirector.Style.CARD,
                SymbolAnimationDirector.styleFor(StakeSlotEngine.QUEEN));
    }

    @Test public void classifiesCelebrationTiers() {
        assertEquals(SymbolAnimationDirector.Tier.SMALL,
                SymbolAnimationDirector.tierFor(2.5));
        assertEquals(SymbolAnimationDirector.Tier.BIG,
                SymbolAnimationDirector.tierFor(5));
        assertEquals(SymbolAnimationDirector.Tier.MEGA,
                SymbolAnimationDirector.tierFor(25));
        assertEquals(SymbolAnimationDirector.Tier.ROYAL,
                SymbolAnimationDirector.tierFor(100));
    }

    @Test public void selectsHighestPayingLine() {
        StakeSlotEngine.LineWin low = new StakeSlotEngine.LineWin(
                0, new int[]{0, 0, 0, 0, 0}, StakeSlotEngine.QUEEN, 3, 8, 8);
        StakeSlotEngine.LineWin high = new StakeSlotEngine.LineWin(
                1, new int[]{1, 1, 1, 1, 1}, StakeSlotEngine.BELL, 5, 512, 512);
        StakeSlotEngine.LineWin selected = SymbolAnimationDirector.primaryWin(Arrays.asList(low, high));
        assertNotNull(selected);
        assertEquals(StakeSlotEngine.BELL, selected.symbol);
        assertEquals(512, selected.payout);
    }

    @Test public void animationFrameStaysBoundedAndSymbolSpecific() {
        SymbolAnimationDirector.Frame bell = SymbolAnimationDirector.frame(
                StakeSlotEngine.BELL, 5, 30, 320);
        SymbolAnimationDirector.Frame seven = SymbolAnimationDirector.frame(
                StakeSlotEngine.SEVEN, 5, 30, 320);
        assertTrue(bell.enter >= 0f && bell.enter <= 1f);
        assertTrue(bell.ring >= 0f && bell.ring <= 1f);
        assertTrue(bell.flash >= 0f && bell.flash <= 1f);
        assertTrue(Math.abs(bell.rotation - seven.rotation) > .1f);
    }
}
