package cl.exequiel.royalspin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Deterministic 5x3 / 20-line slot model mirrored by the Stake Engine math project.
 * Credits in the Android demo are fictitious and never represent money.
 */
public final class StakeSlotEngine {
    public static final int REEL_COUNT = 5;
    public static final int ROW_COUNT = 3;
    public static final int LINE_COUNT = 20;
    public static final int MIN_BET_PER_LINE = 1;
    public static final int MAX_BET_PER_LINE = 5;
    public static final double THEORETICAL_RTP = 0.954796451;

    public static final String WILD = "W";
    public static final String SEVEN = "7";
    public static final String DIAMOND = "D";
    public static final String BELL = "BELL";
    public static final String BAR = "BAR";
    public static final String ACE = "A";
    public static final String KING = "K";
    public static final String QUEEN = "Q";
    public static final String JACK = "J";

    public static final String[] SYMBOLS = {
            WILD, SEVEN, DIAMOND, BELL, BAR, ACE, KING, QUEEN, JACK
    };

    private static final int[] WEIGHTS = {4, 5, 7, 9, 11, 13, 15, 17, 19};

    public static final int[][] PAYLINES = {
            {0, 0, 0, 0, 0}, {1, 1, 1, 1, 1}, {2, 2, 2, 2, 2},
            {0, 1, 2, 1, 0}, {2, 1, 0, 1, 2}, {0, 0, 1, 2, 2},
            {2, 2, 1, 0, 0}, {1, 0, 1, 2, 1}, {1, 2, 1, 0, 1},
            {0, 1, 1, 1, 2}, {2, 1, 1, 1, 0}, {0, 1, 0, 1, 2},
            {2, 1, 2, 1, 0}, {1, 1, 0, 1, 1}, {1, 1, 2, 1, 1},
            {0, 2, 1, 0, 2}, {2, 0, 1, 2, 0}, {0, 0, 2, 0, 0},
            {2, 2, 0, 2, 2}, {1, 0, 0, 0, 1}
    };

    private static final Map<String, int[]> PAYTABLE = createPaytable();
    private static final String[][] REEL_STRIPS = createReelStrips();

    public SpinResult spin(Random random, int requestedBetPerLine) {
        if (random == null) {
            throw new IllegalArgumentException("random is required");
        }
        int betPerLine = clampBet(requestedBetPerLine);
        String[][] board = new String[REEL_COUNT][ROW_COUNT];
        int[] stops = new int[REEL_COUNT];

        for (int reel = 0; reel < REEL_COUNT; reel++) {
            String[] strip = REEL_STRIPS[reel];
            int stop = random.nextInt(strip.length);
            stops[reel] = stop;
            for (int row = 0; row < ROW_COUNT; row++) {
                board[reel][row] = strip[(stop + row) % strip.length];
            }
        }
        return evaluate(board, stops, betPerLine);
    }

    public SpinResult evaluate(String[][] board, int[] stops, int requestedBetPerLine) {
        validateBoard(board);
        int betPerLine = clampBet(requestedBetPerLine);
        List<LineWin> wins = new ArrayList<>();
        int totalPayout = 0;

        for (int lineIndex = 0; lineIndex < PAYLINES.length; lineIndex++) {
            int[] payline = PAYLINES[lineIndex];
            String[] lineSymbols = new String[REEL_COUNT];
            for (int reel = 0; reel < REEL_COUNT; reel++) {
                lineSymbols[reel] = board[reel][payline[reel]];
            }
            Evaluation evaluation = evaluateLine(lineSymbols);
            if (evaluation.multiplier > 0) {
                int payout = evaluation.multiplier * betPerLine;
                totalPayout += payout;
                wins.add(new LineWin(
                        lineIndex,
                        payline.clone(),
                        evaluation.symbol,
                        evaluation.count,
                        evaluation.multiplier,
                        payout
                ));
            }
        }

        return new SpinResult(
                copyBoard(board),
                stops == null ? new int[REEL_COUNT] : stops.clone(),
                betPerLine,
                betPerLine * LINE_COUNT,
                totalPayout,
                Collections.unmodifiableList(wins)
        );
    }

    static int evaluateLineMultiplierForTest(String[] lineSymbols) {
        return evaluateLine(lineSymbols).multiplier;
    }

    public static int clampBet(int value) {
        return Math.max(MIN_BET_PER_LINE, Math.min(MAX_BET_PER_LINE, value));
    }

    public static String displayLabel(String symbol) {
        switch (symbol) {
            case WILD: return "♛";
            case DIAMOND: return "◆";
            case BELL: return "♢";
            default: return symbol;
        }
    }

    public static int symbolColor(String symbol) {
        switch (symbol) {
            case WILD: return 0xFFF6C453;
            case SEVEN: return 0xFFE83D55;
            case DIAMOND: return 0xFF55C9FF;
            case BELL: return 0xFFF5A742;
            case BAR: return 0xFFE9E5D8;
            case ACE: return 0xFFC98CFF;
            case KING: return 0xFF65E6A4;
            case QUEEN: return 0xFFFF8CC8;
            default: return 0xFF93A7FF;
        }
    }

