package zomboidchunkreset.app.ui;

import static zomboidchunkreset.app.i18n.I18n.megabytes;
import static zomboidchunkreset.app.i18n.I18n.number;
import static zomboidchunkreset.app.i18n.I18n.t;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.db.PlayerRecord;
import zomboidchunkreset.core.reset.PlanWarning;
import zomboidchunkreset.core.reset.ResetPlan;
import zomboidchunkreset.core.reset.ResetReport;
import zomboidchunkreset.core.safety.RunningGameDetector;
import zomboidchunkreset.core.save.ScanIssue;

/** Small dialogs. */
final class Dialogs {

    private Dialogs() {
    }

    /** Result of "select by coordinates". */
    record RectangleChoice(ChunkBounds chunks, boolean add) {
    }

    /** Asks for an in-game rectangle (squares) and converts it to the chunks covering it. */
    static Optional<RectangleChoice> selectByCoordinates(Window owner) {
        Dialog<RectangleChoice> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(t("coords.select.title"));
        dialog.setHeaderText(t("coords.select.header"));
        TextField x1 = intField();
        TextField y1 = intField();
        TextField x2 = intField();
        TextField y2 = intField();
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label(t("coords.corner1")), new Label("X"), x1, new Label("Y"), y1);
        grid.addRow(1, new Label(t("coords.corner2")), new Label("X"), x2, new Label("Y"), y2);
        ToggleGroup group = new ToggleGroup();
        RadioButton add = new RadioButton(t("coords.add"));
        RadioButton remove = new RadioButton(t("coords.remove"));
        add.setToggleGroup(group);
        remove.setToggleGroup(group);
        add.setSelected(true);
        Label result = new Label();
        result.getStyleClass().add("muted");
        result.setWrapText(true);
        Runnable refresh = () -> result.setText(parseRect(x1, y1, x2, y2)
                .map(r -> {
                    ChunkPos first = new ChunkPos(r.minX(), r.minY());
                    ChunkPos last = new ChunkPos(r.maxX(), r.maxY());
                    return t("coords.result", r.minX(), r.maxX(), r.minY(), r.maxY(),
                            number((long) r.width() * r.height()), first.minSquareX(), last.maxSquareX(),
                            first.minSquareY(), last.maxSquareY());
                })
                .orElse(t("coords.invalid")));
        for (TextField f : List.of(x1, y1, x2, y2)) {
            f.textProperty().addListener((obs, a, b) -> refresh.run());
        }
        refresh.run();
        VBox box = new VBox(10, grid, new HBox(12, add, remove), result);
        box.setPadding(new Insets(10));
        box.setPrefWidth(460);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Node ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(Bindings.createBooleanBinding(() -> parseRect(x1, y1, x2, y2).isEmpty(),
                x1.textProperty(), y1.textProperty(), x2.textProperty(), y2.textProperty()));
        dialog.setResultConverter(b -> b == ButtonType.OK
                ? new RectangleChoice(parseRect(x1, y1, x2, y2).orElseThrow(), add.isSelected())
                : null);
        x1.requestFocus();
        return dialog.showAndWait();
    }

    /** Asks for an in-game position (squares). */
    static Optional<int[]> goTo(Window owner) {
        Dialog<int[]> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(t("coords.goto.title"));
        dialog.setHeaderText(t("coords.goto.header"));
        TextField x = intField();
        TextField y = intField();
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label("X"), x, new Label("Y"), y);
        grid.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(Bindings.createBooleanBinding(
                () -> parse(x).isEmpty() || parse(y).isEmpty(), x.textProperty(), y.textProperty()));
        dialog.setResultConverter(b -> b == ButtonType.OK ? new int[] {parse(x).orElseThrow(), parse(y).orElseThrow()}
                : null);
        return dialog.showAndWait();
    }

    /** Lists the problems found by the scan. */
    static void showIssues(Window owner, List<ScanIssue> issues) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(t("issues.title"));
        dialog.setHeaderText(t("issues.header", number(issues.size())));
        ListView<String> list = new ListView<>();
        for (ScanIssue issue : issues) {
            list.getItems().add(t("issue." + issue.type()) + " — "
                    + (issue.file() != null ? issue.file().getFileName() + ": " : "") + issue.detail());
        }
        list.setPrefSize(720, 360);
        dialog.getDialogPane().setContent(list);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    /** Shows the result of a simulation (or, later, of a real run). */
    static void showReport(Window owner, ResetReport report) {
        Alert alert = new Alert(report.outcome() == ResetReport.Outcome.COMPLETED ? Alert.AlertType.INFORMATION
                : Alert.AlertType.WARNING);
        alert.initOwner(owner);
        alert.setTitle(t("report.title." + report.mode()));
        alert.setHeaderText(t("report.outcome." + report.outcome() + "." + report.mode()));
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(4);
        int row = 0;
        grid.addRow(row++, new Label(t("report.chunks")),
                value(t("preview.chunksValue", number(report.chunkFilesDeleted()), megabytes(report.chunkBytesDeleted()))));
        grid.addRow(row++, new Label(t("report.folders")), value(number(report.columnFoldersRemoved())));
        grid.addRow(row++, new Label(t("report.cellFiles")), value(number(report.cellFilesDeleted())));
        grid.addRow(row++, new Label(t("report.isoFiles")), value(number(report.isoFilesDeleted())));
        grid.addRow(row++, new Label(t("report.header")),
                value(t(report.regionHeaderRewritten() ? "report.yes" : "report.no")));
        grid.addRow(row, new Label(t("report.vehicles")), value(number(report.vehiclesDeleted())));
        VBox box = new VBox(10, grid);
        if (report.message() != null) {
            Label message = new Label(report.message());
            message.setWrapText(true);
            message.getStyleClass().add("warning-box");
            box.getChildren().add(message);
        }
        if (report.mode() == zomboidchunkreset.core.reset.ResetExecutor.Mode.SIMULATE
                && report.outcome() == ResetReport.Outcome.COMPLETED) {
            Label nothing = new Label(t("report.simulationNote"));
            nothing.getStyleClass().add("muted");
            nothing.setWrapText(true);
            box.getChildren().add(nothing);
        }
        box.setPrefWidth(440);
        alert.getDialogPane().setContent(box);
        alert.showAndWait();
    }

    /** Explains the mouse and keyboard controls. */
    static void showControls(Window owner) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle(t("help.controls.title"));
        alert.setHeaderText(t("help.controls.header"));
        Label text = new Label(t("help.controls.text"));
        text.setWrapText(true);
        text.setPrefWidth(460);
        text.setMinHeight(Region.USE_PREF_SIZE);
        alert.getDialogPane().setContent(text);
        alert.showAndWait();
    }

    /** The warning shown at every start. */
    static void startupWarning(Window owner) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(owner);
        alert.setTitle(t("startup.title"));
        alert.setHeaderText(t("startup.header"));
        Label text = new Label(t("startup.text"));
        text.setWrapText(true);
        text.setPrefWidth(480);
        text.setMinHeight(Region.USE_PREF_SIZE);
        alert.getDialogPane().setContent(text);
        alert.showAndWait();
    }

    /** One sentence (or two) describing the signs of a running game. */
    static String describeRunning(RunningGameDetector.Result result) {
        List<String> parts = new ArrayList<>();
        if (!result.processes().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (RunningGameDetector.ProcessInfo p : result.processes()) {
                String command = p.command().replace('\\', '/');
                names.add(command.substring(command.lastIndexOf('/') + 1) + " (PID " + p.pid() + ")");
            }
            parts.add(t("running.processes", String.join(", ", names)));
        }
        result.recentWrite().ifPresent(time -> parts.add(t("running.recentWrite",
                Math.max(0, Duration.between(time, Instant.now()).getSeconds()))));
        parts.add(t("running.advice"));
        return String.join(" ", parts);
    }

    /**
     * Asks for an explicit confirmation before deleting: summary, warnings, backup reminder, and a
     * check box that must be ticked (two when the whole save would be reset).
     *
     * @return true if the user confirmed
     */
    static boolean confirmDeletion(Window owner, ResetPlan plan, RunningGameDetector.Result running) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(t("confirm.title"));
        String saveName = plan.index().root().getFileName() == null ? plan.index().root().toString()
                : plan.index().root().getFileName().toString();
        dialog.setHeaderText(t("confirm.header", number(plan.chunksToDelete().size()), saveName));

        VBox box = new VBox(10);
        box.setPrefWidth(520);
        if (running.suspicious()) {
            box.getChildren().add(styled(describeRunning(running), "danger-box"));
        }

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(4);
        int row = 0;
        grid.addRow(row++, new Label(t("preview.chunksDeleted")), value(number(plan.chunksToDelete().size())));
        grid.addRow(row++, new Label(t("preview.bytesDeleted")), value(megabytes(plan.chunkBytesToDelete())));
        grid.addRow(row++, new Label(t("preview.chunksKept")),
                value(number(plan.index().chunks().size() - plan.chunksToDelete().size())));
        grid.addRow(row++, new Label(t("preview.cellsReset")), value(number(plan.cellsToReset().size())));
        grid.addRow(row++, new Label(t("preview.cellFiles")), value(number(plan.cellFileCount())));
        grid.addRow(row++, new Label(t("preview.isoFiles")), value(number(plan.isoFilesToDelete().size())));
        grid.addRow(row, new Label(t("preview.vehicles")), value(number(plan.vehiclesToDelete().size())));
        box.getChildren().add(grid);

        for (PlanWarning w : plan.warnings()) {
            if (w.type() == PlanWarning.Type.NOTHING_TO_DO) {
                continue;
            }
            String text = switch (w.type()) {
                case PLAYERS_IN_RESET_AREA -> t("warning.PLAYERS_IN_RESET_AREA",
                        number(plan.playersInResetArea().size()), plan.playersInResetArea().stream()
                                .map(PlayerRecord::displayName).collect(Collectors.joining(", ")));
                case ENTIRE_SAVE_RESET -> t("warning.ENTIRE_SAVE_RESET", number(plan.chunksToDelete().size()));
                default -> t("warning." + w.type());
            };
            box.getChildren().add(styled(text, w.type() == PlanWarning.Type.ENTIRE_SAVE_RESET ? "danger-box"
                    : "warning-box"));
        }

        box.getChildren().add(styled(t("confirm.backup"), "backup-box"));
        CheckBox backup = new CheckBox(t("confirm.backupCheck"));
        backup.setId("backup-check");
        backup.setWrapText(true);
        backup.setMinHeight(Region.USE_PREF_SIZE);
        box.getChildren().add(backup);
        CheckBox entire = new CheckBox(t("confirm.entireCheck"));
        entire.setId("entire-check");
        entire.setWrapText(true);
        entire.setMinHeight(Region.USE_PREF_SIZE);
        boolean everything = plan.hasWarning(PlanWarning.Type.ENTIRE_SAVE_RESET);
        if (everything) {
            box.getChildren().add(entire);
        }

        ButtonType deleteType = new ButtonType(t("confirm.delete"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, deleteType);
        dialog.getDialogPane().setContent(box);
        Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteType);
        deleteButton.setId("confirm-delete");
        deleteButton.getStyleClass().add("danger-button");
        deleteButton.setDefaultButton(false);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setDefaultButton(true);
        BooleanBinding blocked = backup.selectedProperty().not();
        if (everything) {
            blocked = blocked.or(entire.selectedProperty().not());
        }
        deleteButton.disableProperty().bind(blocked);
        dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        return dialog.showAndWait().filter(b -> b == deleteType).isPresent();
    }

    /** Explains how to add a language, and can write a template file. */
    static void addLanguage(Window owner, Path folder) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle(t("language.add.title"));
        alert.setHeaderText(t("language.add.header"));
        Label text = new Label(t("language.add.text", folder));
        text.setWrapText(true);
        text.setPrefWidth(500);
        text.setMinHeight(Region.USE_PREF_SIZE);
        alert.getDialogPane().setContent(text);
        ButtonType create = new ButtonType(t("language.add.create"), ButtonBar.ButtonData.LEFT);
        alert.getButtonTypes().setAll(create, ButtonType.CLOSE);
        if (alert.showAndWait().filter(b -> b == create).isPresent()) {
            try (InputStream in = Dialogs.class.getResourceAsStream("/i18n/messages.properties")) {
                Files.createDirectories(folder);
                Path template = folder.resolve("template_messages.properties");
                Files.copy(in, template, StandardCopyOption.REPLACE_EXISTING);
                info(owner, t("language.add.title"), t("language.add.created", template));
            } catch (IOException | NullPointerException e) {
                error(owner, t("error.title"), String.valueOf(e.getMessage()));
            }
        }
    }

    /** Shows a short information message. */
    static void info(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Label text = new Label(message);
        text.setWrapText(true);
        text.setPrefWidth(460);
        text.setMinHeight(Region.USE_PREF_SIZE);
        alert.getDialogPane().setContent(text);
        alert.showAndWait();
    }

    private static Label styled(String text, String styleClass) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setMinHeight(Region.USE_PREF_SIZE); // wrap instead of cutting the text in dialogs
        l.getStyleClass().add(styleClass);
        return l;
    }

    /** Shows an error message. */
    static void error(Window owner, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle(t("error.title"));
        alert.setHeaderText(header);
        Label text = new Label(message);
        text.setWrapText(true);
        text.setPrefWidth(460);
        text.setMinHeight(Region.USE_PREF_SIZE);
        alert.getDialogPane().setContent(text);
        alert.showAndWait();
    }

    private static Label value(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("value");
        return l;
    }

    private static TextField intField() {
        TextField f = new TextField();
        f.setPrefColumnCount(7);
        return f;
    }

    static Optional<Integer> parse(TextField f) {
        String s = f.getText().trim().replace(" ", "").replace(" ", "");
        if (!s.matches("-?\\d{1,7}")) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(s));
    }

    /** The chunks covering the in-game rectangle typed in the four fields. */
    static Optional<ChunkBounds> parseRect(TextField x1, TextField y1, TextField x2, TextField y2) {
        Optional<Integer> a = parse(x1);
        Optional<Integer> b = parse(y1);
        Optional<Integer> c = parse(x2);
        Optional<Integer> d = parse(y2);
        if (a.isEmpty() || b.isEmpty() || c.isEmpty() || d.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ChunkBounds.of(ChunkPos.ofSquare(a.get(), b.get()), ChunkPos.ofSquare(c.get(), d.get())));
    }
}
