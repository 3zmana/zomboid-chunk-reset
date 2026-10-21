package zomboidchunkreset.app.tools;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.robot.Robot;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import zomboidchunkreset.app.i18n.I18n;
import zomboidchunkreset.app.map.MapView;
import zomboidchunkreset.app.settings.AppSettings;
import zomboidchunkreset.app.ui.MainWindow;
import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.reset.ResetMode;
import zomboidchunkreset.core.save.SaveIndex;
import zomboidchunkreset.core.save.SaveScanner;
import zomboidchunkreset.core.selection.ChunkSelection;

/**
 * Developer tool (not a unit test): opens the real window on a save and drives it like a user, with
 * the system mouse, then writes screenshots. Usage:
 * {@code UiScreenshotTool <save folder> <output folder> [x1 y1 x2 y2]} where the optional in-game
 * rectangle is the area to protect (default: around the first player).
 *
 * <p><b>It really deletes chunks</b> (everything outside the protected area) at the end of the
 * scenario: only run it on a copy of a save.
 */
public final class UiScreenshotTool {

    private static String[] arguments;
    private static volatile MainWindow window;
    private static Robot robotInstance;

    private UiScreenshotTool() {
    }

    public static void main(String[] args) {
        arguments = args;
        // Launched through a nested class: a main class extending Application needs the module path.
        Application.launch(ToolApp.class, args);
    }

    /** The application started by {@link #main}. */
    public static final class ToolApp extends Application {
        @Override
        public void start(Stage stage) {
            // Xvfb has no window manager: without decorations, JavaFX's screen coordinates match the pointer's.
            stage.initStyle(StageStyle.UNDECORATED);
            AppSettings settings = new AppSettings(Path.of(arguments[1]).resolve("settings"));
            open(stage, settings, MainWindow.State.EMPTY);
            Platform.runLater(window::showStartupWarning);
            Thread script = new Thread(() -> {
                try {
                    run(stage);
                    System.out.println("UI script finished");
                } catch (Throwable e) {
                    e.printStackTrace();
                    System.exit(1);
                }
                Platform.exit();
            }, "ui-script");
            script.setDaemon(true);
            script.start();
        }

        private static void open(Stage stage, AppSettings settings, MainWindow.State state) {
            window = new MainWindow(stage, settings, state, (code, current) -> {
                I18n.use(code, settings.languagesFolder());
                window.dispose();
                open(stage, settings, current);
            });
            window.show();
        }
    }

