package zomboidchunkreset.core.format;

import java.util.Optional;

import zomboidchunkreset.core.model.CellPos;

/**
 * Files stored once per cell (32 x 32 chunks). Their content is not decoded, so they can only be
 * kept or deleted as a whole.
 */
public enum CellFileType {

    /** Zombie population. Also exists for cells without any saved chunk. */
    ZOMBIE_POPULATION("zpop", "zpop_"),

    /** Animal population (new in Build 42). */
    ANIMAL_POPULATION("apop", "apop_"),

    /** Per-cell chunk data. Format unknown. */
    CHUNK_DATA("chunkdata", "chunkdata_"),

    /** Per-cell metadata. One byte long in the verified saves. */
    META_CELL("metagrid", "metacell_");

    private static final String SUFFIX = ".bin";

    private final String folder;
    private final String prefix;

    CellFileType(String folder, String prefix) {
        this.folder = folder;
        this.prefix = prefix;
    }

    /** Folder inside the save that contains this type of file. */
    public String folder() {
        return folder;
    }

    /** File name for the given cell. */
    public String fileName(CellPos cell) {
        return prefix + cell.x() + "_" + cell.y() + SUFFIX;
    }

    /**
     * Parses a file name of this type. Names that do not end with two coordinates
     * (such as {@code zpop_ids.bin} or {@code zpop_virtual.bin}) are not per-cell files.
     */
    public Optional<CellPos> parseName(String fileName) {
        return FileNames.parseXY(fileName, prefix, SUFFIX).map(xy -> new CellPos(xy[0], xy[1]));
    }
}
