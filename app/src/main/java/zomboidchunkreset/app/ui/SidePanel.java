package zomboidchunkreset.app.ui;

import static zomboidchunkreset.app.i18n.I18n.megabytes;
import static zomboidchunkreset.app.i18n.I18n.number;
import static zomboidchunkreset.app.i18n.I18n.t;

import java.util.List;
import java.util.stream.Collectors;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import zomboidchunkreset.app.map.Palette;
import zomboidchunkreset.core.db.PlayerRecord;
import zomboidchunkreset.core.reset.PlanWarning;
import zomboidchunkreset.core.reset.ResetMode;
import zomboidchunkreset.core.reset.ResetOptions;
import zomboidchunkreset.core.reset.ResetPlan;
import zomboidchunkreset.core.save.SaveIndex;
import zomboidchunkreset.core.save.ScanIssue;
import zomboidchunkreset.core.terrain.TerrainCategory;

/** Right-hand panel: save summary, selection, action settings with a live preview, legend. */
final class SidePanel extends ScrollPane {

    /** What the panel's buttons trigger. */
    interface Actions {
        void invertSelection();

        void clearSelection();

        void undo();

        void redo();

        void selectByCoordinates();

        void showIssues();

        void simulate();

        void delete();

        void optionsChanged();
    }

    private final VBox terrainLegend = new VBox(6);
    private final VBox plainLegend = new VBox(6);
    private final Label terrainNote = muted(t("legend.terrain.note"));
    private final Label plainNote = muted(t("legend.shade"));
    private final Actions actions;

    // Save
    private final Label saveName = new Label();
    private final Label saveKind = new Label();
    private final Label saveChunks = new Label();
    private final Label saveDetails = new Label();
    private final Label saveIssues = new Label();
    private final HBox issuesRow;
    private final Label runningWarning = new Label();

    // Selection
    private final Label selectionCount = new Label();
    private final Label selectionDetail = new Label();
    private final Button undo = new Button(t("button.undo"));
    private final Button redo = new Button(t("button.redo"));

    // Action
    private final ObjectProperty<ResetMode> mode = new SimpleObjectProperty<>(ResetMode.DELETE_SELECTED);
    private final CheckBox zombies = new CheckBox(t("option.zombies"));
    private final CheckBox animals = new CheckBox(t("option.animals"));
    private final CheckBox isoRegions = new CheckBox(t("option.isoRegions"));
    private final CheckBox vehicles = new CheckBox(t("option.vehicles"));
    private final Label previewDeleted = new Label("–");
    private final Label previewBytes = new Label("–");
    private final Label previewKept = new Label("–");
    private final Label previewCells = new Label("–");
    private final Label previewCellFiles = new Label("–");
    private final Label previewIso = new Label("–");
    private final Label previewVehicles = new Label("–");
    private final VBox warnings = new VBox(6);
    private final Button simulate = new Button(t("button.simulate"));
    private final Button delete = new Button(t("button.delete"));

    private final VBox content = new VBox(12);
    private final List<Node> saveDependent;

