package cl.exequiel.royalspin;

/** Mutable feature state owned exclusively by FeatureSessionController. */
public final class FeatureState {
    public static final int SCHEMA_VERSION = 2;

    public int schemaVersion = SCHEMA_VERSION;
    public boolean active;
    public long sessionId;
    public long lastSettledRoundId;
    public int totalAwardedSpins;
    public int spinsRemaining;
    public int spinsPlayed;
    public int retriggerCount;
    public int totalFeatureWin;
    public int lockedBetPerLine;
    public int triggerWildCount;

    public FeatureState copy() {
        FeatureState copy = new FeatureState();
        copy.schemaVersion = schemaVersion;
        copy.active = active;
        copy.sessionId = sessionId;
        copy.lastSettledRoundId = lastSettledRoundId;
        copy.totalAwardedSpins = totalAwardedSpins;
        copy.spinsRemaining = spinsRemaining;
        copy.spinsPlayed = spinsPlayed;
        copy.retriggerCount = retriggerCount;
        copy.totalFeatureWin = totalFeatureWin;
        copy.lockedBetPerLine = lockedBetPerLine;
        copy.triggerWildCount = triggerWildCount;
        return copy;
    }

    public static FeatureState inactive() {
        return new FeatureState();
    }
}
