package cl.exequiel.royalspin;

/** Owns the free-spins counters. It does not generate RNG results or render frames. */
public final class FeatureSessionController {
    private final FeatureState state;

    public FeatureSessionController() {
        this(FeatureState.inactive());
    }

    public FeatureSessionController(FeatureState restored) {
        state = restored == null ? FeatureState.inactive() : restored.copy();
        normalize();
    }

    public FeatureState snapshot() {
        return state.copy();
    }

    public void beginFeature(FeatureTrigger trigger, int betPerLine) {
        if (trigger == null || !trigger.isInitialFeature()) {
            throw new IllegalArgumentException("An initial feature trigger is required");
        }
        state.active = true;
        state.totalAwardedSpins = Math.min(FeatureRules.MAXIMUM_FEATURE_SPINS,
                trigger.awardedFreeSpins);
        state.spinsRemaining = state.totalAwardedSpins;
        state.spinsPlayed = 0;
        state.retriggerCount = 0;
        state.totalFeatureWin = 0;
        state.lockedBetPerLine = StakeSlotEngine.clampBet(betPerLine);
        state.triggerWildCount = trigger.highestWildCount;
    }

    /** Consumes exactly one free spin before its RNG result is generated. */
    public int consumeNextSpin() {
        if (!state.active || state.spinsRemaining <= 0) {
            throw new IllegalStateException("No free spin is available");
        }
        state.spinsRemaining--;
        state.spinsPlayed++;
        return state.spinsPlayed;
    }

    /** Settles payout and optionally applies one retrigger for the completed spin. */
    public int settleSpin(int payout, FeatureTrigger trigger) {
        if (!state.active) throw new IllegalStateException("Feature is not active");
        state.totalFeatureWin += Math.max(0, payout);
        int added = 0;
        if (trigger != null && trigger.isRetrigger()) {
            int capacity = FeatureRules.MAXIMUM_FEATURE_SPINS - state.totalAwardedSpins;
            added = Math.max(0, Math.min(capacity, trigger.retriggerSpins));
            if (added > 0) {
                state.totalAwardedSpins += added;
                state.spinsRemaining += added;
                state.retriggerCount++;
                state.triggerWildCount = Math.max(state.triggerWildCount,
                        trigger.highestWildCount);
            }
        }
        return added;
    }

    public boolean shouldFinish() {
        return state.active && state.spinsRemaining == 0;
    }

    public int finishFeature() {
        int total = state.totalFeatureWin;
        state.active = false;
        state.spinsRemaining = 0;
        return total;
    }

    public boolean isActive() { return state.active; }
    public int spinsRemaining() { return state.spinsRemaining; }
    public int spinsPlayed() { return state.spinsPlayed; }
    public int totalFeatureWin() { return state.totalFeatureWin; }
    public int lockedBetPerLine() { return state.lockedBetPerLine; }

    private void normalize() {
        state.totalAwardedSpins = clamp(state.totalAwardedSpins, 0,
                FeatureRules.MAXIMUM_FEATURE_SPINS);
        state.spinsRemaining = clamp(state.spinsRemaining, 0, state.totalAwardedSpins);
        state.spinsPlayed = Math.max(0, state.spinsPlayed);
        state.retriggerCount = Math.max(0, state.retriggerCount);
        state.totalFeatureWin = Math.max(0, state.totalFeatureWin);
        state.lockedBetPerLine = StakeSlotEngine.clampBet(
                state.lockedBetPerLine == 0 ? 1 : state.lockedBetPerLine);
        state.triggerWildCount = clamp(state.triggerWildCount, 0, StakeSlotEngine.REEL_COUNT);
        if (state.active && state.totalAwardedSpins == 0) state.active = false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