    private static Evaluation evaluateLine(String[] lineSymbols) {
        if (lineSymbols == null || lineSymbols.length != REEL_COUNT) {
            throw new IllegalArgumentException("A payline must contain exactly five symbols");
        }

        String baseSymbol = WILD;
        for (String symbol : lineSymbols) {
            if (!WILD.equals(symbol)) {
                baseSymbol = symbol;
                break;
            }
        }

        int matched = 0;
        for (String symbol : lineSymbols) {
            if (baseSymbol.equals(symbol) || WILD.equals(symbol)) {
                matched++;
            } else {
                break;
            }
        }

        if (matched < 3) {
            return Evaluation.NONE;
        }

        int multiplier = payoutFor(baseSymbol, matched);
        int wildPrefix = 0;
        for (String symbol : lineSymbols) {
            if (WILD.equals(symbol)) {
                wildPrefix++;
            } else {
                break;
            }
        }
        if (wildPrefix >= 3) {
            int wildMultiplier = payoutFor(WILD, wildPrefix);
            if (wildMultiplier > multiplier) {
                return new Evaluation(WILD, wildPrefix, wildMultiplier);
            }
        }
        return new Evaluation(baseSymbol, matched, multiplier);
    }

    private static int payoutFor(String symbol, int count) {
        int[] payouts = PAYTABLE.get(symbol);
        if (payouts == null || count < 3 || count > 5) {
            return 0;
        }
        return payouts[count - 3];
    }

    private static Map<String, int[]> createPaytable() {
        Map<String, int[]> values = new LinkedHashMap<>();
        values.put(WILD, new int[]{82, 410, 2050});
        values.put(SEVEN, new int[]{67, 256, 1280});
        values.put(DIAMOND, new int[]{51, 169, 850});
        values.put(BELL, new int[]{41, 128, 512});
        values.put(BAR, new int[]{33, 82, 338});
        values.put(ACE, new int[]{17, 41, 169});
        values.put(KING, new int[]{12, 33, 128});
        values.put(QUEEN, new int[]{8, 25, 82});
        values.put(JACK, new int[]{6, 16, 67});
        return Collections.unmodifiableMap(values);
    }

    private static String[][] createReelStrips() {
        String[][] strips = new String[REEL_COUNT][];
        for (int reel = 0; reel < REEL_COUNT; reel++) {
            List<String> strip = new ArrayList<>(100);
            for (int symbolIndex = 0; symbolIndex < SYMBOLS.length; symbolIndex++) {
                for (int count = 0; count < WEIGHTS[symbolIndex]; count++) {
                    strip.add(SYMBOLS[symbolIndex]);
                }
            }
            Collections.shuffle(strip, new Random(0x5A17L + reel * 977L));
            strips[reel] = strip.toArray(new String[0]);
        }
        return strips;
    }

    private static void validateBoard(String[][] board) {
        if (board == null || board.length != REEL_COUNT) {
            throw new IllegalArgumentException("Board must contain five reels");
        }
        for (String[] reel : board) {
            if (reel == null || reel.length != ROW_COUNT) {
                throw new IllegalArgumentException("Each reel must contain three visible rows");
            }
        }
    }

    private static String[][] copyBoard(String[][] source) {
        String[][] copy = new String[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }

    private static final class Evaluation {
        static final Evaluation NONE = new Evaluation("", 0, 0);
        final String symbol;
        final int count;
        final int multiplier;

        Evaluation(String symbol, int count, int multiplier) {
            this.symbol = symbol;
            this.count = count;
            this.multiplier = multiplier;
        }
    }

    public static final class LineWin {
        public final int lineIndex;
        public final int[] rows;
        public final String symbol;
        public final int count;
        public final int multiplier;
        public final int payout;

        LineWin(int lineIndex, int[] rows, String symbol, int count, int multiplier, int payout) {
            this.lineIndex = lineIndex;
            this.rows = rows;
            this.symbol = symbol;
            this.count = count;
            this.multiplier = multiplier;
            this.payout = payout;
        }
    }

    public static final class SpinResult {
        public final String[][] board;
        public final int[] stops;
        public final int betPerLine;
        public final int totalBet;
        public final int totalPayout;
        public final List<LineWin> lineWins;

        SpinResult(String[][] board, int[] stops, int betPerLine, int totalBet,
                   int totalPayout, List<LineWin> lineWins) {
            this.board = board;
            this.stops = stops;
            this.betPerLine = betPerLine;
            this.totalBet = totalBet;
            this.totalPayout = totalPayout;
            this.lineWins = lineWins;
        }

        public double payoutMultiplier() {
            return totalBet == 0 ? 0.0 : totalPayout / (double) totalBet;
        }
    }
}
