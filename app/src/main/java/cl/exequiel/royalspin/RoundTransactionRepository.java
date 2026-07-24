package cl.exequiel.royalspin;

import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Persists a precalculated round before presentation. Credits and transaction flags are
 * committed in the same SharedPreferences transaction so recovery is idempotent.
 */
public final class RoundTransactionRepository {
    private static final String P = "round_v2_";
    private final SharedPreferences prefs;

    public RoundTransactionRepository(SharedPreferences prefs) {
        if (prefs == null) throw new IllegalArgumentException("preferences is required");
        this.prefs = prefs;
    }

    public Record begin(long roundId, StakeSlotEngine.SpinResult result,
                        int creditsAfterDebit, boolean betDebited) {
        if (result == null) throw new IllegalArgumentException("result is required");
        Record record = new Record(roundId, true, result.mode, result.betPerLine,
                result.board, result.stops, result.totalPayout,
                betDebited, false, false);
        SharedPreferences.Editor e = prefs.edit();
        write(e, record);
        e.putInt("credits", Math.max(0, creditsAfterDebit));
        if (!e.commit()) throw new IllegalStateException("Could not persist round start");
        return record;
    }

    public Record load() {
        if (!prefs.getBoolean(P + "active", false)) return null;
        long id = prefs.getLong(P + "id", 0L);
        GameMode mode;
        try {
            mode = GameMode.valueOf(prefs.getString(P + "mode", GameMode.BASE_GAME.name()));
        } catch (RuntimeException ignored) {
            mode = GameMode.BASE_GAME;
        }
        int bet = StakeSlotEngine.clampBet(prefs.getInt(P + "bet", 1));
        String[][] board = decodeBoard(prefs.getString(P + "board", ""));
        int[] stops = decodeStops(prefs.getString(P + "stops", ""));
        if (board == null) {
            clear();
            return null;
        }
        return new Record(id, true, mode, bet, board, stops,
                Math.max(0, prefs.getInt(P + "payout", 0)),
                prefs.getBoolean(P + "bet_debited", false),
                prefs.getBoolean(P + "payout_committed", false),
                prefs.getBoolean(P + "feature_settled", false));
    }

    public StakeSlotEngine.SpinResult restoreResult(StakeSlotEngine engine, Record record) {
        if (engine == null || record == null) return null;
        return engine.evaluate(record.board, record.stops, record.betPerLine, record.mode);
    }

    public Record commitPayout(Record record, int creditsAfterPayout) {
        if (record == null || !record.active || record.payoutCommitted) return record;
        Record updated = record.withPayoutCommitted();
        SharedPreferences.Editor e = prefs.edit();
        write(e, updated);
        e.putInt("credits", Math.max(0, creditsAfterPayout));
        if (!e.commit()) throw new IllegalStateException("Could not commit payout");
        return updated;
    }

    public Record markFeatureSettled(Record record) {
        if (record == null || !record.active || record.featureSettled) return record;
        Record updated = record.withFeatureSettled();
        SharedPreferences.Editor e = prefs.edit();
        write(e, updated);
        if (!e.commit()) throw new IllegalStateException("Could not settle feature round");
        return updated;
    }

    public void complete(Record record) {
        if (record == null) return;
        SharedPreferences.Editor e = prefs.edit();
        remove(e);
        if (!e.commit()) throw new IllegalStateException("Could not complete round");
    }

    public void clear() {
        SharedPreferences.Editor e = prefs.edit();
        remove(e);
        e.commit();
    }

    private static void write(SharedPreferences.Editor e, Record r) {
        e.putBoolean(P + "active", r.active)
                .putLong(P + "id", r.roundId)
                .putString(P + "mode", r.mode.name())
                .putInt(P + "bet", r.betPerLine)
                .putString(P + "board", encodeBoard(r.board))
                .putString(P + "stops", encodeStops(r.stops))
                .putInt(P + "payout", r.payout)
                .putBoolean(P + "bet_debited", r.betDebited)
                .putBoolean(P + "payout_committed", r.payoutCommitted)
                .putBoolean(P + "feature_settled", r.featureSettled);
    }

    private static void remove(SharedPreferences.Editor e) {
        e.remove(P + "active").remove(P + "id").remove(P + "mode")
                .remove(P + "bet").remove(P + "board").remove(P + "stops")
                .remove(P + "payout").remove(P + "bet_debited")
                .remove(P + "payout_committed").remove(P + "feature_settled");
    }

    static String encodeBoard(String[][] board) {
        StringBuilder out = new StringBuilder();
        for (int reel = 0; reel < StakeSlotEngine.REEL_COUNT; reel++) {
            if (reel > 0) out.append('|');
            for (int row = 0; row < StakeSlotEngine.ROW_COUNT; row++) {
                if (row > 0) out.append(',');
                out.append(board[reel][row]);
            }
        }
        return out.toString();
    }

    static String[][] decodeBoard(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        String[] reels = encoded.split("\\|", -1);
        if (reels.length != StakeSlotEngine.REEL_COUNT) return null;
        String[][] board = new String[StakeSlotEngine.REEL_COUNT][StakeSlotEngine.ROW_COUNT];
        for (int reel = 0; reel < reels.length; reel++) {
            String[] rows = reels[reel].split(",", -1);
            if (rows.length != StakeSlotEngine.ROW_COUNT) return null;
            for (int row = 0; row < rows.length; row++) {
                if (rows[row].isEmpty()) return null;
                board[reel][row] = rows[row].toUpperCase(Locale.US);
            }
        }
        return board;
    }

    static String encodeStops(int[] stops) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < StakeSlotEngine.REEL_COUNT; i++) {
            if (i > 0) out.append(',');
            out.append(stops != null && i < stops.length ? stops[i] : 0);
        }
        return out.toString();
    }

    static int[] decodeStops(String encoded) {
        int[] stops = new int[StakeSlotEngine.REEL_COUNT];
        if (encoded == null || encoded.isEmpty()) return stops;
        String[] values = encoded.split(",", -1);
        if (values.length != stops.length) return stops;
        for (int i = 0; i < values.length; i++) {
            try { stops[i] = Integer.parseInt(values[i]); }
            catch (NumberFormatException ignored) { stops[i] = 0; }
        }
        return stops;
    }

    public static final class Record {
        public final long roundId;
        public final boolean active;
        public final GameMode mode;
        public final int betPerLine;
        public final String[][] board;
        public final int[] stops;
        public final int payout;
        public final boolean betDebited;
        public final boolean payoutCommitted;
        public final boolean featureSettled;

        Record(long roundId, boolean active, GameMode mode, int betPerLine,
               String[][] board, int[] stops, int payout, boolean betDebited,
               boolean payoutCommitted, boolean featureSettled) {
            this.roundId = roundId;
            this.active = active;
            this.mode = mode;
            this.betPerLine = betPerLine;
            this.board = board;
            this.stops = stops;
            this.payout = payout;
            this.betDebited = betDebited;
            this.payoutCommitted = payoutCommitted;
            this.featureSettled = featureSettled;
        }

        Record withPayoutCommitted() {
            return new Record(roundId, active, mode, betPerLine, board, stops, payout,
                    betDebited, true, featureSettled);
        }

        Record withFeatureSettled() {
            return new Record(roundId, active, mode, betPerLine, board, stops, payout,
                    betDebited, payoutCommitted, true);
        }
    }
}
