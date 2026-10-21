package zomboidchunkreset.core.save;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import zomboidchunkreset.core.db.PlayerRecord;
import zomboidchunkreset.core.db.PlayersDatabase;
import zomboidchunkreset.core.db.VehicleRecord;
import zomboidchunkreset.core.db.VehiclesDatabase;
import zomboidchunkreset.core.format.CellFileType;
import zomboidchunkreset.core.format.ChunkFileHeader;
import zomboidchunkreset.core.format.FileNames;
import zomboidchunkreset.core.format.IsoRegionChunkFile;
import zomboidchunkreset.core.format.RegionHeader;
import zomboidchunkreset.core.model.CellPos;
import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;

/**
 * Scans a save folder and builds a {@link SaveIndex}. Never writes anything: files are only listed
 * and read, and the SQLite databases are opened read-only.
 */
public final class SaveScanner {

    /** Folder holding the chunk files. */
    public static final String MAP_FOLDER = "map";

    /** Folder holding {@code RegionHeader.bin} and the {@code datachunk} files. */
    public static final String ISO_REGION_FOLDER = "isoregiondata";

    /**
     * Global files that were seen to contain world positions on the reference saves. Their formats
     * are not fully decoded and they are never modified; they are listed so that the user interface
     * can warn about objects that may be left behind in deleted areas.
     */
    public static final List<String> GLOBAL_FILES_WITH_POSITIONS = List.of(
            "gos_farming.bin", "gos_campfire.bin", "gos_trap.bin", "gos_feedingTrough.bin",
            "gos_rainbarrel.bin", "map_basements.bin", "entity_data.bin", "reanimated.bin", "map_meta.bin");

    private SaveScanner() {
    }

    /** Scans with {@link ScanOptions#FULL} and no progress reporting. */
    public static SaveIndex scan(Path root) throws IOException {
        return scan(root, ScanOptions.FULL, ScanProgress.NONE);
    }

