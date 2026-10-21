package zomboidchunkreset.app.ui;

import static zomboidchunkreset.app.i18n.I18n.number;
import static zomboidchunkreset.app.i18n.I18n.t;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Modal progress window shown while files are deleted. It cannot be closed, only cancelled. */
final class ProgressWindow {

    private final Stage stage = new Stage();
    private final ProgressBar bar = new ProgressBar(0);
    private final Label text = new Label(t("progress.starting"));
    private final Button cancel = new Button(t("button.cancel"));
    private Runnable onCancel = () -> { };

    ProgressWindow(Window owner) {
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(t("progress.title"));
        stage.setResizable(false);
        stage.setOnCloseRequest(e -> e.consume());
        bar.setPrefWidth(380);
        Label header = new Label(t("progress.header"));
        header.getStyleClass().add("section-title");
        Label keepOpen = new Label(t("progress.keepOpen"));
        keepOpen.getStyleClass().add("muted");
        keepOpen.setWrapText(true);
        cancel.setOnAction(e -> {
            cancel.setDisable(true);
            cancel.setText(t("progress.cancelling"));
            onCancel.run();
        });
        HBox buttons = new HBox(cancel);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        VBox box = new VBox(10, header, bar, text, keepOpen, buttons);
        box.setPadding(new Insets(16));
        box.setPrefWidth(420);
        Scene scene = new Scene(box);
        scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        stage.setScene(scene);
    }

    void setOnCancel(Runnable action) {
        this.onCancel = action;
    }

    void update(int done, int total) {
        bar.setProgress(total == 0 ? 1 : (double) done / total);
        text.setText(t("progress.text", number(done), number(total)));
    }

    void show() {
        stage.show();
    }

    void close() {
        stage.setOnCloseRequest(null);
        stage.close();
    }
}
