package zomboidchunkreset.core.format;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import zomboidchunkreset.core.model.ChunkPos;

/**
 * {@code isoregiondata/RegionHeader.bin}: the list of chunks that have a
 * {@code datachunk_<chunkX>_<chunkY>.bin} file (big-endian).
 *
 * <pre>
 * int   version            (249 in the verified saves)
 * int   count
 * count x { short a; short b; }   with chunk X = b and chunk Y = a
 * </pre>
 *
 * The pair order (Y first, then X) is the reverse of the file names. It was verified on every
 * entry of the two reference saves: read as (X, Y), no entry matched an existing file.
 * The file size is always exactly {@code 8 + 4 * count}.
 */
public record RegionHeader(int version, List<ChunkPos> chunks) {

    /** Name of the header file inside {@code isoregiondata/}. */
    public static final String FILE_NAME = "RegionHeader.bin";

    public RegionHeader {
        chunks = List.copyOf(chunks);
    }

    /** Parses a header from its full byte content. */
    public static RegionHeader parse(Path file, byte[] bytes) throws SaveFormatException {
        if (bytes.length < 8) {
            throw new SaveFormatException(file, "region header shorter than 8 bytes");
        }
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int version = b.getInt();
        int count = b.getInt();
        if (count < 0 || (long) bytes.length != 8L + 4L * count) {
            throw new SaveFormatException(file, "region header size " + bytes.length
                    + " does not match its entry count " + count);
        }
        List<ChunkPos> chunks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            short first = b.getShort();
            short second = b.getShort();
            chunks.add(new ChunkPos(second, first));
        }
        return new RegionHeader(version, chunks);
    }

    /** Reads {@code RegionHeader.bin}. */
    public static RegionHeader read(Path file) throws IOException {
        return parse(file, Files.readAllBytes(file));
    }

    /** Serializes this header in the same layout it was read from. */
    public byte[] toBytes() {
        ByteBuffer b = ByteBuffer.allocate(8 + 4 * chunks.size()).order(ByteOrder.BIG_ENDIAN);
        b.putInt(version).putInt(chunks.size());
        for (ChunkPos chunk : chunks) {
            if (chunk.x() != (short) chunk.x() || chunk.y() != (short) chunk.y()) {
                throw new IllegalStateException(chunk + " does not fit in the 16-bit header fields");
            }
            b.putShort((short) chunk.y()).putShort((short) chunk.x());
        }
        return b.array();
    }
}
