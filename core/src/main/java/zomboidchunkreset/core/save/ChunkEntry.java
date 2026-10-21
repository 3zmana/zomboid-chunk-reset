package zomboidchunkreset.core.save;

import zomboidchunkreset.core.format.ChunkFileHeader;
import zomboidchunkreset.core.model.ChunkPos;

/**
 * A saved chunk found in {@code map/}.
 *
 * @param pos      chunk coordinates, taken from the file path
 * @param fileSize size of the chunk file in bytes
 * @param header   parsed header, or {@code null} when headers were not read or could not be read
 */
public record ChunkEntry(ChunkPos pos, long fileSize, ChunkFileHeader header) {
}