    private static void run(Stage stage) throws Exception {
        Path save = Path.of(arguments[0]);
        Path out = Files.createDirectories(Path.of(arguments[1]));

        // 1. Start-up warning.
        Window warning = waitForWindow(stage, w -> true);
        pause(300);
        shot(warning, out.resolve("00-startup-warning.png"));
        fx(warning::hide);
        pause(300);
        shot(stage, out.resolve("01-empty.png"));

        // 2. Open the save.
        fx(() -> window.openSave(save, false));
        waitFor(() -> window.index() != null && window.currentPlan() != null, 120);
        MainWindow w = window;
        System.out.printf("opened: %d chunks, kind %s%n", w.index().chunks().size(), w.index().kind());
        pause(1500); // the running-game check runs in the background
        String running = fxGet(() -> {
            Node n = stage.getScene().getRoot().lookup("#running-warning");
            return n != null && n.isVisible() ? ((Label) n).getText() : null;
        });
        System.out.println("running-game warning: " + running);
        shot(stage, out.resolve("02-overview.png"));

        // 3. Select the base with the real (virtual-screen) mouse.
        ChunkBounds base;
        if (arguments.length >= 6) {
            base = ChunkBounds.of(ChunkPos.ofSquare(Integer.parseInt(arguments[2]), Integer.parseInt(arguments[3])),
                    ChunkPos.ofSquare(Integer.parseInt(arguments[4]), Integer.parseInt(arguments[5])));
        } else {
            ChunkPos c = w.index().players().get(0).chunk();
            base = new ChunkBounds(c.x() - 5, c.y() - 4, c.x() + 6, c.y() + 4);
        }
        double cx = (base.minX() + base.maxX() + 1) / 2.0 * 8;
        double cy = (base.minY() + base.maxY() + 1) / 2.0 * 8;
        MapView map = w.map();
        fx(() -> map.centerOnSquare(cx, cy));
        fx(map::zoomOut);
        pause(300);
        Point2D from = fxGet(() -> map.toLocal(base.minX() + 0.3, base.minY() + 0.3));
        Point2D to = fxGet(() -> map.toLocal(base.maxX() + 0.7, base.maxY() + 0.7));
        moveTo(map, from);
        robot(r -> r.mousePress(MouseButton.PRIMARY));
        moveTo(map, from.midpoint(to));
        moveTo(map, to);
        robot(r -> r.mouseRelease(MouseButton.PRIMARY));
        int expected = base.width() * base.height();
        waitFor(() -> w.selection().selection().size() == expected, 10);
        System.out.printf("selected by mouse: %d%n", w.selection().selection().size());

        // Shift+click deselects one chunk; undo restores it.
        moveTo(map, fxGet(() -> map.toLocal(base.minX() + 0.5, base.minY() + 0.5)));
        robot(r -> r.keyPress(KeyCode.SHIFT));
        robot(r -> r.mouseClick(MouseButton.PRIMARY));
        robot(r -> r.keyRelease(KeyCode.SHIFT));
        int afterShift = fxGet(() -> w.selection().selection().size());
        fx(() -> w.selection().undo());
        System.out.printf("shift-click: %d -> %d, undo: %d%n", expected, afterShift,
                fxGet(() -> w.selection().selection().size()));

        fx(() -> w.setMode(ResetMode.KEEP_SELECTED));
        waitFor(() -> w.currentPlan() != null && w.currentPlan().mode() == ResetMode.KEEP_SELECTED, 30);
        pause(400);
        shot(stage, out.resolve("03-keep-base.png"));

        // 4. Export, clear, switch mode, import: same selection and mode again.
        Path file = out.resolve("selection.json");
        ChunkSelection before = fxGet(() -> w.selection().snapshot());
        fx(() -> w.exportSelection(file));
        fx(w::clearSelection);
        fx(() -> w.setMode(ResetMode.DELETE_SELECTED));
        fx(() -> w.importSelection(file));
        boolean same = fxGet(() -> w.selection().selection().equals(before));
        System.out.printf("export/import: same selection %s, mode %s%n", same,
                fxGet(() -> w.currentPlan() == null ? null : w.state().mode()));
        System.out.println(Files.readString(file).strip());
        if (!same) {
            throw new IllegalStateException("import did not restore the selection");
        }

        // 5. Delete for real (on this copy): confirmation dialog first.
        fx(() -> ((Button) stage.getScene().getRoot().lookup("#delete-button")).fire());
        Window confirm = waitForWindow(stage, win -> win.getScene().getRoot().lookup("#backup-check") != null);
        pause(300);
        shot(confirm, out.resolve("04-confirm.png"));
        Button deleteButton = fxGet(() -> (Button) confirm.getScene().getRoot().lookup("#confirm-delete"));
        boolean disabledBefore = fxGet(deleteButton::isDisabled);
        fx(() -> ((CheckBox) confirm.getScene().getRoot().lookup("#backup-check")).setSelected(true));
        boolean disabledAfter = fxGet(deleteButton::isDisabled);
        System.out.printf("delete button disabled before ticking: %s, after: %s%n", disabledBefore, disabledAfter);
        if (!disabledBefore || disabledAfter) {
            throw new IllegalStateException("the confirmation check box does not guard the Delete button");
        }
        fx(deleteButton::fire);
        String reportTitle = I18n.t("report.title.APPLY");
        Window report = waitForWindow(stage, win -> win instanceof Stage s && reportTitle.equals(s.getTitle()));
        pause(300);
        shot(report, out.resolve("05-report.png"));
        fx(report::hide);
        waitFor(() -> !w.busy() && w.index() != null && w.index().chunks().size() < before.size() + 1, 120);
        pause(800);
        shot(stage, out.resolve("06-after-delete.png"));
        SaveIndex after = SaveScanner.scan(save);
        System.out.printf("after deletion: %d chunks left, %d scan issues, selection still %d%n",
                after.chunks().size(), after.issues().size(), fxGet(() -> w.selection().selection().size()));
        if (!after.issues().isEmpty()) {
            throw new IllegalStateException("the save is inconsistent after deletion: " + after.issues());
        }

        // 6. Switch to French through the Language menu: the window is rebuilt, the state kept.
        fx(() -> {
            MenuBar bar = (MenuBar) ((BorderPane) stage.getScene().getRoot()).getTop();
            for (Menu m : bar.getMenus()) {
                for (MenuItem item : m.getItems()) {
                    if ("Français".equals(item.getText())) {
                        item.fire();
                    }
                }
            }
        });
        waitFor(() -> window != w && window.index() != null, 30);
        pause(800);
        System.out.printf("language %s, selection kept: %d, mode %s%n", I18n.code(),
                fxGet(() -> window.selection().selection().size()), fxGet(() -> window.state().mode()));
        shot(stage, out.resolve("07-french.png"));
        fx(() -> window.map().fit());
        pause(400);
        shot(stage, out.resolve("08-french-fit.png"));
    }

    // ------------------------------------------------------------------ helpers

    private static void robot(Consumer<Robot> action) throws Exception {
        fx(() -> {
            if (robotInstance == null) {
                robotInstance = new Robot();
            }
            action.accept(robotInstance);
        });
        pause(120);
    }

    private static void moveTo(MapView map, Point2D local) throws Exception {
        Point2D screen = fxGet(() -> map.localToScreen(local));
        robot(r -> r.mouseMove(screen.getX(), screen.getY()));
    }

    private static Window waitForWindow(Stage main, Predicate<Window> condition) throws Exception {
        AtomicReference<Window> found = new AtomicReference<>();
        waitFor(() -> {
            for (Window win : Window.getWindows()) {
                if (win != main && win.isShowing() && win.getScene() != null && condition.test(win)) {
                    found.set(win);
                    return true;
                }
            }
            return false;
        }, 60);
        return found.get();
    }

    private static void shot(Window win, Path file) throws Exception {
        WritableImage image = fxGet(() -> {
            Scene scene = win.getScene();
            return scene.snapshot(null);
        });
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                buffered.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        ImageIO.write(buffered, "png", file.toFile());
        System.out.println("wrote " + file.getFileName());
    }

    private static void pause(long millis) throws Exception {
        Thread.sleep(millis);
        fx(() -> { });
    }

    private static void waitFor(Supplier<Boolean> condition, int seconds) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < end) {
            if (fxGet(condition::get)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("timed out");
    }

    private static void fx(Runnable r) throws Exception {
        fxGet(() -> {
            r.run();
            return null;
        });
    }

    private static <T> T fxGet(Supplier<T> s) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(s.get());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(120, TimeUnit.SECONDS)) {
            throw new IllegalStateException("FX thread blocked");
        }
        if (error.get() != null) {
            throw new IllegalStateException(error.get());
        }
        return result.get();
    }
}
