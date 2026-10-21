package zomboidchunkreset.core.reset;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import zomboidchunkreset.core.db.VehicleRecord;
import zomboidchunkreset.core.db.VehiclesDatabase;
import zomboidchunkreset.core.format.CellFileType;
import zomboidchunkreset.core.format.RegionHeader;
import zomboidchunkreset.core.model.CellPos;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.save.ChunkEntry;
import zomboidchunkreset.core.save.SaveIndex;
import zomboidchunkreset.core.save.SaveScanner;

/**
 * Carries out a {@link ResetPlan}, or simulates it.
 *
 * <p>Both modes first run the same pre-checks (planned files still present, {@code RegionHeader.bin}
 * unchanged since the scan, files and folders writable). If any fails, nothing is changed.
 * {@link Mode#SIMULATE} stops there and reports what would be done.
 *
 * <p>{@link Mode#APPLY} works in an order that keeps the save consistent even if it is stopped
 * part-way (error or cancellation):
 * <ol>
 *   <li>take the write lock on {@code vehicles.db} (abort, with no change, if the game holds it);</li>
 *   <li>rewrite {@code RegionHeader.bin} atomically, then delete the {@code datachunk} files;</li>
 *   <li>delete the chunk files, then the {@code map/<x>/} folders left empty;</li>
 *   <li>delete the per-cell files only of the planned cells whose chunks are really all gone;</li>
 *   <li>delete the vehicle rows only of chunks that are really gone, and commit.</li>
 * </ol>
 * Steps 4 and 5 also run after a stop, so per-cell files and vehicles always match the chunks
 * actually deleted. {@code players.db} is never opened for writing.
 */
public final class ResetExecutor {

    /** Simulation or real run. */
    public enum Mode {
        /** Run the pre-checks and report what would be done; change nothing. */
        SIMULATE,
        /** Delete for real. */
        APPLY
    }

    private ResetExecutor() {
    }

    /** Runs or simulates the plan. Never throws for expected problems: see {@link ResetReport#outcome()}. */
    public static ResetReport run(ResetPlan plan, Mode mode, ResetProgress progress) {
        return new Run(plan, mode, progress).execute();
    }

    private static final class Run {
        private final ResetPlan plan;
        private final Mode mode;
        private final ResetProgress progress;
        private final SaveIndex index;
        private final Path root;
        private final List<String> log = new ArrayList<>();
        private final int total;
        private int done;

        private boolean changesMade;
        private int chunkFilesDeleted;
        private long chunkBytesDeleted;
        private int columnFoldersRemoved;
        private int cellFilesDeleted;
        private int isoFilesDeleted;
        private boolean regionHeaderRewritten;
        private int vehiclesDeleted;
        private int alreadyAbsent;

        Run(ResetPlan plan, Mode mode, ResetProgress progress) {
            this.plan = plan;
            this.mode = mode;
            this.progress = progress;
            this.index = plan.index();
            this.root = index.root();
            this.total = plan.filesToDelete() + (plan.newRegionHeader().isPresent() ? 1 : 0)
                    + (plan.vehiclesToDelete().isEmpty() ? 0 : 1);
        }

        ResetReport execute() {
            List<String> problems = preCheck();
            if (!problems.isEmpty()) {
                log.addAll(problems);
                return report(ResetReport.Outcome.ABORTED, String.join("\n", problems));
            }
            return mode == Mode.SIMULATE ? simulate() : apply();
        }

        // ---------------------------------------------------------------- pre-checks

