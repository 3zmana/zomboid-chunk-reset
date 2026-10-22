package zomboidchunkreset.core.terrain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import zomboidchunkreset.core.testutil.FakeChunk;

class ChunkContentReaderTest {

    private record Square(int x, int y, int level, int[] sprites) {
    }

    private static List<Square> decode(byte[] data) throws ChunkContentException {
        List<Square> squares = new ArrayList<>();
        ChunkContentReader.read(data, (x, y, level, sprites, count) ->
                squares.add(new Square(x, y, level, Arrays.copyOf(sprites, count))));
        return squares;
    }

    @Test
    void reportsEverySquareWithItsSprites() throws Exception {
        byte[] data = new FakeChunk()
                .ground(0, 0, 100, 200)
                .ground(7, 7, 300)
                .object(3, 5, 1, 400)
                .object(3, 5, 2, 500)
                .ground(3, 5, 600)
                .object(2, 2, -1, 700)
                .build();
        List<Square> squares = decode(data);
        assertEquals(6, squares.size());
        assertEquals(0, squares.get(0).x());
        assertArrayEquals(new int[] {100, 200}, squares.get(0).sprites());
        // Column (2,2): only the basement level.
        assertEquals(-1, squares.get(1).level());
        // Column (3,5): levels in increasing order.
        assertEquals(List.of(0, 1, 2), squares.subList(2, 5).stream().map(Square::level).toList());
        assertEquals(3, squares.get(2).x());
        assertEquals(5, squares.get(2).y());
        assertArrayEquals(new int[] {600}, squares.get(2).sprites());
        assertArrayEquals(new int[] {300}, squares.get(5).sprites());
    }

    @Test
    void skipsComplexObjects() throws Exception {
        byte[] data = new FakeChunk()
                .ground(1, 1, 10)
                .door(1, 1, 11)
                .tree(1, 2, 12)
                .container(1, 3, 13, 3)
                .deadBody(4, 4)
                .ground(6, 6, 14)
                .build();
        List<Square> squares = decode(data);
        assertArrayEquals(new int[] {10, 11}, squares.get(0).sprites());
        assertArrayEquals(new int[] {12}, squares.get(1).sprites());
        assertArrayEquals(new int[] {13}, squares.get(2).sprites());
        assertArrayEquals(new int[] {}, squares.get(3).sprites()); // body only
        assertArrayEquals(new int[] {14}, squares.get(4).sprites());
    }

    @Test
    void stopsOnUnsupportedObjectsAfterReportingEarlierSquares() {
        byte[] data = new FakeChunk().ground(0, 0, 1).zombie(0, 1).ground(0, 2, 3).build();
        List<Square> squares = new ArrayList<>();
        assertThrows(ChunkContentException.class, () -> ChunkContentReader.read(data,
                (x, y, level, sprites, count) -> squares.add(new Square(x, y, level, Arrays.copyOf(sprites, count)))));
        assertEquals(1, squares.size());
    }

    @Test
    void rejectsInconsistentFiles() {
        assertThrows(ChunkContentException.class, () -> decode(new FakeChunk().ground(0, 0, 1).trailingBytes(3).build()));
        assertThrows(ChunkContentException.class, () -> decode(new FakeChunk().ground(0, 0, 1).vehicles(1).build()));
        byte[] full = new FakeChunk().ground(0, 0, 1).build();
        assertThrows(ChunkContentException.class, () -> decode(Arrays.copyOf(full, full.length - 5)));
        assertThrows(ChunkContentException.class, () -> decode(new byte[10]));
        assertThrows(ChunkContentException.class, () -> decode(new FakeChunk().worldVersion(195).build()));
    }

    @Test
    void neverFailsWithAnUncheckedExceptionOnGarbage() {
        java.util.Random random = new java.util.Random(4);
        byte[] valid = new FakeChunk().ground(0, 0, 1).container(2, 2, 5, 4).deadBody(3, 3).build();
        for (int i = 0; i < 2000; i++) {
            byte[] data = valid.clone();
            for (int k = 0; k < 4; k++) {
                int pos = 17 + random.nextInt(data.length - 17);
                data[pos] = (byte) random.nextInt(256);
            }
            try {
                decode(data);
            } catch (ChunkContentException expected) {
                assertTrue(expected.getMessage() != null);
            }
        }
    }
}
