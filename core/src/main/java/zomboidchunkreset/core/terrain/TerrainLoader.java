package zomboidchunkreset.core.terrain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import zomboidchunkreset.core.model.ChunkPos;

/**
 * Reads the top view of many chunks in parallel, for the map. Read-only: chunk files are opened for
 * reading only and never changed.
 */
public final class TerrainLoader {

    /** Receives each chunk as soon as it is read (called from worker threads). */
    @FunctionalInterface
    public interface Listener {
        /** @param terrain the chunk's top view, or {@code null} if nothing reliable could be read */
        void loaded(ChunkPos pos, ChunkTerrain terrain);
    }

    /**
     * Remembers chunks already read, so that scanning a save again (after a deletion, or when the
     * window is rebuilt) only reads the chunk files that changed. Holds one save at a time.
     */
    public static final class Cache {

        private record Entry(long size, long modified, ChunkTerrain terrain) {
        }

        private final ConcurrentHashMap<ChunkPos, Entry> entries = new ConcurrentHashMap<>();
        private Path root;

        synchronized void useRoot(Path saveRoot) {
            Path normalized = saveRoot.toAbsolutePath().normalize();
            if (!normalized.equals(root)) {
                entries.clear();
                root = normalized;
            }
        }

        Optional<ChunkTerrain> get(ChunkPos pos, long size, long modified, boolean[] hit) {
            Entry e = entries.get(pos);
            if (e != null && e.size() == size && e.modified() == modified) {
                hit[0] = true;
                return Optional.ofNullable(e.terrain());
            }
            hit[0] = false;
            return Optional.empty();
        }

        void put(ChunkPos pos, long size, long modified, ChunkTerrain terrain) {
            entries.put(pos, new Entry(size, modified, terrain));
        }

        /** Number of remembered chunks. */
        public int size() {
            return entries.size();
        }
    }

    private TerrainLoader() {
    }

    /** Path of a chunk file in a save. */
    public static Path chunkFile(Path saveRoot, ChunkPos pos) {
        return saveRoot.resolve("map").resolve(Integer.toString(pos.x())).resolve(pos.y() + ".bin");
    }

    /**
     * Reads the given chunks of a save.
     *
     * @param threads   number of worker threads (at least 1)
     * @param cancelled polled regularly; loading stops early when it returns {@code true}
     * @return the number of chunks reported to the listener
     */
    public static int load(Path saveRoot, Collection<ChunkPos> chunks, TileTable table, Cache cache, int threads,
                           Listener listener, BooleanSupplier cancelled) throws InterruptedException {
        cache.useRoot(saveRoot);
        List<ChunkPos> positions = new ArrayList<>(chunks);
        int workers = Math.max(1, Math.min(threads, positions.size()));
        if (positions.isEmpty()) {
            return 0;
        }
        TerrainReader reader = new TerrainReader(table);
        ExecutorService pool = Executors.newFixedThreadPool(workers, r -> {
            Thread t = new Thread(r, "zcr-terrain");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
        AtomicInteger next = new AtomicInteger();
        AtomicInteger reported = new AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int w = 0; w < workers; w++) {
                futures.add(pool.submit(() -> {
                    boolean[] hit = new boolean[1];
                    for (int i = next.getAndIncrement(); i < positions.size(); i = next.getAndIncrement()) {
                        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        ChunkPos pos = positions.get(i);
                        listener.loaded(pos, readOne(saveRoot, pos, reader, cache, hit));
                        reported.incrementAndGet();
                    }
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    throw new IllegalStateException("terrain loading failed", e.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
        return reported.get();
    }

    private static ChunkTerrain readOne(Path saveRoot, ChunkPos pos, TerrainReader reader, Cache cache, boolean[] hit) {
        Path file = chunkFile(saveRoot, pos);
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            long size = attrs.size();
            long modified = attrs.lastModifiedTime().toMillis();
            Optional<ChunkTerrain> cached = cache.get(pos, size, modified, hit);
            if (hit[0]) {
                return cached.orElse(null);
            }
            ChunkTerrain terrain = reader.read(Files.readAllBytes(file)).orElse(null);
            cache.put(pos, size, modified, terrain);
            return terrain;
        } catch (IOException | RuntimeException e) {
            // Unreadable chunk (deleted meanwhile, locked by the game...): the map keeps its plain colour.
            return null;
        }
    }
}
