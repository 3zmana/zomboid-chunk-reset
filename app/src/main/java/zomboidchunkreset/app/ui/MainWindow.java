package zomboidchunkreset.app.ui;

import static zomboidchunkreset.app.i18n.I18n.number;
import static zomboidchunkreset.app.i18n.I18n.t;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import zomboidchunkreset.app.ZomboidChunkResetApp;
import zomboidchunkreset.app.i18n.I18n;
import zomboidchunkreset.app.map.MapModel;
import zomboidchunkreset.app.map.MapView;
import zomboidchunkreset.app.model.SelectionState;
import zomboidchunkreset.app.settings.AppSettings;
import zomboidchunkreset.core.db.PlayerRecord;
import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.reset.ResetExecutor;
import zomboidchunkreset.core.reset.ResetMode;
import zomboidchunkreset.core.reset.ResetOptions;
import zomboidchunkreset.core.reset.ResetPlan;
import zomboidchunkreset.core.reset.ResetPlanner;
import zomboidchunkreset.core.reset.ResetProgress;
import zomboidchunkreset.core.reset.ResetReport;
import zomboidchunkreset.core.safety.RunningGameDetector;
import zomboidchunkreset.core.save.ChunkEntry;
import zomboidchunkreset.core.save.NotASaveException;
import zomboidchunkreset.core.save.SaveIndex;
import zomboidchunkreset.core.save.SaveScanner;
import zomboidchunkreset.core.save.ScanOptions;
import zomboidchunkreset.core.selection.ChunkSelection;
import zomboidchunkreset.core.selection.SelectionFile;

/** The main window: menus, map, side panel and status bar. */
public final class MainWindow implements SidePanel.Actions {

    /** What survives rebuilding the window (for example after a language change). */
    public record State(SaveIndex index, ChunkSelection selection, ResetMode mode, ResetOptions options) {
        public static final State EMPTY = new State(null, new ChunkSelection(), ResetMode.DELETE_SELECTED,
                ResetOptions.ALL);
    }

