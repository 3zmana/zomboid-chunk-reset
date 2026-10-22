package zomboidchunkreset.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

import zomboidchunkreset.core.terrain.TileClass;
import zomboidchunkreset.core.terrain.TileTable;
import zomboidchunkreset.core.terrain.TileTableBuilder;

/**
 * Development tool: builds the bundled tile table from the {@code media} folder of a Project Zomboid
 * installation. Run with {@code ./gradlew :core:generateTileTable -PpzMedia=<game>/media}.
 * Only the derived table (numbers) is written; game files are never copied.
 */
public final class TileTableGenerator {

    private TileTableGenerator() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: TileTableGenerator <game media folder> <output file>");
            System.exit(2);
        }
        Path media = Path.of(args[0]);
        Path output = Path.of(args[1]);
        TileTable table = TileTableBuilder.build(media);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.write(output, table.toBytes());

        Map<TileClass, Integer> counts = new EnumMap<>(TileClass.class);
        for (int id = 0; id < (1 << 24); id++) {
            TileClass c = table.classOf(id);
            if (c != TileClass.UNKNOWN) {
                counts.merge(c, 1, Integer::sum);
            }
        }
        System.out.println("Tile table written to " + output + " (" + Files.size(output) + " bytes, "
                + table.size() + " tiles)");
        counts.forEach((c, n) -> System.out.println("  " + c + ": " + n));
    }
}
