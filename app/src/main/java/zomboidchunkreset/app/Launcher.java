package zomboidchunkreset.app;

/**
 * Entry point. Kept separate from the {@link javafx.application.Application} subclass so that the
 * program starts with JavaFX on the class path (no module path needed).
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        ZomboidChunkResetApp.main(args);
    }
}
