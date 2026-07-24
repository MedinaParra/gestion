package cl.exequiel.royalspin.landscape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class LandscapeGameEngine {
    public static final String WILD = "WILD";
    public static final String DIAMOND = "DIAMOND";
    public static final String SEVEN = "SEVEN";
    public static final String BELL = "BELL";
    public static final String BAR = "BAR";
    public static final String A = "A";
    public static final String K = "K";
    public static final String Q = "Q";
    public static final String J = "J";

    public static final int REELS = 5;
    public static final int ROWS = 3;
    public static final int LINES = 20;

    private static final String[] SYMBOLS = {
            WILD, DIAMOND, SEVEN, BELL, BAR, A, K, Q, J
    };

    private static final int[] WEIGHTS = {
            5, 7, 8, 10, 12, 16, 17, 18, 18
    };

    public static final int[][] PAYLINES = {
            {1,1,1,1,1}, {0,0,0,0,0}, {2,2,2,2,2},
            {0,1,2,1,0}, {2,1,0,1,2},
            {0,0,1,2,2}, {2,2,1,0,0},
            {1,0,0,0,1}, {1,2,2,2,1},
            {0,1,1,1,0}, {2,1,1,1,2},
            {1,0,1,2,1}, {1,2,1,0,1},
            {0,1,0,1,0}, {2,1,2,1,2},
            {0,2,0,2,0}, {2,0,2,0,2},
            {1,0,2,0,1}, {1,2,0,2,1},
            {0,2,1,2,0}
    };

    private final Random random;

    public LandscapeGameEngine(long seed) {
        random = new Random(seed);
    }

    public SpinResult spin(int betPerLine, boolean freeSpin) {
        String[][] board = new String[REELS][ROWS];
        for (int reel = 0; reel < REELS; reel++) {
            for (int row = 0; row < ROWS; row++) {
                board[reel][row] = weightedSymbol();
            }
        }
        return evaluate(board, betPerLine, freeSpin);
    }

    public SpinResult scripted(String scene, int betPerLine, boolean freeSpin) {
        String[][] board = {
                {Q, J, SEVEN},
                {WILD, DIAMOND, K},
                {Q, BAR, A},
                {J, Q, K},
                {BELL, DIAMOND, WILD}
        };
        if ("bell".equals(scene)) {
            board[0][1] = BELL; board[1][1] = BELL; board[2][1] = BELL;
        } else if ("bar".equals(scene)) {
            board[0][1] = BAR; board[1][1] = BAR; board[2][1] = BAR; board[3][1] = BAR;
        } else if ("seven".equals(scene)) {
            board[0][2] = SEVEN; board[1][2] = SEVEN; board[2][2] = SEVEN;
            board[3][2] = SEVEN; board[4][2] = SEVEN;
        } else if ("wild".equals(scene) || "feature".equals(scene)) {
            board[0][1] = WILD; board[1][1] = WILD; board[2][1] = WILD;
            board[3][1] = WILD; board[4][1] = WILD;
        } else if ("diamond".equals(scene)) {
            board[0][0] = DIAMOND; board[1][0] = DIAMOND; board[2][0] = DIAMOND;
        }
        return evaluate(board, betPerLine, freeSpin);
    }

    public SpinResult evaluate(String[][] board, int betPerLine, boolean freeSpin) {
        List<LineWin> wins = new ArrayList<>();
        int payout = 0;
        int maxWildPrefix = 0;
        for (int line = 0; line < PAYLINES.length; line++) {
            int[] rows = PAYLINES[line];
            String first = board[0][rows[0]];
            String resolved = first;
            if (WILD.equals(first)) {
                for (int reel = 1; reel < REELS; reel++) {
                    String candidate = board[reel][rows[reel]];
                    if (!WILD.equals(candidate)) {
                        resolved = candidate;
                        break;
                    }
                }
            }

            int count = 0;
            for (int reel = 0; reel < REELS; reel++) {
                String symbol = board[reel][rows[reel]];
                if (symbol.equals(resolved) || WILD.equals(symbol)) count++;
                else break;
            }

            int wildPrefix = 0;
            for (int reel = 0; reel < REELS; reel++) {
                if (WILD.equals(board[reel][rows[reel]])) wildPrefix++;
                else break;
            }
            maxWildPrefix = Math.max(maxWildPrefix, wildPrefix);

            if (count >= 3) {
                int multiplier = pay(resolved, count);
                if (multiplier > 0) {
                    int linePayout = multiplier * betPerLine;
                    payout += linePayout;
                    wins.add(new LineWin(line, rows.clone(), resolved, count, linePayout));
                }
            }
        }

        int awarded = maxWildPrefix >= 3 ? (freeSpin ? 10 : 30) : 0;
        return new SpinResult(copy(board), Collections.unmodifiableList(wins),
                betPerLine * LINES, payout, awarded, maxWildPrefix, freeSpin);
    }

    private String weightedSymbol() {
        int total = 0;
        for (int weight : WEIGHTS) total += weight;
        int roll = random.nextInt(total);
        for (int i = 0; i < WEIGHTS.length; i++) {
            roll -= WEIGHTS[i];
            if (roll < 0) return SYMBOLS[i];
        }
        return Q;
    }

    private static int pay(String symbol, int count) {
        if (count < 3) return 0;
        if (WILD.equals(symbol)) return count == 3 ? 20 : count == 4 ? 80 : 250;
        if (DIAMOND.equals(symbol)) return count == 3 ? 16 : count == 4 ? 50 : 150;
        if (SEVEN.equals(symbol)) return count == 3 ? 14 : count == 4 ? 45 : 125;
        if (BELL.equals(symbol)) return count == 3 ? 10 : count == 4 ? 30 : 90;
        if (BAR.equals(symbol)) return count == 3 ? 8 : count == 4 ? 24 : 70;
        if (A.equals(symbol)) return count == 3 ? 5 : count == 4 ? 14 : 35;
        if (K.equals(symbol)) return count == 3 ? 4 : count == 4 ? 12 : 30;
        if (Q.equals(symbol)) return count == 3 ? 3 : count == 4 ? 10 : 25;
        return count == 3 ? 2 : count == 4 ? 8 : 20;
    }

    private static String[][] copy(String[][] source) {
        String[][] copy = new String[source.length][];
        for (int i = 0; i < source.length; i++) copy[i] = source[i].clone();
        return copy;
    }

    public static final class LineWin {
        public final int line;
        public final int[] rows;
        public final String symbol;
        public final int count;
        public final int payout;

        LineWin(int line, int[] rows, String symbol, int count, int payout) {
            this.line = line;
            this.rows = rows;
            this.symbol = symbol;
            this.count = count;
            this.payout = payout;
        }
    }

    public static final class SpinResult {
        public final String[][] board;
        public final List<LineWin> wins;
        public final int totalBet;
        public final int payout;
        public final int awardedFreeSpins;
        public final int wildPrefix;
        public final boolean freeSpin;

        SpinResult(String[][] board, List<LineWin> wins, int totalBet, int payout,
                   int awardedFreeSpins, int wildPrefix, boolean freeSpin) {
            this.board = board;
            this.wins = wins;
            this.totalBet = totalBet;
            this.payout = payout;
            this.awardedFreeSpins = awardedFreeSpins;
            this.wildPrefix = wildPrefix;
            this.freeSpin = freeSpin;
        }

        public boolean featureTriggered() {
            return awardedFreeSpins > 0;
        }
    }
}