        private List<String> preCheck() {
            List<String> problems = new ArrayList<>();
            if (!Files.isDirectory(root.resolve(SaveScanner.MAP_FOLDER))) {
                problems.add("The save folder is gone: " + root);
                return problems;
            }
            List<Path> files = allFilesToDelete();
            long missing = files.stream().filter(p -> !Files.exists(p)).count();
            if (missing > 0) {
                problems.add(missing + " planned file(s) no longer exist: the save changed since it was scanned."
                        + " Scan it again.");
            }
            Path headerFile = root.resolve(SaveScanner.ISO_REGION_FOLDER).resolve(RegionHeader.FILE_NAME);
            if (plan.newRegionHeader().isPresent()) {
                try {
                    if (!RegionHeader.read(headerFile).equals(plan.originalRegionHeader().orElseThrow())) {
                        problems.add(RegionHeader.FILE_NAME + " changed since the scan. Scan the save again.");
                    }
                } catch (IOException e) {
                    problems.add("Cannot read " + headerFile + ": " + e.getMessage());
                }
                if (!Files.isWritable(headerFile.getParent())) {
                    problems.add("Folder not writable: " + headerFile.getParent());
                }
            }
            Path vehiclesDb = root.resolve(VehiclesDatabase.FILE_NAME);
            if (!plan.vehiclesToDelete().isEmpty() && !Files.isWritable(vehiclesDb)) {
                problems.add("Not writable: " + vehiclesDb);
            }
            Set<Path> folders = new LinkedHashSet<>();
            for (Path p : files) {
                folders.add(p.getParent());
            }
            for (Path folder : folders) {
                if (Files.isDirectory(folder) && !Files.isWritable(folder)) {
                    problems.add("Folder not writable: " + folder);
                }
            }
            problems.addAll(readOnlyFiles(files));
            return problems;
        }

        /** On file systems with DOS attributes (Windows), a read-only file cannot be deleted. */
        private List<String> readOnlyFiles(List<Path> files) {
            List<String> problems = new ArrayList<>();
            try {
                if (!Files.getFileStore(root).supportsFileAttributeView(DosFileAttributeView.class)) {
                    return problems;
                }
            } catch (IOException e) {
                return problems;
            }
            long readOnly = 0;
            Path example = null;
            for (Path p : files) {
                try {
                    if (Files.readAttributes(p, DosFileAttributes.class).isReadOnly()) {
                        readOnly++;
                        example = example == null ? p : example;
                    }
                } catch (IOException | UnsupportedOperationException e) {
                    // missing files are reported separately
                }
            }
            if (readOnly > 0) {
                problems.add(readOnly + " file(s) are read-only, for example " + example);
            }
            return problems;
        }

        private List<Path> allFilesToDelete() {
            List<Path> files = new ArrayList<>(plan.filesToDelete());
            for (ChunkEntry e : plan.chunksToDelete()) {
                files.add(chunkPath(e.pos()));
            }
            for (CellFileType type : CellFileType.values()) {
                files.addAll(plan.cellFilesToDelete(type).values());
            }
            files.addAll(plan.isoFilesToDelete().values());
            return files;
        }

        private Path chunkPath(ChunkPos pos) {
            return root.resolve(SaveScanner.MAP_FOLDER).resolve(Integer.toString(pos.x())).resolve(pos.y() + ".bin");
        }

        // ---------------------------------------------------------------- simulation

        private ResetReport simulate() {
            chunkFilesDeleted = plan.chunksToDelete().size();
            chunkBytesDeleted = plan.chunkBytesToDelete();
            cellFilesDeleted = plan.cellFileCount();
            isoFilesDeleted = plan.isoFilesToDelete().size();
            regionHeaderRewritten = plan.newRegionHeader().isPresent();
            vehiclesDeleted = plan.vehiclesToDelete().size();
            columnFoldersRemoved = countColumnsThatWouldBeEmptied();
            progress.update(total, total);
            log.add("Simulation only: nothing was changed.");
            return report(ResetReport.Outcome.COMPLETED, null);
        }