    private final Stage stage;
    private final AppSettings settings;
    private final BiConsumer<String, State> languageChanger;
    private final SelectionState selection = new SelectionState();
    private final MapView map = new MapView(selection);
    private final SidePanel side = new SidePanel(this);
    private final Label hoverLabel = new Label();
    private final Label selectionLabel = new Label();
    private final Label zoomLabel = new Label();
    private final ProgressBar scanProgress = new ProgressBar();
    private final Label scanLabel = new Label(t("status.scanning"));
    private final HBox scanBox;
    private final VBox emptyState;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "zcr-worker");
        t.setDaemon(true);
        return t;
    });
    private final AtomicLong previewGeneration = new AtomicLong();
    private final List<MenuItem> saveDependentItems = new ArrayList<>();
    private MenuItem undoItem;
    private MenuItem redoItem;

    private SaveIndex index;
    private ResetPlan currentPlan;
    private Task<SaveIndex> scanTask;
    private boolean busy;

    public MainWindow(Stage stage, AppSettings settings, State initial, BiConsumer<String, State> languageChanger) {
        this.stage = stage;
        this.settings = settings;
        this.languageChanger = languageChanger;

        Button cancelScan = new Button(t("button.cancel"));
        cancelScan.setOnAction(e -> {
            if (scanTask != null) {
                scanTask.cancel(true);
            }
        });
        scanProgress.setPrefWidth(160);
        scanBox = new HBox(8, scanLabel, scanProgress, cancelScan);
        scanBox.setAlignment(Pos.CENTER_RIGHT);
        scanBox.setVisible(false);
        scanBox.setManaged(false);

        HBox.setHgrow(hoverLabel, Priority.ALWAYS);
        hoverLabel.setMaxWidth(Double.MAX_VALUE);
        HBox status = new HBox(16, hoverLabel, scanBox, selectionLabel, zoomLabel);
        status.getStyleClass().add("status-bar");
        status.setAlignment(Pos.CENTER_LEFT);

        Button openButton = new Button(t("menu.file.open"));
        openButton.getStyleClass().add("primary-button");
        openButton.setOnAction(e -> chooseSave());
        Label emptyTitle = new Label(t("empty.title"));
        emptyTitle.getStyleClass().add("empty-title");
        Label emptyHint = new Label(t("empty.hint"));
        emptyHint.getStyleClass().add("empty-hint");
        emptyHint.setWrapText(true);
        emptyHint.setTextAlignment(TextAlignment.CENTER);
        emptyHint.setMaxWidth(440);
        emptyState = new VBox(14, emptyTitle, emptyHint, openButton);
        emptyState.setAlignment(Pos.CENTER);
        emptyState.setPickOnBounds(false);
        StackPane center = new StackPane(map, emptyState);

        BorderPane root = new BorderPane();
        root.setTop(buildMenus());
        root.setCenter(center);
        root.setRight(side);
        root.setBottom(status);

        // Opening a save by dropping its folder on the window.
        root.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        root.setOnDragDropped(e -> {
            List<File> files = e.getDragboard().getFiles();
            if (files != null && !files.isEmpty() && !busy) {
                openSave(files.get(0).toPath(), false);
            }
            e.setDropCompleted(true);
            e.consume();
        });

        Scene scene = new Scene(root, stage.getScene() != null ? stage.getScene().getWidth() : 1280,
                stage.getScene() != null ? stage.getScene().getHeight() : 800);
        scene.getStylesheets().add(MainWindow.class.getResource("/css/app.css").toExternalForm());
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(560);
        stage.setTitle(ZomboidChunkResetApp.NAME);
        // Never close the window in the middle of a deletion.
        stage.setOnCloseRequest(e -> {
            if (busy) {
                e.consume();
            }
        });

        map.modeProperty().bind(side.modeProperty());
        map.hoveredProperty().addListener((obs, a, b) -> updateHover(b));
        map.zoomProperty().addListener((obs, a, b) -> updateZoom());
        selection.addListener(this::selectionChanged);

        side.modeProperty().set(initial.mode());
        side.setOptions(initial.options());
        if (initial.index() != null) {
            showSave(initial.index(), false);
            selection.replace(initial.selection());
        }
        updateHover(null);
        updateZoom();
        selectionChanged();
        updateSaveDependent();
    }

    public void show() {
        stage.show();
    }

    /** Stops background work (when the application exits or the window is rebuilt). */
    public void dispose() {
        if (scanTask != null) {
            scanTask.cancel(true);
        }
        worker.shutdownNow();
    }

    /** What to keep when the window is rebuilt. */
    public State state() {
        return new State(index, selection.snapshot(), side.modeProperty().get(), side.options());
    }

    /** The reminder shown at every start: work on a copy. */
    public void showStartupWarning() {
        Dialogs.startupWarning(stage);
    }

    // ------------------------------------------------------------------ menus

    private MenuBar buildMenus() {
        MenuItem open = item(t("menu.file.open"), "Shortcut+O", this::chooseSave);
        MenuItem rescan = item(t("menu.file.rescan"), "F5", () -> {
            if (index != null) {
                openSave(index.root(), true);
            }
        });
        MenuItem importSel = item(t("menu.file.importSelection"), "Shortcut+Shift+I", this::chooseImport);
        MenuItem exportSel = item(t("menu.file.exportSelection"), "Shortcut+Shift+E", this::chooseExport);
        MenuItem quit = item(t("menu.file.quit"), "Shortcut+Q", () -> {
            if (!busy) {
                stage.close();
            }
        });
        Menu file = new Menu(t("menu.file"), null, open, rescan, new SeparatorMenuItem(), importSel, exportSel,
                new SeparatorMenuItem(), quit);

        undoItem = item(t("menu.edit.undo"), "Shortcut+Z", this::undo);
        redoItem = item(t("menu.edit.redo"), "Shortcut+Y", this::redo);
        MenuItem selectAll = item(t("menu.edit.selectAll"), "Shortcut+A", this::selectAll);
        MenuItem invert = item(t("menu.edit.invert"), "Shortcut+I", this::invertSelection);
        MenuItem clear = item(t("menu.edit.clear"), "Shortcut+D", this::clearSelection);
        MenuItem byCoordinates = item(t("menu.edit.byCoordinates"), "Shortcut+R", this::selectByCoordinates);
        Menu edit = new Menu(t("menu.edit"), null, undoItem, redoItem, new SeparatorMenuItem(), selectAll, invert,
                clear, new SeparatorMenuItem(), byCoordinates);

        MenuItem zoomIn = item(t("menu.view.zoomIn"), "Shortcut+Equals", map::zoomIn);
        MenuItem zoomOut = item(t("menu.view.zoomOut"), "Shortcut+Minus", map::zoomOut);
        MenuItem fit = item(t("menu.view.fit"), "Shortcut+0", map::fit);
        MenuItem goTo = item(t("menu.view.goTo"), "Shortcut+G", this::goTo);
        Menu view = new Menu(t("menu.view"), null, zoomIn, zoomOut, fit, new SeparatorMenuItem(), goTo);

        Menu language = new Menu(t("menu.language"));
        ToggleGroup languages = new ToggleGroup();
        for (I18n.Language l : I18n.available(settings.languagesFolder())) {
            RadioMenuItem item = new RadioMenuItem(l.name());
            item.setToggleGroup(languages);
            item.setSelected(l.code().equals(I18n.code()));
            item.setOnAction(e -> {
                if (!l.code().equals(I18n.code()) && !busy) {
                    languageChanger.accept(l.code(), state());
                }
            });
            language.getItems().add(item);
        }
        language.getItems().addAll(new SeparatorMenuItem(),
                item(t("menu.language.add"), null, () -> Dialogs.addLanguage(stage, settings.languagesFolder())));

        MenuItem controls = item(t("menu.help.controls"), "F1", () -> Dialogs.showControls(stage));
        Menu help = new Menu(t("menu.help"), null, controls);

        saveDependentItems.addAll(List.of(rescan, importSel, exportSel, selectAll, invert, clear, byCoordinates, zoomIn,
                zoomOut, fit, goTo));
        return new MenuBar(file, edit, view, language, help);
    }

    private static MenuItem item(String text, String accelerator, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(KeyCombination.keyCombination(accelerator));
        }
        item.setOnAction(e -> action.run());
        return item;
    }

    private void updateSaveDependent() {
        for (MenuItem item : saveDependentItems) {
            item.setDisable(index == null || busy);
        }
        selectionLabel.setVisible(index != null);
        zoomLabel.setVisible(index != null);
        emptyState.setVisible(index == null && (scanTask == null || !scanTask.isRunning()));
    }

    // ------------------------------------------------------------------ opening a save

    private void chooseSave() {
        if (busy) {
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(t("dialog.open.title"));
        Path initial = settings.lastFolder().filter(Files::isDirectory).orElse(defaultSavesFolder());
        if (initial != null && Files.isDirectory(initial)) {
            chooser.setInitialDirectory(initial.toFile());
        }
        File folder = chooser.showDialog(stage);
        if (folder != null) {
            openSave(folder.toPath(), false);
        }
    }

    /** {@code <home>/Zomboid/Saves}, where the game keeps its saves on Windows and Linux. */
    private static Path defaultSavesFolder() {
        Path saves = Path.of(System.getProperty("user.home"), "Zomboid", "Saves");
        return Files.isDirectory(saves) ? saves : null;
    }

    /**
     * Scans a save folder in the background, then shows it.
     *
     * @param keepSelection keep the current selection (when scanning the same save again)
     */
    public void openSave(Path folder, boolean keepSelection) {
        if (scanTask != null && scanTask.isRunning()) {
            scanTask.cancel(true);
        }
        Task<SaveIndex> task = new Task<>() {
            @Override
            protected SaveIndex call() throws Exception {
                return SaveScanner.scan(folder, ScanOptions.FULL, (done, total) -> updateProgress(done, total));
            }
        };
        scanTask = task;
        scanProgress.progressProperty().bind(task.progressProperty());
        setScanning(true);
        task.setOnSucceeded(e -> {
            setScanning(false);
            showSave(task.getValue(), keepSelection);
        });
        task.setOnFailed(e -> {
            setScanning(false);
            Throwable error = task.getException();
            if (error instanceof NotASaveException) {
                Dialogs.error(stage, t("error.notASave.header"), t("error.notASave.text", folder));
            } else if (!(error instanceof InterruptedIOException)) {
                Dialogs.error(stage, t("error.scan.header"), String.valueOf(error.getMessage()));
            }
        });
        task.setOnCancelled(e -> setScanning(false));
        Thread thread = new Thread(task, "zcr-scan");
        thread.setDaemon(true);
        thread.start();
    }

    private void setScanning(boolean scanning) {
        scanBox.setVisible(scanning);
        scanBox.setManaged(scanning);
        updateSaveDependent();
        if (scanning) {
            emptyState.setVisible(false);
        }
    }

    private void showSave(SaveIndex newIndex, boolean keepSelection) {
        index = newIndex;
        currentPlan = null;
        settings.setLastFolder(newIndex.root().getParent());
        if (!keepSelection) {
            selection.reset();
        }
        map.setModel(MapModel.of(newIndex));
        side.showSave(newIndex);
        side.setRunningWarning(null);
        stage.setTitle(ZomboidChunkResetApp.NAME + " — " + newIndex.root().getFileName());
        updateSaveDependent();
        updateHover(null);
        selectionChanged();
        checkRunningGame(newIndex.root());
    }

    private void checkRunningGame(Path root) {
        worker.submit(() -> {
            RunningGameDetector.Result result = RunningGameDetector.check(root);
            Platform.runLater(() -> {
                if (index != null && index.root().equals(root)) {
                    side.setRunningWarning(result.suspicious() ? Dialogs.describeRunning(result) : null);
                }
            });
        });
    }

    /** The scanned save, or null (for tests and tools). */
    public SaveIndex index() {
        return index;
    }

    /** The map (for tests and tools). */
    public MapView map() {
        return map;
    }

    /** The selection (for tests and tools). */
    public SelectionState selection() {
        return selection;
    }

    /** The latest preview, or null (for tests and tools). */
    public ResetPlan currentPlan() {
        return currentPlan;
    }

    /** Whether a deletion is running (for tests and tools). */
    public boolean busy() {
        return busy;
    }

    /** Switches between "delete selected" and "keep selected". */
    public void setMode(ResetMode mode) {
        side.modeProperty().set(mode);
    }

    // ------------------------------------------------------------------ selection

    private void selectionChanged() {
        ChunkSelection s = selection.selection();
        int withChunks = countWithChunks(s);
        side.showSelection(s.size(), withChunks, s.countByCell().size(), selection.canUndo(), selection.canRedo());
        selectionLabel.setText(t("status.selection", number(s.size())));
        if (undoItem != null) {
            undoItem.setDisable(!selection.canUndo());
            redoItem.setDisable(!selection.canRedo());
        }
        updatePreview();
    }

    private int countWithChunks(ChunkSelection s) {
        MapModel model = map.model();
        if (model == null) {
            return 0;
        }
        int[] count = {0};
        s.forEach(p -> {
            if (model.hasChunk(p.x(), p.y())) {
                count[0]++;
            }
        });
        return count[0];
    }

    private void selectAll() {
        dataBounds().ifPresent(b -> selection.edit(s -> s.addRect(b)));
    }

    @Override
    public void invertSelection() {
        dataBounds().ifPresent(b -> selection.edit(s -> s.invertWithin(b)));
    }

    @Override
    public void clearSelection() {
        selection.edit(ChunkSelection::clear);
    }

    @Override
    public void undo() {
        selection.undo();
    }

    @Override
    public void redo() {
        selection.redo();
    }

    @Override
    public void selectByCoordinates() {
        if (index == null) {
            return;
        }
        Dialogs.selectByCoordinates(stage).ifPresent(choice -> selection.edit(s -> {
            if (choice.add()) {
                s.addRect(choice.chunks());
            } else {
                s.removeRect(choice.chunks());
            }
        }));
    }

    private void goTo() {
        if (index != null) {
            Dialogs.goTo(stage).ifPresent(p -> map.centerOnSquare(p[0] + 0.5, p[1] + 0.5));
        }
    }

    private Optional<ChunkBounds> dataBounds() {
        return map.model() == null ? Optional.empty() : map.model().dataBounds();
    }

    // ------------------------------------------------------------------ import / export

    private FileChooser selectionChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(t("file.filter.selection"), "*.json"));
        settings.lastFolder().filter(Files::isDirectory).ifPresent(f -> chooser.setInitialDirectory(f.toFile()));
        return chooser;
    }

    private void chooseExport() {
        if (index == null) {
            return;
        }
        FileChooser chooser = selectionChooser(t("export.title"));
        chooser.setInitialFileName("selection.json");
        File file = chooser.showSaveDialog(stage);
        if (file != null) {
            exportSelection(file.toPath());
        }
    }

    private void chooseImport() {
        if (index == null) {
            return;
        }
        File file = selectionChooser(t("import.title")).showOpenDialog(stage);
        if (file != null) {
            importSelection(file.toPath());
        }
    }

    /** Writes the selection and the current mode to a file. */
    public void exportSelection(Path file) {
        try {
            SelectionFile.write(file, selection.snapshot(), side.modeProperty().get());
        } catch (IOException e) {
            Dialogs.error(stage, t("export.error.header"), String.valueOf(e.getMessage()));
        }
    }

    /** Replaces the selection (undoable) by a file's content, and switches to the file's mode if it has one. */
    public void importSelection(Path file) {
        SelectionFile.Content content;
        try {
            content = SelectionFile.read(file);
        } catch (IOException e) {
            Dialogs.error(stage, t("import.error.header"), String.valueOf(e.getMessage()));
            return;
        }
        selection.replace(content.selection());
        content.mode().ifPresent(m -> side.modeProperty().set(m));
        if (!content.selection().isEmpty() && countWithChunks(content.selection()) == 0) {
            Dialogs.info(stage, t("import.title"), t("import.noOverlap"));
        }
    }

    // ------------------------------------------------------------------ preview, simulation, deletion

    @Override
    public void optionsChanged() {
        updatePreview();
    }

    private void updatePreview() {
        if (index == null) {
            side.showPlan(null);
            return;
        }
        long generation = previewGeneration.incrementAndGet();
        SaveIndex idx = index;
        ChunkSelection snapshot = selection.snapshot();
        ResetMode mode = side.modeProperty().get();
        ResetOptions options = side.options();
        worker.submit(() -> {
            if (generation != previewGeneration.get()) {
                return;
            }
            ResetPlan plan = ResetPlanner.plan(idx, snapshot, mode, options);
            Platform.runLater(() -> {
                if (generation == previewGeneration.get()) {
                    currentPlan = plan;
                    side.showPlan(plan);
                }
            });
        });
    }

    @Override
    public void simulate() {
        if (index == null || busy) {
            return;
        }
        // Never reuse the preview: it may be a step behind the selection. The plan is computed again
        // from exactly what is selected when the button is pressed.
        SaveIndex idx = index;
        ChunkSelection snapshot = selection.snapshot();
        ResetMode mode = side.modeProperty().get();
        ResetOptions options = side.options();
        worker.submit(() -> {
            ResetPlan plan = ResetPlanner.plan(idx, snapshot, mode, options);
            ResetReport report = ResetExecutor.run(plan, ResetExecutor.Mode.SIMULATE, ResetProgress.NONE);
            Platform.runLater(() -> Dialogs.showReport(stage, report));
        });
    }

    @Override
    public void delete() {
        if (index == null || busy) {
            return;
        }
        SaveIndex idx = index;
        ChunkSelection snapshot = selection.snapshot();
        ResetMode mode = side.modeProperty().get();
        ResetOptions options = side.options();
        worker.submit(() -> {
            // Fresh plan and fresh check for a running game, right now.
            ResetPlan plan = ResetPlanner.plan(idx, snapshot, mode, options);
            RunningGameDetector.Result running = RunningGameDetector.check(idx.root());
            Platform.runLater(() -> {
                side.setRunningWarning(running.suspicious() ? Dialogs.describeRunning(running) : null);
                if (plan.isEmpty()) {
                    Dialogs.info(stage, t("confirm.title"), t("warning.NOTHING_TO_DO"));
                    return;
                }
                if (Dialogs.confirmDeletion(stage, plan, running)) {
                    runDeletion(plan);
                }
            });
        });
    }

    private void runDeletion(ResetPlan plan) {
        setBusy(true);
        ProgressWindow progress = new ProgressWindow(stage);
        AtomicLong lastUpdate = new AtomicLong();
        Thread thread = new Thread(() -> {
            ResetReport report = ResetExecutor.run(plan, ResetExecutor.Mode.APPLY, (done, total) -> {
                long now = System.nanoTime();
                if (done == total || now - lastUpdate.get() > 50_000_000L) {
                    lastUpdate.set(now);
                    Platform.runLater(() -> progress.update(done, total));
                }
            });
            Platform.runLater(() -> {
                progress.close();
                setBusy(false);
                Dialogs.showReport(stage, report);
                if (report.changesMade()) {
                    openSave(plan.index().root(), true);
                }
            });
        }, "zcr-delete");
        thread.setDaemon(false); // never let the program exit in the middle of a deletion
        progress.setOnCancel(thread::interrupt);
        thread.start();
        progress.show();
    }

    private void setBusy(boolean value) {
        busy = value;
        side.setBusy(value);
        updateSaveDependent();
    }

    @Override
    public void showIssues() {
        if (index != null) {
            Dialogs.showIssues(stage, index.issues());
        }
    }

    // ------------------------------------------------------------------ status bar

    private void updateHover(ChunkPos pos) {
        if (pos == null || index == null) {
            hoverLabel.setText(index == null ? "" : t("status.hint"));
            return;
        }
        StringBuilder text = new StringBuilder(t("status.position", pos.x(), pos.y(), pos.cell().x(), pos.cell().y(),
                pos.minSquareX(), pos.maxSquareX(), pos.minSquareY(), pos.maxSquareY()));
        ChunkEntry entry = index.chunks().get(pos);
        text.append("  ·  ").append(entry != null
                ? t("status.saved", I18n.kilobytes(entry.fileSize()))
                : t("status.notGenerated"));
        if (selection.selection().contains(pos)) {
            text.append("  ·  ").append(t("status.selected"));
        }
        for (PlayerRecord p : index.players()) {
            if (p.chunk().equals(pos)) {
                text.append("  ·  ").append(t("status.player", p.displayName()));
            }
        }
        hoverLabel.setText(text.toString());
    }

    private void updateZoom() {
        double s = map.zoomProperty().get();
        zoomLabel.setText(t("status.zoom", s >= 1 ? String.format(Locale.ROOT, "%.0f", s)
                : String.format(I18n.locale(), "%.2f", s)));
    }
}