    SidePanel(Actions actions) {
        this.actions = actions;
        getStyleClass().add("side-panel");
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setPrefWidth(330);
        setMinWidth(300);

        saveName.getStyleClass().add("save-name");
        saveName.setWrapText(true);
        saveKind.getStyleClass().add("muted");
        saveIssues.getStyleClass().add("issue-text");
        Button issuesButton = new Button(t("button.details"));
        issuesButton.setOnAction(e -> actions.showIssues());
        issuesRow = new HBox(8, saveIssues, issuesButton);
        issuesRow.setAlignment(Pos.CENTER_LEFT);
        runningWarning.getStyleClass().add("danger-box");
        runningWarning.setId("running-warning");
        runningWarning.setWrapText(true);
        runningWarning.setMaxWidth(Double.MAX_VALUE);
        setRunningWarning(null);
        VBox save = section(t("section.save"), saveName, saveKind, saveChunks, saveDetails, issuesRow, runningWarning);

        selectionCount.getStyleClass().add("value");
        selectionCount.setWrapText(true);
        selectionDetail.getStyleClass().add("muted");
        selectionDetail.setWrapText(true);
        Button invert = new Button(t("button.invert"));
        invert.setOnAction(e -> actions.invertSelection());
        Button clear = new Button(t("button.clear"));
        clear.setOnAction(e -> actions.clearSelection());
        undo.setOnAction(e -> actions.undo());
        redo.setOnAction(e -> actions.redo());
        Button byCoordinates = new Button(t("button.selectByCoordinates"));
        byCoordinates.setOnAction(e -> actions.selectByCoordinates());
        byCoordinates.setMaxWidth(Double.MAX_VALUE);
        FlowPane selectionButtons = new FlowPane(6, 6, invert, clear, undo, redo);
        VBox selection = section(t("section.selection"), selectionCount, selectionDetail, selectionButtons,
                byCoordinates);

        ToggleGroup group = new ToggleGroup();
        RadioButton deleteSelected = new RadioButton(t("mode.deleteSelected"));
        RadioButton keepSelected = new RadioButton(t("mode.keepSelected"));
        deleteSelected.setToggleGroup(group);
        keepSelected.setToggleGroup(group);
        deleteSelected.setUserData(ResetMode.DELETE_SELECTED);
        keepSelected.setUserData(ResetMode.KEEP_SELECTED);
        deleteSelected.setWrapText(true);
        keepSelected.setWrapText(true);
        deleteSelected.setSelected(true);
        group.selectedToggleProperty().addListener((obs, a, b) -> {
            if (b != null) {
                mode.set((ResetMode) b.getUserData());
            }
        });
        mode.addListener((obs, a, b) -> {
            group.selectToggle(b == ResetMode.DELETE_SELECTED ? deleteSelected : keepSelected);
            actions.optionsChanged();
        });

        Label alsoDelete = new Label(t("option.title"));
        alsoDelete.getStyleClass().add("muted");
        for (CheckBox box : List.of(zombies, animals, isoRegions, vehicles)) {
            box.setSelected(true);
            box.setWrapText(true);
            box.selectedProperty().addListener((obs, a, b) -> actions.optionsChanged());
        }
        zombies.setTooltip(tip("option.zombies.tooltip"));
        animals.setTooltip(tip("option.animals.tooltip"));
        isoRegions.setTooltip(tip("option.isoRegions.tooltip"));
        vehicles.setTooltip(tip("option.vehicles.tooltip"));

        GridPane preview = new GridPane();
        preview.setHgap(10);
        preview.setVgap(4);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setHgrow(Priority.ALWAYS);
        preview.getColumnConstraints().addAll(labels, new ColumnConstraints());
        addRow(preview, 0, t("preview.chunksDeleted"), previewDeleted);
        addRow(preview, 1, t("preview.bytesDeleted"), previewBytes);
        addRow(preview, 2, t("preview.chunksKept"), previewKept);
        addRow(preview, 3, t("preview.cellsReset"), previewCells);
        addRow(preview, 4, t("preview.cellFiles"), previewCellFiles);
        addRow(preview, 5, t("preview.isoFiles"), previewIso);
        addRow(preview, 6, t("preview.vehicles"), previewVehicles);

        Label previewTitle = new Label(t("preview.title"));
        previewTitle.getStyleClass().add("subsection-title");
        simulate.setMaxWidth(Double.MAX_VALUE);
        simulate.setOnAction(e -> actions.simulate());
        delete.setMaxWidth(Double.MAX_VALUE);
        delete.getStyleClass().add("danger-button");
        delete.setId("delete-button");
        delete.setOnAction(e -> actions.delete());
        Label note = new Label(t("action.confirmNote"));
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        VBox action = section(t("section.action"), deleteSelected, keepSelected, alsoDelete, zombies, animals,
                isoRegions, vehicles, previewTitle, preview, warnings, simulate, delete, note);

        int grass = TerrainCategory.GRASS_MEDIUM.ordinal();
        terrainLegend.getChildren().addAll(
                swatch(Palette.terrainColor(TerrainCategory.WATER), t("legend.terrain.water")),
                swatch(Palette.terrainColor(TerrainCategory.ROAD), t("legend.terrain.road")),
                swatch(Palette.terrainColor(TerrainCategory.GRASS_MEDIUM), t("legend.terrain.grass")),
                swatch(Palette.terrainColor(TerrainCategory.TALL_GRASS), t("legend.terrain.tallGrass")),
                swatch(Palette.terrainColor(TerrainCategory.TREE), t("legend.terrain.tree")),
                swatch(Palette.terrainColor(TerrainCategory.SAND), t("legend.terrain.sand")),
                swatch(Palette.terrainColor(TerrainCategory.ROOF), t("legend.terrain.roof")),
                swatch(Palette.terrainColor(TerrainCategory.ROOF_FLAT), t("legend.terrain.flatRoof")),
                swatch(Palette.terrainColor(TerrainCategory.WALL), t("legend.terrain.wall")),
                swatch(Palette.terrainColor(TerrainCategory.NEUTRAL), t("legend.terrain.other")),
                swatch(Palette.terrain(Palette.STATE_RESET)[grass], t("legend.terrain.deleted")),
                swatch(Palette.terrain(Palette.STATE_PROTECTED)[grass], t("legend.terrain.protected")),
                swatch(Palette.kept(5), t("legend.terrain.plain")));
        plainLegend.getChildren().addAll(
                swatch(Palette.kept(5), t("legend.kept")),
                swatch(Palette.deleted(5), t("legend.deleted")),
                swatch(Palette.protectedChunk(5), t("legend.protected")));
        VBox legend = section(t("section.legend"),
                terrainLegend,
                plainLegend,
                swatch(Palette.SELECTED_EMPTY_DELETE, t("legend.selectedEmpty")),
                swatch(Palette.EMPTY_A, t("legend.notGenerated")),
                playerSwatch(),
                terrainNote,
                plainNote);
        setTerrainShown(true);

        content.getChildren().addAll(save, selection, action, legend);
        content.setPadding(new Insets(12));
        setContent(content);
        saveDependent = List.of(selection, action, legend);
        showSave(null);
        showPlan(null);
    }

