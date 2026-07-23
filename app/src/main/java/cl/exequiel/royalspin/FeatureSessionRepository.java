package cl.exequiel.royalspin;

import android.content.SharedPreferences;

/** Persists feature counters so an interrupted bonus can be recovered safely. */
public final class FeatureSessionRepository {
    private static final String PREFIX = "feature_";
    private final SharedPreferences preferences;

    public FeatureSessionRepository(SharedPreferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("preferences is required");
        this.preferences = preferences;
    }

    public FeatureState load() {
        FeatureState state = new FeatureState();
        state.active = preferences.getBoolean(PREFIX + "active", false);
        state.totalAwardedSpins = preferences.getInt(PREFIX + "awarded", 0);
        state.spinsRemaining = preferences.getInt(PREFIX + "remaining", 0);
        state.spinsPlayed = preferences.getInt(PREFIX + "played", 0);
        state.retriggerCount = preferences.getInt(PREFIX + "retriggers", 0);
        state.totalFeatureWin = preferences.getInt(PREFIX + "win", 0);
        state.lockedBetPerLine = preferences.getInt(PREFIX + "bet", 1);
        state.triggerWildCount = preferences.getInt(PREFIX + "wilds", 0);
        return state;
    }

    public void save(FeatureState state) {
        if (state == null) return;
        preferences.edit()
                .putBoolean(PREFIX + "active", state.active)
                .putInt(PREFIX + "awarded", state.totalAwardedSpins)
                .putInt(PREFIX + "remaining", state.spinsRemaining)
                .putInt(PREFIX + "played", state.spinsPlayed)
                .putInt(PREFIX + "retriggers", state.retriggerCount)
                .putInt(PREFIX + "win", state.totalFeatureWin)
                .putInt(PREFIX + "bet", state.lockedBetPerLine)
                .putInt(PREFIX + "wilds", state.triggerWildCount)
                .apply();
    }

    public void clear() {
        preferences.edit()
                .remove(PREFIX + "active")
                .remove(PREFIX + "awarded")
                .remove(PREFIX + "remaining")
                .remove(PREFIX + "played")
                .remove(PREFIX + "retriggers")
                .remove(PREFIX + "win")
                .remove(PREFIX + "bet")
                .remove(PREFIX + "wilds")
                .apply();
    }
}
