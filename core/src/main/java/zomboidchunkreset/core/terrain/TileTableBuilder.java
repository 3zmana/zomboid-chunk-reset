package zomboidchunkreset.core.terrain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@link TileTable} from the vanilla game's {@code .tiles} files. Development use only:
 * the result is committed as a resource, so users never need game files.
 *
 * <p>Sprite id of tile {@code i} of the tilesheet numbered {@code n} in a definition file, as
 * established on real Build 42 saves (every sprite id of the reference saves resolves, see
 * {@code docs/SAVE_FORMAT.md}):
 * <pre>
 * newtiledefinitions.tiles        (n - 1) * 1024 + i
 * tiledefinitions_erosion.tiles   1048576 + (n - 1) * 512 + i
 * jumbo_trees_big.tiles           2097152 + (n - 1) * 512 + i
 * jumbo_trees.tiles               2621440 + (n - 1) * 512 + i
 * </pre>
 * {@code tiledefinitions_noiseworks.patch.tiles} holds partial property lists for tilesheets of
 * {@code newtiledefinitions.tiles} with the same name; they are merged into the original properties
 * (replacing them instead would erase floor materials the saves show are in use). On the vanilla
 * files, merging or ignoring the patch gives the same table. Other files ({@code tiledefinitions_overlays},
 * {@code tiledefinitions_b42chunkcaching}) never appeared in the reference saves, so their ids are not
 * established and they are left out.
 */
public final class TileTableBuilder {

    /** A definition file with its id range. */
    public record Source(String fileName, int baseId, int pageSize, boolean treeFile) {
    }

    /** Vanilla Build 42 definition files used for the table. */
    public static final List<Source> SOURCES = List.of(
            new Source("newtiledefinitions.tiles", 0, 1024, false),
            new Source("tiledefinitions_erosion.tiles", 1_048_576, 512, true),
            new Source("jumbo_trees_big.tiles", 2_097_152, 512, true),
            new Source("jumbo_trees.tiles", 2_621_440, 512, true));

    /** Patch file applied to {@code newtiledefinitions.tiles}. */
    public static final String PATCH_FILE = "tiledefinitions_noiseworks.patch.tiles";

    private TileTableBuilder() {
    }

    /**
     * Builds the table from a folder containing the files listed in {@link #SOURCES} (the game's
     * {@code media} folder). The patch file is applied when present.
     */
    public static TileTable build(Path mediaFolder) throws IOException {
        Map<String, List<TileDefinitions.Sheet>> files = new LinkedHashMap<>();
        for (Source s : SOURCES) {
            Path file = mediaFolder.resolve(s.fileName());
            if (!Files.isRegularFile(file)) {
                throw new IOException("missing " + s.fileName() + " in " + mediaFolder);
            }
            files.put(s.fileName(), TileDefinitions.read(file));
        }
        Path patch = mediaFolder.resolve(PATCH_FILE);
        List<TileDefinitions.Sheet> patchSheets = Files.isRegularFile(patch) ? TileDefinitions.read(patch) : List.of();
        return build(files, patchSheets);
    }

    /** Builds the table from already parsed files (keyed by the file names of {@link #SOURCES}). */
    public static TileTable build(Map<String, List<TileDefinitions.Sheet>> files, List<TileDefinitions.Sheet> patch) {
        Map<String, TileDefinitions.Sheet> patchByName = new HashMap<>();
        for (TileDefinitions.Sheet p : patch) {
            patchByName.put(p.name(), p);
        }
        Map<Integer, TileClass> classes = new HashMap<>();
        for (Source source : SOURCES) {
            List<TileDefinitions.Sheet> sheets = files.get(source.fileName());
            if (sheets == null) {
                throw new IllegalArgumentException("missing " + source.fileName());
            }
            for (TileDefinitions.Sheet sheet : sheets) {
                List<Map<String, String>> tiles = sheet.tiles();
                if (source.baseId() == 0 && patchByName.containsKey(sheet.name())) {
                    tiles = applyPatch(tiles, patchByName.get(sheet.name()).tiles());
                }
                if (sheet.number() < 1 || tiles.size() > source.pageSize()) {
                    throw new IllegalArgumentException("tilesheet " + sheet.name() + " does not fit the id scheme");
                }
                boolean anyTree = tiles.stream().anyMatch(t -> t.containsKey("tree"));
                boolean treeSheet = TileClassifier.isTreeSheet(sheet.name(), anyTree, source.treeFile());
                int first = source.baseId() + (sheet.number() - 1) * source.pageSize();
                for (int i = 0; i < tiles.size(); i++) {
                    TileClass c = TileClassifier.classify(sheet.name(), tiles.get(i), treeSheet);
                    TileClass previous = classes.put(first + i, c);
                    if (previous != null) {
                        throw new IllegalArgumentException("duplicate sprite id " + (first + i));
                    }
                }
            }
        }
        return TileTable.of(classes);
    }

    /** Patched properties are merged into the tile with the same index (patch values win). */
    private static List<Map<String, String>> applyPatch(List<Map<String, String>> tiles,
                                                        List<Map<String, String>> patched) {
        List<Map<String, String>> result = new ArrayList<>(tiles);
        for (int i = 0; i < patched.size() && i < result.size(); i++) {
            Map<String, String> merged = new LinkedHashMap<>(result.get(i));
            merged.putAll(patched.get(i));
            result.set(i, merged);
        }
        return result;
    }
}