    // ------------------------------------------------------------------ state

    ObjectProperty<ResetMode> modeProperty() {
        return mode;
    }

    ResetOptions options() {
        return new ResetOptions(zombies.isSelected(), animals.isSelected(), isoRegions.isSelected(),
                vehicles.isSelected());
    }

    void setOptions(ResetOptions options) {
        zombies.setSelected(options.zombiePopulation());
        animals.setSelected(options.animalPopulation());
        isoRegions.setSelected(options.isoRegions());
        vehicles.setSelected(options.vehicles());
    }

    /** Shows (or hides, with null) the "the game seems to be running" warning. */
    void setRunningWarning(String text) {
        runningWarning.setText(text == null ? "" : text);
        runningWarning.setVisible(text != null);
        runningWarning.setManaged(text != null);
    }

    /** Disables the actions while a deletion runs. */
    void setBusy(boolean busy) {
        busyState = busy;
        for (Node n : saveDependent) {
            n.setDisable(busy || !saveOpen);
        }
        refreshButtons();
    }

    private boolean busyState;
    private boolean saveOpen;
    private boolean planEmpty = true;

    private void refreshButtons() {
        simulate.setDisable(busyState || planEmpty);
        delete.setDisable(busyState || planEmpty);
    }

    void showSave(SaveIndex index) {
        saveOpen = index != null;
        for (Node n : saveDependent) {
            n.setDisable(index == null || busyState);
        }
        for (Label l : List.of(saveKind, saveChunks, saveDetails)) {
            l.setVisible(index != null);
            l.setManaged(index != null);
        }
        if (index == null) {
            saveName.setText(t("save.none"));
            issuesRow.setVisible(false);
            issuesRow.setManaged(false);
            return;
        }
        saveName.setText(index.root().getFileName() == null ? index.root().toString()
                : index.root().getFileName().toString());
        saveName.setTooltip(new Tooltip(index.root().toString()));
        saveKind.setText(t("save.kind." + index.kind()));
        saveChunks.setText(t("save.chunks", number(index.chunks().size()), megabytes(index.totalChunkBytes())));
        saveDetails.setText(t("save.details", number(index.chunkCountByCell().size()), number(index.players().size()),
                number(index.vehicles().size())));
        long problems = index.issues().stream().filter(i -> i.severity() != ScanIssue.Severity.INFO).count();
        issuesRow.setVisible(!index.issues().isEmpty());
        issuesRow.setManaged(!index.issues().isEmpty());
        saveIssues.setText(problems > 0 ? t("save.problems", number(problems))
                : t("save.notes", number(index.issues().size())));
    }

