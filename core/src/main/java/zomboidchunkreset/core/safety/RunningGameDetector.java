package zomboidchunkreset.core.safety;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Looks for signs that Project Zomboid (game or dedicated server) is running. A running game keeps
 * writing its save, so editing it at the same time could corrupt it. These are heuristics: they can
 * miss a game started differently, or a server on another machine.
 *
 * <ul>
 *   <li>a process whose executable name starts with {@code ProjectZomboid} (game client, and the
 *       server on some installs);</li>
 *   <li>a Java process whose executable lives in a folder whose path contains {@code zomboid}
 *       (the bundled {@code jre64} used by the dedicated server scripts);</li>
 *   <li>a process whose arguments mention the game's main classes ({@code zombie.network.GameServer},
 *       {@code zombie.gameStates...}) or {@code -Dzomboid.} options (arguments are not available on
 *       Windows, hence the rules above);</li>
 *   <li>a save written very recently.</li>
 * </ul>
 * Process names are to be confirmed on real installations; this program's own process is ignored.
 */
public final class RunningGameDetector {

    /**
     * Save files the game rewrites regularly while running. Files this program writes itself
     * ({@code vehicles.db}, {@code RegionHeader.bin}) are left out, so that a deletion does not look
     * like the game at work.
     */
    private static final List<String> SAVE_FILES = List.of("map_t.bin", "map_meta.bin", "players.db",
            "map_zone.bin", "reanimated.bin");

    private RunningGameDetector() {
    }

    /** What is known about a running process. */
    public record ProcessInfo(long pid, String command, List<String> arguments) {
    }

    /** Whether a process looks like Project Zomboid. */
    public static boolean looksLikeZomboid(ProcessInfo p) {
        String command = p.command() == null ? "" : p.command().replace('\\', '/');
        String lower = command.toLowerCase(Locale.ROOT);
        String name = lower.substring(lower.lastIndexOf('/') + 1);
        if (name.contains("chunkreset")) {
            return false;
        }
        if (name.startsWith("projectzomboid")) {
            return true;
        }
        boolean java = name.equals("java") || name.equals("java.exe") || name.equals("javaw")
                || name.equals("javaw.exe");
        if (java && lower.substring(0, lower.length() - name.length()).contains("zomboid")
                && !lower.contains("chunkreset") && !lower.contains("chunk-reset")) {
            return true;
        }
        for (String arg : p.arguments()) {
            if (arg.contains("zombie.network.GameServer") || arg.startsWith("zombie.gameStates")
                    || arg.startsWith("-Dzomboid.")) {
                return true;
            }
        }
        return false;
    }

    /** Running processes that look like Project Zomboid (excluding this program). */
    public static List<ProcessInfo> findGameProcesses() {
        long self = ProcessHandle.current().pid();
        List<ProcessInfo> found = new ArrayList<>();
        ProcessHandle.allProcesses().forEach(h -> {
            if (h.pid() == self) {
                return;
            }
            try {
                ProcessHandle.Info info = h.info();
                ProcessInfo p = new ProcessInfo(h.pid(), info.command().orElse(""),
                        info.arguments().map(List::of).orElse(List.of()));
                if (looksLikeZomboid(p)) {
                    found.add(p);
                }
            } catch (RuntimeException e) {
                // process vanished or not accessible
            }
        });
        return found;
    }

    /** The most recent modification of the files the game rewrites, if within {@code window} of now. */
    public static Optional<Instant> recentlyWritten(Path saveRoot, Duration window, Instant now) {
        Instant latest = null;
        for (String name : SAVE_FILES) {
            Path f = saveRoot.resolve(name);
            try {
                if (Files.isRegularFile(f)) {
                    FileTime t = Files.getLastModifiedTime(f);
                    if (latest == null || t.toInstant().isAfter(latest)) {
                        latest = t.toInstant();
                    }
                }
            } catch (IOException e) {
                // ignore unreadable files
            }
        }
        if (latest != null && !latest.isBefore(now.minus(window)) && !latest.isAfter(now.plus(window))) {
            return Optional.of(latest);
        }
        return Optional.empty();
    }

    /** Result of {@link #check}. */
    public record Result(List<ProcessInfo> processes, Optional<Instant> recentWrite) {
        /** Whether there is any sign of a running game. */
        public boolean suspicious() {
            return !processes.isEmpty() || recentWrite.isPresent();
        }
    }

    /** Checks processes, and recent writes to the given save (if any) within the last two minutes. */
    public static Result check(Path saveRoot) {
        Optional<Instant> recent = saveRoot == null ? Optional.empty()
                : recentlyWritten(saveRoot, Duration.ofMinutes(2), Instant.now());
        return new Result(findGameProcesses(), recent);
    }
}
