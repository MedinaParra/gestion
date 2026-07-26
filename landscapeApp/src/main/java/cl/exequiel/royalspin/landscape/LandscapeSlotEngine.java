package cl.exequiel.royalspin.landscape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Self-contained fictional-credit slot engine for the landscape presentation.
 * Results are computed before any animation starts.
 */
public final class LandscapeSlotEngine {
    public static final int REELS = 5;
    public static final int ROWS = 3;
    public static final int LINES = 20;

    public static final int A = 0;
    public static final int K = 1;
    public static final int Q = 2;
    public static final int J = 3;
    public static final int SEVEN = 4;
    public static final int BAR = 5;
    public static final int BELL = 6;
    public static final int DIAMOND = 7;
    public static final int WILD = 8;

    private static final int[] WEIGHTS = {18, 17, 16, 15, 7, 6, 5, 4, 3};
    private static final int[][] PAYLINES = {
            {1,1,1,1,1}, {0,0,0,0,0}, {2,2,2,2,2},
            {0,1,2,1,0}, {2,1,0,1,2},
            {0,0,1,2,2}, {2,2,1,0,0},
            {1,0,0,0,1}, {1,2,2,2,1},
            {0,1,1,1,0}, {2,1,1,1,2},
            {0,1,0,1,0}, {2,1,2,1,2},
            {1,0,1,0,1}, {1,2,1,2,1},
            {0,2,0,2,0}, {2,0,2,0,2},
            {0,2,2,2,0}, {2,0,0,0,2},
            {1,0,2,0,1}
    };

    private LandscapeSlotEngine() {}

    public static SpinResult spin(Random random, int betPerLine) {
        int[][] board = new int[REELS][ROWS];
        for (int reel = 0; reel < REELS; reel++) {
            for (int row = 0; row < ROWS; row++) {
                board[reel][row] = weightedSymbol(random);
            }
        }
        return evaluate(board, betPerLine);
    }

    /**
     * Deterministic visual-validation boards. They are only selected through the explicit
     * Android intent extra "demo" and never affect normal random play.
     */
    public static SpinResult demo(String scene, int betPerLine) {
        int[][] board = {
                {Q, J, SEVEN},
                {K, DIAMOND, A},
                {A, BAR, K},
                {J, Q, BELL},
                {BELL, DIAMOND, A}
        };

        if ("bell".equals(scene) || "normal".equals(scene)) {
            board[0][1] = BELL; board[1][1] = BELL; board[2][1] = BELL;
        } else if ("bar".equals(scene)) {
            board[0][1] = BAR; board[1][1] = BAR; board[2][1] = BAR;
        } else if ("seven".equals(scene)) {
            board[0][1] = SEVEN; board[1][1] = SEVEN; board[2][1] = SEVEN;
        } else if ("diamond".equals(scene)) {
            board[0][1] = DIAMOND; board[1][1] = DIAMOND; board[2][1] = DIAMOND;
        } else if ("wild".equals(scene) || "big".equals(scene)) {
            board[0][1] = WILD; board[1][1] = WILD; board[2][1] = WILD;
            board[3][1] = WILD;
        } else if ("royal".equals(scene)) {
            for (int reel = 0; reel < REELS; reel++) board[reel][1] = WILD;
        } else if ("feature".equals(scene) || "free".equals(scene)) {
            board[0][1] = WILD; board[1][1] = WILD; board[2][1] = WILD;
            board[3][1] = DIAMOND; board[4][1] = WILD;
        } else if ("anticipation".equals(scene)) {
            board[0][1] = DIAMOND;
            board[1][1] = DIAMOND;
            board[2][1] = J;
            board[3][1] = BAR;
            board[4][1] = WILD;
        }
        return evaluate(board, betPerLine);
    }

    public static SpinResult evaluate(int[][] board, int betPerLine) {
        List<LineWin> wins = new ArrayList<>();
        int payout = 0;
        boolean feature = false;
        int featureWilds = 0;

        for (int line = 0; line < PAYLINES.length; line++) {
            int[] rows = PAYLINES[line];
            int leadingWilds = 0;
            while (leadingWilds < REELS && board[leadingWilds][rows[leadingWilds]] == WILD) {
                leadingWilds++;
            }
            if (leadingWilds >= 3) {
                feature = true;
                featureWilds = Math.max(featureWilds, leadingWilds);
            }

            LineWin best = null;
            for (int candidate = A; candidate <= WILD; candidate++) {
                int count = 0;
                for (int reel = 0; reel < REELS; reel++) {
                    int symbol = board[reel][rows[reel]];
                    if (symbol == candidate || (symbol == WILD && candidate != WILD)) count++;
                    else break;
                }
                if (count >= 3) {
                    int amount = pay(candidate, count) * betPerLine;
                    if (best == null || amount > best.amount) {
                        best = new LineWin(line, rows.clone(), candidate, count, amount);
                    }
                }
            }
            if (best != null) {
                wins.add(best);
                payout += best.amount;
            }
        }
        return new SpinResult(copy(board), payout, wins, feature, featureWilds);
    }

    private static int weightedSymbol(Random random) {
        int total = 0;
        for (int weight : WEIGHTS) total += weight;
        int roll = random.nextInt(total);
        for (int symbol = 0; symbol < WEIGHTS.length; symbol++) {
            roll -= WEIGHTS[symbol];
            if (roll < 0) return symbol;
        }
        return A;
    }

    private static int pay(int symbol, int count) {
        int index = Math.max(3, Math.min(5, count)) - 3;
        int[][] table = {
                {5, 15, 40},
                {5, 15, 40},
                {4, 12, 35},
                {4, 12, 35},
                {18, 70, 280},
                {14, 55, 210},
                {20, 85, 330},
                {25, 110, 450},
                {30, 140, 600}
        };
        return table[symbol][index];
    }

    private static int[][] copy(int[][] source) {
        int[][] result = new int[source.length][];
        for (int i = 0; i < source.length; i++) result[i] = source[i].clone();
        return result;
    }

    public static String name(int symbol) {
        switch (symbol) {
            case A: return "A";
            case K: return "K";
            case Q: return "Q";
            case J: return "J";
            case SEVEN: return "7";
            case BAR: return "BAR";
            case BELL: return "BELL";
            case DIAMOND: return "DIAMOND";
            case WILD: return "WILD";
            default: return "?";
        }
    }

    public static int[] payline(int index) {
        return PAYLINES[Math.max(0, Math.min(PAYLINES.length - 1, index))].clone();
    }

    public static final class SpinResult {
        public final int[][] board;
        public final int payout;
        public final List<LineWin> wins;
        public final boolean freeSpinsTriggered;
        public final int triggerWildCount;

        SpinResult(int[][] board, int payout, List<LineWin> wins,
                   boolean freeSpinsTriggered, int triggerWildCount) {
            this.board = board;
            this.payout = payout;
            this.wins = Collections.unmodifiableList(new ArrayList<>(wins));
            this.freeSpinsTriggered = freeSpinsTriggered;
            this.triggerWildCount = triggerWildCount;
        }
    }

    public static final class LineWin {
        public final int line;
        public final int[] rows;
        public final int symbol;
        public final int count;
        public final int amount;

        LineWin(int line, int[] rows, int symbol, int count, int amount) {
            this.line = line;
            this.rows = rows;
            this.symbol = symbol;
            this.count = count;
            this.amount = amount;
        }
    }
}