    /**
     * Scans a save folder.
     *
     * @throws NotASaveException     if the folder has no {@code map/} folder
     * @throws InterruptedIOException if the calling thread is interrupted
     */
    public static SaveIndex scan(Path root, ScanOptions options, ScanProgress progress) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new NotASaveException(root, "not a folder");
        }
        Path mapFolder = root.resolve(MAP_FOLDER);
        if (!Files.isDirectory(mapFolder)) {
            throw new NotASaveException(root, "no '" + MAP_FOLDER + "' folder");
        }
        List<ScanIssue> issues = new ArrayList<>();

        // Chunks
        Map<ChunkPos, ChunkEntry> chunks = new HashMap<>();
        Set<Integer> worldVersions = new TreeSet<>();
        ChunkBounds bounds = scanChunks(mapFolder, options, progress, chunks, worldVersions, issues);

        // Per-cell files
        Map<CellFileType, Map<CellPos, Path>> cellFiles = new EnumMap<>(CellFileType.class);
        for (CellFileType type : CellFileType.values()) {
            cellFiles.put(type, scanCellFiles(root.resolve(type.folder()), type));
        }

        // Iso regions
        Path isoFolder = root.resolve(ISO_REGION_FOLDER);
        Map<ChunkPos, Path> isoFiles = new HashMap<>();
        RegionHeader regionHeader = scanIsoRegions(isoFolder, options, isoFiles, issues);

        // Databases
        List<PlayerRecord> players = new ArrayList<>();
        Path playersDb = root.resolve(PlayersDatabase.FILE_NAME);
        if (Files.isRegularFile(playersDb)) {
            try {
                players.addAll(PlayersDatabase.read(playersDb));
            } catch (IOException e) {
                issues.add(new ScanIssue(ScanIssue.Type.DATABASE_UNREADABLE, playersDb, e.getMessage()));
            }
        }
        for (PlayerRecord p : players) {
            if (p.storedChunk() != null && !p.storedChunk().equals(p.chunk())) {
                issues.add(new ScanIssue(ScanIssue.Type.PLAYER_CHUNK_MISMATCH, playersDb,
                        "stored " + p.storedChunk() + " but position is in " + p.chunk()));
            }
        }
        List<VehicleRecord> vehicles = new ArrayList<>();
        Path vehiclesDb = root.resolve(VehiclesDatabase.FILE_NAME);
        if (Files.isRegularFile(vehiclesDb)) {
            try {
                vehicles.addAll(VehiclesDatabase.read(vehiclesDb));
            } catch (IOException e) {
                issues.add(new ScanIssue(ScanIssue.Type.DATABASE_UNREADABLE, vehiclesDb, e.getMessage()));
            }
        }
        for (VehicleRecord v : vehicles) {
            if (!v.storedChunk().equals(v.chunkFromPosition())) {
                issues.add(new ScanIssue(ScanIssue.Type.VEHICLE_CHUNK_MISMATCH, vehiclesDb,
                        "vehicle " + v.id() + ": stored " + v.storedChunk() + " but position is in "
                                + v.chunkFromPosition()));
            }
        }

        SaveKindDetector.Result kind = SaveKindDetector.detect(root, players);

        List<String> globalFiles = new ArrayList<>();
        for (String name : GLOBAL_FILES_WITH_POSITIONS) {
            if (Files.isRegularFile(root.resolve(name))) {
                globalFiles.add(name);
            }
        }

        return new SaveIndex(root, kind.kind(), kind.signals(), chunks, bounds, worldVersions, cellFiles,
                regionHeader, isoFiles, players, vehicles, globalFiles, issues);
    }

    private static ChunkBounds scanChunks(Path mapFolder, ScanOptions options, ScanProgress progress,
                                          Map<ChunkPos, ChunkEntry> chunks, Set<Integer> worldVersions,
                                          List<ScanIssue> issues) throws IOException {
        List<Path> columns = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(mapFolder)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry) && FileNames.parseCoordinate(name).isPresent()) {
                    columns.add(entry);
                } else {
                    issues.add(new ScanIssue(ScanIssue.Type.MAP_UNEXPECTED_ENTRY, entry, "ignored"));
                }
            }
        }
        ChunkBounds[] bounds = {null};
        int done = 0;
        progress.update(0, columns.size());
        for (Path column : columns) {
            int x = FileNames.parseCoordinate(column.getFileName().toString()).orElseThrow();
            // walkFileTree hands over the attributes obtained while listing the folder, which saves
            // one file-system call per chunk (noticeable with tens of thousands of chunks).
            Files.walkFileTree(column, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedIOException("scan cancelled");
                    }
                    Optional<Integer> y = parseChunkFileName(file.getFileName().toString());
                    if (y.isEmpty() || !attrs.isRegularFile()) {
                        issues.add(new ScanIssue(ScanIssue.Type.MAP_UNEXPECTED_ENTRY, file, "ignored"));
                        return FileVisitResult.CONTINUE;
                    }
                    ChunkPos pos = new ChunkPos(x, y.get());
                    long size = attrs.size();
                    ChunkFileHeader header = null;
                    if (options.readChunkHeaders()) {
                        try {
                            header = ChunkFileHeader.read(file);
                            worldVersions.add(header.worldVersion());
                            if (!header.matchesFileSize(size)) {
                                issues.add(new ScanIssue(ScanIssue.Type.CHUNK_LENGTH_MISMATCH, file,
                                        "declared " + header.declaredLength() + " bytes, file has " + size));
                            }
                        } catch (InterruptedIOException e) {
                            throw e;
                        } catch (IOException e) {
                            issues.add(new ScanIssue(ScanIssue.Type.CHUNK_UNREADABLE, file, e.getMessage()));
                        }
                    }
                    chunks.put(pos, new ChunkEntry(pos, size, header));
                    bounds[0] = bounds[0] == null ? ChunkBounds.of(pos, pos) : bounds[0].including(pos);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    issues.add(new ScanIssue(ScanIssue.Type.CHUNK_UNREADABLE, file, e.getMessage()));
                    return FileVisitResult.CONTINUE;
                }
            });
            progress.update(++done, columns.size());
        }
        return bounds[0];
    }

    /** Parses {@code <y>.bin}. */
    static Optional<Integer> parseChunkFileName(String name) {
        if (!name.endsWith(".bin")) {
            return Optional.empty();
        }
        return FileNames.parseCoordinate(name.substring(0, name.length() - 4));
    }

    private static Map<CellPos, Path> scanCellFiles(Path folder, CellFileType type) throws IOException {
        Map<CellPos, Path> files = new HashMap<>();
        if (!Files.isDirectory(folder)) {
            return files;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path file : stream) {
                // Other files in these folders (such as zpop_ids.bin) are global and simply not listed.
                type.parseName(file.getFileName().toString())
                        .filter(cell -> Files.isRegularFile(file))
                        .ifPresent(cell -> files.put(cell, file));
            }
        }
        return files;
    }

    private static RegionHeader scanIsoRegions(Path folder, ScanOptions options, Map<ChunkPos, Path> isoFiles,
                                               List<ScanIssue> issues) throws IOException {
        if (!Files.isDirectory(folder)) {
            return null;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path file : stream) {
                IsoRegionChunkFile.parseName(file.getFileName().toString())
                        .filter(pos -> Files.isRegularFile(file))
                        .ifPresent(pos -> isoFiles.put(pos, file));
            }
        }
        if (options.readIsoRegionHeaders()) {
            for (Map.Entry<ChunkPos, Path> e : isoFiles.entrySet()) {
                Path file = e.getValue();
                try {
                    IsoRegionChunkFile.Header h = IsoRegionChunkFile.readHeader(file);
                    long size = Files.size(file);
                    if (!h.chunk().equals(e.getKey()) || h.declaredLength() != size) {
                        issues.add(new ScanIssue(ScanIssue.Type.ISO_FILE_HEADER_MISMATCH, file,
                                "header says " + h.chunk() + ", " + h.declaredLength() + " bytes; file is "
                                        + e.getKey() + ", " + size + " bytes"));
                    }
                } catch (IOException ex) {
                    issues.add(new ScanIssue(ScanIssue.Type.ISO_FILE_UNREADABLE, file, ex.getMessage()));
                }
            }
        }
        Path headerFile = folder.resolve(RegionHeader.FILE_NAME);
        if (!Files.isRegularFile(headerFile)) {
            return null;
        }
        RegionHeader header;
        try {
            header = RegionHeader.read(headerFile);
        } catch (IOException e) {
            issues.add(new ScanIssue(ScanIssue.Type.ISO_HEADER_UNREADABLE, headerFile, e.getMessage()));
            return null;
        }
        Set<ChunkPos> listed = new HashSet<>(header.chunks());
        for (ChunkPos pos : new TreeSet<>(listed)) {
            if (!isoFiles.containsKey(pos)) {
                issues.add(new ScanIssue(ScanIssue.Type.ISO_HEADER_ENTRY_WITHOUT_FILE, headerFile,
                        "lists " + pos + " but " + IsoRegionChunkFile.fileName(pos) + " is missing"));
            }
        }
        for (ChunkPos pos : new TreeSet<>(isoFiles.keySet())) {
            if (!listed.contains(pos)) {
                issues.add(new ScanIssue(ScanIssue.Type.ISO_FILE_NOT_IN_HEADER, isoFiles.get(pos),
                        "not listed in " + RegionHeader.FILE_NAME));
            }
        }
        return header;
    }
}
