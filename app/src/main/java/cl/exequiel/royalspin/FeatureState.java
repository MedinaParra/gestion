package cl.exequiel.royalspin;

/** Mutable feature state owned by FeatureSessionController. */
public final class FeatureState {
    public boolean active;
    public int totalAwardedSpins;
    public int spinsRemaining;
    public int spinsPlayed;
    public int retriggerCount;
    public int totalFeatureWin;
    public int lockedBetPerLine;
    public int triggerWildCount;

    public FeatureState copy() {
        FeatureState copy = new FeatureState();
        copy.active = active;
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
