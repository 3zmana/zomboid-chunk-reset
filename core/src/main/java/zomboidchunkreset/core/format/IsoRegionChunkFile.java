package zomboidchunkreset.core.format;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import zomboidchunkreset.core.model.ChunkPos;

/**
 * {@code isoregiondata/datachunk_<chunkX>_<chunkY>.bin}. Uses the same chunk coordinates as
 * {@code map/}. Its first 16 bytes are (big-endian ints): length (= file size), version,
 * chunk X, chunk Y. The rest is not decoded.
 */
public final class IsoRegionChunkFile {

    /** File name prefix. */
    public static final String PREFIX = "datachunk_";

    /** File name suffix. */
    public static final String SUFFIX = ".bin";

    /** Size of the decoded part of the header. */
    public static final int HEADER_SIZE = 16;

    private IsoRegionChunkFile() {
    }

    /** Header fields of a data chunk file. */
    public record Header(int declaredLength, int version, ChunkPos chunk) {
    }

    /** Parses a file name such as {@code datachunk_1423_832.bin}. */
    public static Optional<ChunkPos> parseName(String fileName) {
        return FileNames.parseXY(fileName, PREFIX, SUFFIX).map(xy -> new ChunkPos(xy[0], xy[1]));
    }

    /** File name for the given chunk. */
    public static String fileName(ChunkPos chunk) {
        return PREFIX + chunk.x() + "_" + chunk.y() + SUFFIX;
    }

    /** Reads the decoded part of the header. */
    public static Header readHeader(Path file) throws IOException {
        ByteBuffer b = ByteBuffer.allocate(HEADER_SIZE);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            while (b.hasRemaining()) {
                if (channel.read(b) < 0) {
                    throw new SaveFormatException(file, "data chunk shorter than " + HEADER_SIZE + " bytes");
                }
            }
        }
        b.flip().order(ByteOrder.BIG_ENDIAN);
        int length = b.getInt();
        int version = b.getInt();
        int x = b.getInt();
        int y = b.getInt();
        return new Header(length, version, new ChunkPos(x, y));
    }
}
