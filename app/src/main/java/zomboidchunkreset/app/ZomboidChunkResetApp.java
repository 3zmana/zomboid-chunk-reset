package zomboidchunkreset.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import zomboidchunkreset.app.i18n.I18n;
import zomboidchunkreset.app.settings.AppSettings;
import zomboidchunkreset.app.ui.MainWindow;

/** The JavaFX application. */
public final class ZomboidChunkResetApp extends Application {

    /** Name shown in titles. */
    public static final String NAME = "ZomboidChunkReset";

    private AppSettings settings;
    private MainWindow window;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        Links.init(getHostServices());
        for (int size : new int[] {16, 32, 48, 64, 128, 256}) {
            var icon = ZomboidChunkResetApp.class.getResource("/icons/icon-" + size + ".png");
            if (icon != null) {
                stage.getIcons().add(new Image(icon.toExternalForm()));
            }
        }
        settings = AppSettings.load();
        I18n.use(settings.language(), settings.languagesFolder());
        open(stage, MainWindow.State.empty());
        Platform.runLater(window::showStartupWarning);
    }

    /** Builds the window; called again (keeping the state) when the language changes. */
    private void open(Stage stage, MainWindow.State state) {
        window = new MainWindow(stage, settings, state, (code, current) -> {
            settings.setLanguage(code);
            settings.save();
            I18n.use(code, settings.languagesFolder());
            window.dispose();
            open(stage, current);
        });
        window.show();
    }

    @Override
    public void stop() {
        if (window != null) {
            window.dispose();
        }
        if (settings != null) {
            settings.save();
        }
    }
}
