package cl.exequiel.royalspin;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class RoundTransactionRepositoryTest {
    @Test
    public void boardRoundTripsWithoutChangingSymbols() {
        String[][] board = {
                {"W", "A", "J"}, {"7", "Q", "BAR"}, {"D", "K", "A"},
                {"BELL", "J", "Q"}, {"W", "BAR", "K"}
        };
        String encoded = RoundTransactionRepository.encodeBoard(board);
        String[][] decoded = RoundTransactionRepository.decodeBoard(encoded);
        for (int reel = 0; reel < board.length; reel++) {
            assertArrayEquals(board[reel], decoded[reel]);
        }
    }

    @Test
    public void stopsRoundTrip() {
        int[] stops = {4, 18, 35, 62, 99};
        assertArrayEquals(stops, RoundTransactionRepository.decodeStops(
                RoundTransactionRepository.encodeStops(stops)));
    }

    @Test
    public void malformedBoardIsRejected() {
        assertEquals(null, RoundTransactionRepository.decodeBoard("W,A,J|7,Q,BAR"));
    }
}
