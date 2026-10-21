package zomboidchunkreset.core.format;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zomboidchunkreset.core.model.CellPos;
import zomboidchunkreset.core.model.ChunkPos;

/** Byte sequences below are copied from the headers of real Build 42 save files. */
class FormatTest {

    @TempDir
    Path dir;

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    @Test
    void chunkHeaderFromRealBytes() {
        byte[] real = bytes(0x00, 0x00, 0x00, 0x00, 0xf9, 0x00, 0x00, 0x6e, 0x74,
                0x00, 0x00, 0x00, 0x00, 0x3a, 0xc0, 0x92, 0xc2);
        ChunkFileHeader h = ChunkFileHeader.parse(ByteBuffer.wrap(real));
        assertEquals(0, h.flag());
        assertEquals(249, h.worldVersion());
        assertEquals(28276, h.declaredLength());
        assertEquals(0x3ac092c2L, h.checksum());
        assertTrue(h.matchesFileSize(28276));
        assertArrayEquals(real, h.toBytes());
    }

    @Test
    void chunkHeaderTooShort() throws IOException {
        Path f = Files.write(dir.resolve("short.bin"), new byte[10]);
        assertThrows(SaveFormatException.class, () -> ChunkFileHeader.read(f));
    }

    @Test
    void regionHeaderFromRealBytesStoresYThenX() throws IOException {
        // Version 249, one entry: 0x0362 (866), 0x0598 (1432) -> file datachunk_1432_866.bin
        byte[] real = bytes(0x00, 0x00, 0x00, 0xf9, 0x00, 0x00, 0x00, 0x01, 0x03, 0x62, 0x05, 0x98);
        RegionHeader h = RegionHeader.parse(dir, real);
        assertEquals(249, h.version());
        assertEquals(List.of(new ChunkPos(1432, 866)), h.chunks());
        assertArrayEquals(real, h.toBytes());

        Path f = Files.write(dir.resolve(RegionHeader.FILE_NAME), real);
        assertEquals(h, RegionHeader.read(f));
    }

    @Test
    void regionHeaderRoundTrip() throws IOException {
        RegionHeader h = new RegionHeader(249, List.of(new ChunkPos(1, 2), new ChunkPos(1700, 300), new ChunkPos(-4, 5)));
        assertEquals(h, RegionHeader.parse(dir, h.toBytes()));
        assertEquals(8 + 4 * 3, h.toBytes().length);
        assertEquals(new RegionHeader(249, List.of()), RegionHeader.parse(dir, new RegionHeader(249, List.of()).toBytes()));
    }

    @Test
    void regionHeaderRejectsSizeMismatch() {
        byte[] wrongCount = bytes(0x00, 0x00, 0x00, 0xf9, 0x00, 0x00, 0x00, 0x02, 0x03, 0x62, 0x05, 0x98);
        assertThrows(SaveFormatException.class, () -> RegionHeader.parse(dir, wrongCount));
        assertThrows(SaveFormatException.class, () -> RegionHeader.parse(dir, new byte[4]));
    }

    @Test
    void regionHeaderRejectsCoordinatesOutside16Bits() {
        RegionHeader h = new RegionHeader(249, List.of(new ChunkPos(40000, 0)));
        assertThrows(IllegalStateException.class, h::toBytes);
    }

    @Test
    void isoRegionChunkFromRealBytes() throws IOException {
        byte[] head = bytes(0x00, 0x00, 0x00, 0x5c, 0x00, 0x00, 0x00, 0xf9, 0x00, 0x00, 0x05, 0x8f, 0x00, 0x00, 0x03, 0x40);
        byte[] content = new byte[92];
        System.arraycopy(head, 0, content, 0, head.length);
        Path f = Files.write(dir.resolve("datachunk_1423_832.bin"), content);
        IsoRegionChunkFile.Header h = IsoRegionChunkFile.readHeader(f);
        assertEquals(92, h.declaredLength());
        assertEquals(249, h.version());
        assertEquals(new ChunkPos(1423, 832), h.chunk());
        assertEquals(Optional.of(new ChunkPos(1423, 832)), IsoRegionChunkFile.parseName("datachunk_1423_832.bin"));
        assertEquals("datachunk_1423_832.bin", IsoRegionChunkFile.fileName(new ChunkPos(1423, 832)));
        assertEquals(Optional.empty(), IsoRegionChunkFile.parseName(RegionHeader.FILE_NAME));
    }

    @Test
    void cellFileNames() {
        assertEquals(Optional.of(new CellPos(42, 26)), CellFileType.ZOMBIE_POPULATION.parseName("zpop_42_26.bin"));
        assertEquals(Optional.empty(), CellFileType.ZOMBIE_POPULATION.parseName("zpop_ids.bin"));
        assertEquals(Optional.empty(), CellFileType.ZOMBIE_POPULATION.parseName("zpop_virtual.bin"));
        assertEquals(Optional.empty(), CellFileType.ZOMBIE_POPULATION.parseName("apop_42_26.bin"));
        assertEquals(Optional.of(new CellPos(-1, 3)), CellFileType.META_CELL.parseName("metacell_-1_3.bin"));
        assertEquals("chunkdata_58_14.bin", CellFileType.CHUNK_DATA.fileName(new CellPos(58, 14)));
        assertEquals("metagrid", CellFileType.META_CELL.folder());
        assertEquals("apop", CellFileType.ANIMAL_POPULATION.folder());
    }

    @Test
    void fileNameParsing() {
        assertEquals(Optional.of(1570), FileNames.parseCoordinate("1570"));
        assertEquals(Optional.of(-12), FileNames.parseCoordinate("-12"));
        assertEquals(Optional.empty(), FileNames.parseCoordinate("abc"));
        assertEquals(Optional.empty(), FileNames.parseCoordinate("12345678901"));
        assertEquals(Optional.empty(), FileNames.parseXY("zpop_1_2_3.bin", "zpop_", ".bin"));
        assertEquals(Optional.empty(), FileNames.parseXY("zpop_.bin", "zpop_", ".bin"));
    }
}
