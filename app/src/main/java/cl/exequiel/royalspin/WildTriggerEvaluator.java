package cl.exequiel.royalspin;

import java.util.ArrayList;
import java.util.List;

/** Pure feature evaluator. It never pays credits and never starts animations. */
public final class WildTriggerEvaluator {

    public FeatureTrigger evaluate(String[][] board, int[][] paylines, GameMode mode) {
        validate(board, paylines);
        List<Integer> lines = new ArrayList<>();
        int highest = 0;

        for (int lineIndex = 0; lineIndex < paylines.length; lineIndex++) {
            int[] line = paylines[lineIndex];
            int wildPrefix = 0;
            for (int reel = 0; reel < StakeSlotEngine.REEL_COUNT; reel++) {
                if (StakeSlotEngine.WILD.equals(board[reel][line[reel]])) {
                    wildPrefix++;
                } else {
                    break;
                }
            }
            if (wildPrefix >= FeatureRules.TRIGGER_WILDS) {
                lines.add(lineIndex);
                highest = Math.max(highest, wildPrefix);
            }
        }

        if (lines.isEmpty()) return FeatureTrigger.NONE;
        if (mode == GameMode.FREE_SPINS) {
            return new FeatureTrigger(true, highest, 0,
                    FeatureRules.RETRIGGER_FREE_SPINS, lines);
        }
        return new FeatureTrigger(true, highest,
                FeatureRules.INITIAL_FREE_SPINS, 0, lines);
    }

    private static void validate(String[][] board, int[][] paylines) {
        if (board == null || board.length != StakeSlotEngine.REEL_COUNT) {
            throw new IllegalArgumentException("Board must contain five reels");
        }
        for (String[] reel : board) {
            if (reel == null || reel.length != StakeSlotEngine.ROW_COUNT) {
                throw new IllegalArgumentException("Each reel must contain three rows");
            }
        }
        if (paylines == null || paylines.length == 0) {
            throw new IllegalArgumentException("At least one payline is required");
        }
        for (int[] line : paylines) {
            if (line == null || line.length != StakeSlotEngine.REEL_COUNT) {
                throw new IllegalArgumentException("Each payline must contain five rows");
            }
            for (int row : line) {
                if (row < 0 || row >= StakeSlotEngine.ROW_COUNT) {
                    throw new IllegalArgumentException("Payline row out of range");
                }
            }
        }
    }
}