    void showSelection(int positions, int withChunks, int cells, boolean canUndo, boolean canRedo) {
        selectionCount.setText(t("selection.count", number(positions)));
        selectionDetail.setText(positions == 0 ? t("selection.help")
                : t("selection.detail", number(withChunks), number(cells)));
        undo.setDisable(!canUndo);
        redo.setDisable(!canRedo);
    }

    void showPlan(ResetPlan plan) {
        warnings.getChildren().clear();
        if (plan == null) {
            for (Label l : List.of(previewDeleted, previewBytes, previewKept, previewCells, previewCellFiles,
                    previewIso, previewVehicles)) {
                l.setText("–");
            }
            planEmpty = true;
            refreshButtons();
            return;
        }
        int total = plan.index().chunks().size();
        previewDeleted.setText(number(plan.chunksToDelete().size()));
        previewDeleted.getStyleClass().remove("danger-number");
        if (!plan.chunksToDelete().isEmpty()) {
            previewDeleted.getStyleClass().add("danger-number");
        }
        previewBytes.setText(megabytes(plan.chunkBytesToDelete()));
        previewKept.setText(number(total - plan.chunksToDelete().size()));
        previewCells.setText(number(plan.cellsToReset().size()));
        previewCellFiles.setText(number(plan.cellFileCount()));
        previewIso.setText(number(plan.isoFilesToDelete().size()));
        previewVehicles.setText(number(plan.vehiclesToDelete().size()));
        for (PlanWarning w : plan.warnings()) {
            warnings.getChildren().add(warning(w, plan));
        }
        planEmpty = plan.isEmpty();
        refreshButtons();
    }

    private Label warning(PlanWarning w, ResetPlan plan) {
        String text = switch (w.type()) {
            case PLAYERS_IN_RESET_AREA -> t("warning.PLAYERS_IN_RESET_AREA", number(plan.playersInResetArea().size()),
                    plan.playersInResetArea().stream().map(PlayerRecord::displayName).collect(Collectors.joining(", ")));
            case ENTIRE_SAVE_RESET -> t("warning.ENTIRE_SAVE_RESET", number(plan.chunksToDelete().size()));
            case SCAN_ISSUES_PRESENT -> t("warning.SCAN_ISSUES_PRESENT");
            default -> t("warning." + w.type());
        };
        Label l = new Label(text);
        l.setWrapText(true);
        l.getStyleClass().add(w.type() == PlanWarning.Type.NOTHING_TO_DO ? "info-box" : "warning-box");
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    // ------------------------------------------------------------------ helpers

    private static VBox section(String title, Node... children) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");
        VBox box = new VBox(6);
        box.getStyleClass().add("section");
        box.getChildren().add(heading);
        box.getChildren().addAll(children);
        return box;
    }

    private static void addRow(GridPane grid, int row, String label, Label value) {
        Label l = new Label(label);
        l.setWrapText(true);
        value.getStyleClass().add("value");
        value.setMinWidth(Region.USE_PREF_SIZE); // numbers never get cut; labels wrap instead
        grid.add(l, 0, row);
        grid.add(value, 1, row);
    }

    /** Shows the legend of the terrain view or of the plain view. */
    public void setTerrainShown(boolean shown) {
        for (Node n : List.of(terrainLegend, terrainNote)) {
            n.setVisible(shown);
            n.setManaged(shown);
        }
        for (Node n : List.of(plainLegend, plainNote)) {
            n.setVisible(!shown);
            n.setManaged(!shown);
        }
    }

    private static Tooltip tip(String key) {
        Tooltip tooltip = new Tooltip(t(key));
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(360);
        return tooltip;
    }

    private static Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("muted");
        l.setWrapText(true);
        return l;
    }

    private static HBox swatch(int argb, String text) {
        Region box = new Region();
        box.getStyleClass().add("swatch");
        box.setStyle("-fx-background-color: " + Palette.css(argb) + ";");
        Label l = new Label(text);
        l.setWrapText(true);
        HBox row = new HBox(8, box, l);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static HBox playerSwatch() {
        Region dot = new Region();
        dot.getStyleClass().addAll("swatch", "player-swatch");
        dot.setStyle("-fx-background-color: " + Palette.PLAYER + ";");
        HBox row = new HBox(8, dot, new Label(t("legend.player")));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
