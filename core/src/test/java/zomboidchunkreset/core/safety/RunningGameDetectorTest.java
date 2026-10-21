package zomboidchunkreset.core.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import zomboidchunkreset.core.safety.RunningGameDetector.ProcessInfo;

class RunningGameDetectorTest {

    @TempDir
    Path dir;

    private static boolean matches(String command, String... args) {
        return RunningGameDetector.looksLikeZomboid(new ProcessInfo(1, command, List.of(args)));
    }

    @Test
    void recognisesTheGameAndTheServer() {
        // Windows client and server (no arguments available on Windows)
        assertTrue(matches("C:\\Program Files (x86)\\Steam\\steamapps\\common\\ProjectZomboid\\ProjectZomboid64.exe"));
        assertTrue(matches("D:\\Games\\Project Zomboid Dedicated Server\\jre64\\bin\\java.exe"));
        assertTrue(matches("D:\\Games\\ProjectZomboid\\jre64\\bin\\javaw.exe"));
        // Linux
        assertTrue(matches("/home/user/.steam/steam/steamapps/common/ProjectZomboid/projectzomboid/ProjectZomboid64"));
        assertTrue(matches("/opt/pzserver/jre64/bin/java", "-Xmx8g", "zombie.network.GameServer"));
        assertTrue(matches("/usr/bin/java", "-Dzomboid.steam=1", "-cp", "x"));
    }

    @Test
    void ignoresOtherProgramsAndThisOne() {
        assertFalse(matches("C:\\Windows\\explorer.exe"));
        assertFalse(matches("/usr/lib/jvm/java-21/bin/java", "-jar", "something.jar"));
        assertFalse(matches("C:\\Tools\\ZomboidChunkReset\\ZomboidChunkReset.exe"));
        assertFalse(matches("C:\\Tools\\ZomboidChunkReset\\runtime\\bin\\java.exe"));
        assertFalse(matches("/home/user/zomboid-chunk-reset/build/jdk/bin/java", "zomboidchunkreset.app.Launcher"));
        assertFalse(matches("", "notes about zomboid"));
        assertFalse(matches(null));
    }

    @Test
    void recentWrites() throws IOException {
        Path f = Files.write(dir.resolve("map_t.bin"), new byte[1]);
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        Files.setLastModifiedTime(f, FileTime.from(now.minusSeconds(30)));
        assertEquals(Optional.of(now.minusSeconds(30)), RunningGameDetector.recentlyWritten(dir, Duration.ofMinutes(2), now));
        Files.setLastModifiedTime(f, FileTime.from(now.minusSeconds(600)));
        assertEquals(Optional.empty(), RunningGameDetector.recentlyWritten(dir, Duration.ofMinutes(2), now));
        assertEquals(Optional.empty(), RunningGameDetector.recentlyWritten(dir.resolve("missing"),
                Duration.ofMinutes(2), now));
    }

    /** Starts a harmless process named like the game and checks that it is found. */
    @Test
    void findsARealProcessNamedLikeTheGame() throws Exception {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        Path source;
        List<String> args;
        Path exe;
        if (os.contains("win")) {
            source = Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"), "System32", "PING.EXE");
            exe = dir.resolve("ProjectZomboid64.exe");
            args = List.of("-n", "30", "127.0.0.1");
        } else {
            source = Path.of("/bin/sleep");
            exe = dir.resolve("ProjectZomboid64");
            args = List.of("30");
        }
        Assumptions.assumeTrue(Files.isExecutable(source), "no harmless program to copy");
        Files.copy(source, exe, StandardCopyOption.COPY_ATTRIBUTES);
        exe.toFile().setExecutable(true);
        List<String> command = new java.util.ArrayList<>(List.of(exe.toString()));
        command.addAll(args);
        Process p;
        try {
            p = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException e) {
            Assumptions.abort("cannot start a copied program here: " + e.getMessage());
            return;
        }
        Thread.sleep(300);
        Assumptions.assumeTrue(p.isAlive(), "the copied program did not keep running");
        try {
            assertFalse(RunningGameDetector.findGameProcesses().stream().noneMatch(i -> i.pid() == p.pid()),
                    "the fake game process was not detected");
        } finally {
            p.destroyForcibly().waitFor();
        }
        assertTrue(RunningGameDetector.findGameProcesses().stream().noneMatch(i -> i.pid() == p.pid()));
    }
}