        private int countColumnsThatWouldBeEmptied() {
            Set<Path> deleted = new HashSet<>();
            Set<Path> columns = new TreeSet<>();
            for (ChunkEntry e : plan.chunksToDelete()) {
                Path p = chunkPath(e.pos());
                deleted.add(p);
                columns.add(p.getParent());
            }
            int count = 0;
            for (Path column : columns) {
                boolean empty = true;
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(column)) {
                    for (Path entry : stream) {
                        if (!deleted.contains(entry)) {
                            empty = false;
                            break;
                        }
                    }
                } catch (IOException e) {
                    empty = false;
                }
                if (empty) {
                    count++;
                }
            }
            return count;
        }

        // ---------------------------------------------------------------- real run

        private ResetReport apply() {
            VehiclesDatabase.Deletion vehicles = null;
            if (!plan.vehiclesToDelete().isEmpty()) {
                try {
                    vehicles = VehiclesDatabase.Deletion.begin(root.resolve(VehiclesDatabase.FILE_NAME));
                } catch (IOException e) {
                    String msg = "vehicles.db is locked or cannot be written (is the game or the server running?): "
                            + e.getMessage();
                    log.add(msg);
                    return report(ResetReport.Outcome.ABORTED, msg);
                }
            }

            ResetReport.Outcome outcome = ResetReport.Outcome.COMPLETED;
            String message = null;
            Set<ChunkPos> goneChunks = new HashSet<>();
            try {
                deleteIsoRegions();
                deleteChunks(goneChunks);
            } catch (InterruptedIOException e) {
                outcome = ResetReport.Outcome.CANCELLED;
                message = "Cancelled.";
                log.add(message);
            } catch (IOException e) {
                outcome = ResetReport.Outcome.STOPPED;
                message = "Stopped by an error: " + e.getMessage();
                log.add(message);
            }
            removeEmptyColumns();

            // Consistency steps: always run, even after a stop. After a stop they only follow the
            // chunks actually deleted (cells or vehicles without any chunk file are left alone).
            boolean completed = outcome == ResetReport.Outcome.COMPLETED;
            try {
                deleteCellFiles(goneChunks, completed);
            } catch (IOException e) {
                outcome = ResetReport.Outcome.STOPPED;
                message = (message == null ? "" : message + "\n") + "Stopped by an error: " + e.getMessage();
                log.add("Stopped by an error: " + e.getMessage());
            }
            if (vehicles != null) {
                List<VehicleRecord> rows = new ArrayList<>();
                for (VehicleRecord r : plan.vehiclesToDelete()) {
                    // Only vehicles whose chunk is really gone (or, after a full run, never had a file).
                    if (goneChunks.contains(r.storedChunk()) || (completed && !index.hasChunk(r.storedChunk()))) {
                        rows.add(r);
                    }
                }
                String error = null;
                try {
                    int deleted = vehicles.delete(rows);
                    vehicles.commit();
                    vehiclesDeleted = deleted;
                    changesMade |= deleted > 0;
                    log.add("Deleted " + deleted + " vehicle row(s) from vehicles.db.");
                    tick();
                } catch (IOException e) {
                    error = "Vehicles not deleted: " + e.getMessage();
                } finally {
                    try {
                        vehicles.close();
                    } catch (IOException e) {
                        log.add("Warning: " + e.getMessage());
                    }
                }
                if (error != null) {
                    outcome = ResetReport.Outcome.STOPPED;
                    message = message == null ? error : message + "\n" + error;
                    log.add(error);
                }
            }
            if (alreadyAbsent > 0) {
                log.add(alreadyAbsent + " planned file(s) were already absent.");
            }
            if (outcome == ResetReport.Outcome.STOPPED && !changesMade) {
                outcome = ResetReport.Outcome.ABORTED;
            }
            if (outcome == ResetReport.Outcome.COMPLETED) {
                log.add("Reset completed.");
            }
            return report(outcome, message);
        }

