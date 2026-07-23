package cl.exequiel.royalspin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Consolidated feature trigger. Multiple winning lines still create one feature award. */
public final class FeatureTrigger {
    public static final FeatureTrigger NONE = new FeatureTrigger(false, 0, 0, 0,
            Collections.emptyList());

    public final boolean triggered;
    public final int highestWildCount;
    public final int awardedFreeSpins;
    public final int retriggerSpins;
    public final List<Integer> triggeringLines;

    public FeatureTrigger(boolean triggered, int highestWildCount, int awardedFreeSpins,
                          int retriggerSpins, List<Integer> triggeringLines) {
        this.triggered = triggered;
        this.highestWildCount = Math.max(0, highestWildCount);
        this.awardedFreeSpins = Math.max(0, awardedFreeSpins);
        this.retriggerSpins = Math.max(0, retriggerSpins);
        List<Integer> safe = triggeringLines == null
                ? Collections.emptyList() : new ArrayList<>(triggeringLines);
        this.triggeringLines = Collections.unmodifiableList(safe);
    }

    public boolean isInitialFeature() {
        return triggered && awardedFreeSpins > 0;
    }

    public boolean isRetrigger() {
        return triggered && retriggerSpins > 0;
    }
}
