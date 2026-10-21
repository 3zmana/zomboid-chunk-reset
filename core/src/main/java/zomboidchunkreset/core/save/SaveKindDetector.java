package zomboidchunkreset.core.save;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import zomboidchunkreset.core.db.PlayerRecord;

/**
 * Tells single-player and server saves apart from their content (the folder path is not used,
 * since saves are often copied elsewhere before editing).
 *
 * <p>Signals observed on the reference saves:
 * <ul>
 *   <li>server only: folder {@code map_visited_server/}, file {@code servermap_symbols.bin},
 *       rows in {@code networkPlayers};</li>
 *   <li>single-player only: files {@code map_visited.bin}, {@code map_symbols.bin},
 *       {@code map_ver.bin}, {@code mods.txt}, {@code thumb.png}, rows in {@code localPlayers}.</li>
 * </ul>
 */
public final class SaveKindDetector {

    private static final List<String> SERVER_PATHS = List.of("map_visited_server", "servermap_symbols.bin");
    private static final List<String> SINGLE_PLAYER_PATHS =
            List.of("map_visited.bin", "map_symbols.bin", "map_ver.bin", "mods.txt", "thumb.png");

    private SaveKindDetector() {
    }

    /** Detection result. */
    public record Result(SaveKind kind, List<String> signals) {
    }

    /** Detects the kind of the save in {@code root}, using already-read players. */
    public static Result detect(Path root, List<PlayerRecord> players) {
        List<String> server = new ArrayList<>();
        List<String> single = new ArrayList<>();
        for (String name : SERVER_PATHS) {
            if (Files.exists(root.resolve(name))) {
                server.add(name);
            }
        }
        for (String name : SINGLE_PLAYER_PATHS) {
            if (Files.exists(root.resolve(name))) {
                single.add(name);
            }
        }
        if (players.stream().anyMatch(p -> p.source() == PlayerRecord.Source.NETWORK)) {
            server.add("players.db:networkPlayers");
        }
        if (players.stream().anyMatch(p -> p.source() == PlayerRecord.Source.LOCAL)) {
            single.add("players.db:localPlayers");
        }
        List<String> signals = new ArrayList<>(server);
        signals.addAll(single);
        SaveKind kind;
        if (!server.isEmpty() && single.isEmpty()) {
            kind = SaveKind.MULTIPLAYER_SERVER;
        } else if (server.isEmpty() && !single.isEmpty()) {
            kind = SaveKind.SINGLE_PLAYER;
        } else {
            kind = SaveKind.UNKNOWN;
        }
        return new Result(kind, signals);
    }
}
