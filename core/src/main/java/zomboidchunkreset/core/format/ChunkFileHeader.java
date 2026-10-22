package zomboidchunkreset.core.format;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The first 17 bytes of a chunk file {@code map/<chunkX>/<chunkY>.bin} (big-endian).
 *
 * <pre>
 * offset size field
 *      0    1 flag            (always 0 in the verified saves)
 *      1    4 worldVersion    (int, 249 in the verified saves)
 *      5    4 declaredLength  (int, equals the file size in every verified file)
 *      9    8 checksum        (long, presumed checksum, not verified)
 * </pre>
 *
 * The file does not contain its own chunk coordinates: they only come from its path.
 * The rest of the file is only read (never written) to draw the map, see
 * {@code zomboidchunkreset.core.terrain.ChunkContentReader}; deletion always removes whole files.
 */
public record ChunkFileHeader(int flag, int worldVersion, int declaredLength, long checksum) {

    /** Header size in bytes. */
    public static final int SIZE = 17;

    /** Parses a header from the start of the given buffer. */
    public static ChunkFileHeader parse(ByteBuffer buffer) {
        ByteBuffer b = buffer.duplicate().order(ByteOrder.BIG_ENDIAN);
        int flag = b.get() & 0xFF;
        int worldVersion = b.getInt();
        int declaredLength = b.getInt();
        long checksum = b.getLong();
        return new ChunkFileHeader(flag, worldVersion, declaredLength, checksum);
    }

    /** Reads the header of a chunk file. */
    public static ChunkFileHeader read(Path file) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(SIZE);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) {
                    throw new SaveFormatException(file, "chunk file shorter than its " + SIZE + "-byte header");
                }
            }
        }
        buffer.flip();
        return parse(buffer);
    }

    /** Writes this header as bytes (used by tests to build synthetic chunk files). */
    public byte[] toBytes() {
        return ByteBuffer.allocate(SIZE).order(ByteOrder.BIG_ENDIAN)
                .put((byte) flag).putInt(worldVersion).putInt(declaredLength).putLong(checksum)
                .array();
    }

    /** Whether the declared length matches the actual file size, as in every verified file. */
    public boolean matchesFileSize(long fileSize) {
        return declaredLength == fileSize;
    }
}
