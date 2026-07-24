package cl.exequiel.royalspin;

import android.content.SharedPreferences;

/** Persists feature state synchronously so process death cannot lose a completed transition. */
public final class FeatureSessionRepository {
    private static final String PREFIX = "feature_";
    private final SharedPreferences preferences;

    public FeatureSessionRepository(SharedPreferences preferences) {
        if (preferences == null) throw new IllegalArgumentException("preferences is required");
        this.preferences = preferences;
    }

    public FeatureState load() {
        FeatureState state = new FeatureState();
        state.schemaVersion = preferences.getInt(PREFIX + "schema", FeatureState.SCHEMA_VERSION);
        state.active = preferences.getBoolean(PREFIX + "active", false);
        state.sessionId = preferences.getLong(PREFIX + "session_id", 0L);
        state.lastSettledRoundId = preferences.getLong(PREFIX + "last_round", 0L);
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
        boolean ok = preferences.edit()
                .putInt(PREFIX + "schema", FeatureState.SCHEMA_VERSION)
                .putBoolean(PREFIX + "active", state.active)
                .putLong(PREFIX + "session_id", state.sessionId)
                .putLong(PREFIX + "last_round", state.lastSettledRoundId)
                .putInt(PREFIX + "awarded", state.totalAwardedSpins)
                .putInt(PREFIX + "remaining", state.spinsRemaining)
                .putInt(PREFIX + "played", state.spinsPlayed)
                .putInt(PREFIX + "retriggers", state.retriggerCount)
                .putInt(PREFIX + "win", state.totalFeatureWin)
                .putInt(PREFIX + "bet", state.lockedBetPerLine)
                .putInt(PREFIX + "wilds", state.triggerWildCount)
                .commit();
        if (!ok) throw new IllegalStateException("Could not persist feature state");
    }

    public void clear() {
        SharedPreferences.Editor e = preferences.edit();
        e.remove(PREFIX + "schema").remove(PREFIX + "active")
                .remove(PREFIX + "session_id").remove(PREFIX + "last_round")
                .remove(PREFIX + "awarded").remove(PREFIX + "remaining")
                .remove(PREFIX + "played").remove(PREFIX + "retriggers")
                .remove(PREFIX + "win").remove(PREFIX + "bet").remove(PREFIX + "wilds");
        e.commit();
    }
}
