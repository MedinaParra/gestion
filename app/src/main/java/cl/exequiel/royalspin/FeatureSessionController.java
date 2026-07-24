package cl.exequiel.royalspin;

import java.util.concurrent.atomic.AtomicLong;

/** Owns free-spin counters. It never generates RNG outcomes or renders frames. */
public final class FeatureSessionController {
    private static final AtomicLong IDS = new AtomicLong(2_026_072_400_000L);
    private final FeatureState state;

    public FeatureSessionController() {
        this(FeatureState.inactive());
    }

    public FeatureSessionController(FeatureState restored) {
        state = restored == null ? FeatureState.inactive() : restored.copy();
        normalize();
    }

    public FeatureState snapshot() { return state.copy(); }

    public void beginFeature(FeatureTrigger trigger, int betPerLine) {
        beginFeature(trigger, betPerLine, IDS.incrementAndGet());
    }

    public void beginFeature(FeatureTrigger trigger, int betPerLine, long sessionId) {
        if (trigger == null || !trigger.isInitialFeature()) {
            throw new IllegalArgumentException("An initial feature trigger is required");
        }
        state.schemaVersion = FeatureState.SCHEMA_VERSION;
        state.active = true;
        state.sessionId = sessionId > 0L ? sessionId : IDS.incrementAndGet();
        state.lastSettledRoundId = 0L;
        state.totalAwardedSpins = Math.min(FeatureRules.MAXIMUM_FEATURE_SPINS,
                trigger.awardedFreeSpins);
        state.spinsRemaining = state.totalAwardedSpins;
        state.spinsPlayed = 0;
        state.retriggerCount = 0;
        state.totalFeatureWin = 0;
        state.lockedBetPerLine = StakeSlotEngine.clampBet(betPerLine);
        state.triggerWildCount = trigger.highestWildCount;
    }

    /** Consumes one free spin before its precalculated result is persisted. */
    public int consumeNextSpin() {
        if (!state.active || state.spinsRemaining <= 0) {
            throw new IllegalStateException("No free spin is available");
        }
        state.spinsRemaining--;
        state.spinsPlayed++;
        return state.spinsPlayed;
    }

    /** Compatibility path for the v1.6 renderer. */
    public int settleSpin(int payout, FeatureTrigger trigger) {
        return settleSpin(Math.max(1L, state.lastSettledRoundId + 1L), payout, trigger);
    }

    /**
     * Applies payout/retrigger once. Replaying the same persisted round id is a no-op.
     */
    public int settleSpin(long roundId, int payout, FeatureTrigger trigger) {
        if (!state.active) throw new IllegalStateException("Feature is not active");
        if (roundId <= 0L) throw new IllegalArgumentException("roundId must be positive");
        if (roundId == state.lastSettledRoundId) return 0;
        if (roundId < state.lastSettledRoundId) {
            throw new IllegalStateException("Stale feature round " + roundId);
        }

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
        state.lastSettledRoundId = roundId;
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
    public long sessionId() { return state.sessionId; }
    public long lastSettledRoundId() { return state.lastSettledRoundId; }
    public int spinsRemaining() { return state.spinsRemaining; }
    public int spinsPlayed() { return state.spinsPlayed; }
    public int totalFeatureWin() { return state.totalFeatureWin; }
    public int lockedBetPerLine() { return state.lockedBetPerLine; }

    private void normalize() {
        if (state.schemaVersion != FeatureState.SCHEMA_VERSION) {
            resetToInactive();
            return;
        }
        state.totalAwardedSpins = clamp(state.totalAwardedSpins, 0,
                FeatureRules.MAXIMUM_FEATURE_SPINS);
        state.spinsRemaining = clamp(state.spinsRemaining, 0, state.totalAwardedSpins);
        state.spinsPlayed = Math.max(0, state.spinsPlayed);
        state.retriggerCount = Math.max(0, state.retriggerCount);
        state.totalFeatureWin = Math.max(0, state.totalFeatureWin);
        state.lockedBetPerLine = StakeSlotEngine.clampBet(
                state.lockedBetPerLine == 0 ? 1 : state.lockedBetPerLine);
        state.triggerWildCount = clamp(state.triggerWildCount, 0, StakeSlotEngine.REEL_COUNT);
        state.lastSettledRoundId = Math.max(0L, state.lastSettledRoundId);
        if (state.active && (state.totalAwardedSpins == 0 || state.sessionId <= 0L)) {
            resetToInactive();
        }
    }

    private void resetToInactive() {
        FeatureState blank = FeatureState.inactive();
        state.schemaVersion = blank.schemaVersion;
        state.active = false;
        state.sessionId = 0L;
        state.lastSettledRoundId = 0L;
        state.totalAwardedSpins = 0;
        state.spinsRemaining = 0;
        state.spinsPlayed = 0;
        state.retriggerCount = 0;
        state.totalFeatureWin = 0;
        state.lockedBetPerLine = 1;
        state.triggerWildCount = 0;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