        private void deleteIsoRegions() throws IOException {
            if (plan.newRegionHeader().isPresent()) {
                Path headerFile = root.resolve(SaveScanner.ISO_REGION_FOLDER).resolve(RegionHeader.FILE_NAME);
                checkCancelled();
                AtomicFiles.write(headerFile, plan.newRegionHeader().get().toBytes());
                changesMade = true;
                regionHeaderRewritten = true;
                log.add("Rewrote " + RegionHeader.FILE_NAME + ": " + plan.originalRegionHeader().orElseThrow().chunks().size()
                        + " -> " + plan.newRegionHeader().get().chunks().size() + " entries.");
                tick();
            }
            for (Path file : plan.isoFilesToDelete().values()) {
                checkCancelled();
                delete(file);
                isoFilesDeleted++;
                tick();
            }
            if (isoFilesDeleted > 0) {
                log.add("Deleted " + isoFilesDeleted + " iso-region file(s).");
            }
        }

        private void deleteChunks(Set<ChunkPos> gone) throws IOException {
            try {
                for (ChunkEntry e : plan.chunksToDelete()) {
                    checkCancelled();
                    delete(chunkPath(e.pos()));
                    gone.add(e.pos());
                    chunkFilesDeleted++;
                    chunkBytesDeleted += e.fileSize();
                    tick();
                }
            } finally {
                log.add("Deleted " + chunkFilesDeleted + " chunk file(s) (" + chunkBytesDeleted + " bytes).");
            }
        }

        private void removeEmptyColumns() {
            Set<Path> columns = new TreeSet<>();
            for (ChunkEntry e : plan.chunksToDelete()) {
                columns.add(chunkPath(e.pos()).getParent());
            }
            for (Path column : columns) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(column)) {
                    if (stream.iterator().hasNext()) {
                        continue;
                    }
                } catch (IOException e) {
                    continue;
                }
                try {
                    Files.delete(column);
                    columnFoldersRemoved++;
                } catch (DirectoryNotEmptyException | NoSuchFileException e) {
                    // left as is
                } catch (IOException e) {
                    log.add("Could not remove empty folder " + column + ": " + e.getMessage());
                }
            }
            if (columnFoldersRemoved > 0) {
                log.add("Removed " + columnFoldersRemoved + " empty map folder(s).");
            }
        }

        private void deleteCellFiles(Set<ChunkPos> goneChunks, boolean completed) throws IOException {
            // A planned cell is only reset if every chunk file it had is really gone.
            Set<CellPos> safeCells = new HashSet<>(plan.cellsToReset());
            Set<CellPos> cellsWithChunks = new HashSet<>();
            for (ChunkPos pos : index.chunks().keySet()) {
                cellsWithChunks.add(pos.cell());
                if (!goneChunks.contains(pos)) {
                    safeCells.remove(pos.cell());
                }
            }
            if (!completed) {
                // After a stop, leave cells that never had chunks for a later run.
                safeCells.retainAll(cellsWithChunks);
            }
            for (CellFileType type : CellFileType.values()) {
                for (Map.Entry<CellPos, Path> e : plan.cellFilesToDelete(type).entrySet()) {
                    if (safeCells.contains(e.getKey())) {
                        delete(e.getValue());
                        cellFilesDeleted++;
                        tick();
                    }
                }
            }
            if (cellFilesDeleted > 0) {
                log.add("Deleted " + cellFilesDeleted + " per-cell file(s) for " + safeCells.size() + " cell(s).");
            }
        }

        private void delete(Path file) throws IOException {
            try {
                Files.delete(file);
                changesMade = true;
            } catch (NoSuchFileException e) {
                alreadyAbsent++;
            }
        }

        private static void checkCancelled() throws InterruptedIOException {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("cancelled");
            }
        }

        private void tick() {
            progress.update(++done, total);
        }

        private ResetReport report(ResetReport.Outcome outcome, String message) {
            return new ResetReport(mode, outcome, mode == Mode.APPLY && changesMade, message, chunkFilesDeleted,
                    chunkBytesDeleted, columnFoldersRemoved, cellFilesDeleted, isoFilesDeleted, regionHeaderRewritten,
                    vehiclesDeleted, log);
        }
    }
}
