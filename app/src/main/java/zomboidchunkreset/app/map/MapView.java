package zomboidchunkreset.app.map;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import zomboidchunkreset.app.model.SelectionState;
import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.model.WorldGeometry;
import zomboidchunkreset.core.reset.ResetMode;

/**
 * The interactive chunk map.
 *
 * <ul>
 *   <li>left click / drag: select (rectangle);</li>
 *   <li>Shift + left: deselect;</li>
 *   <li>Ctrl (Cmd on macOS) + left: whole cells;</li>
 *   <li>right or middle drag: move the map; wheel: zoom around the cursor.</li>
 * </ul>
 */
public final class MapView extends Region {

    private static final double ZOOM_STEP = 1.25;

    private final Canvas canvas = new Canvas();
    private final Viewport viewport = new Viewport();
    private final SelectionState selection;
    private final ObjectProperty<ResetMode> mode = new SimpleObjectProperty<>(ResetMode.DELETE_SELECTED);
    private final BooleanProperty showTerrain = new SimpleBooleanProperty(true);
    private final ReadOnlyObjectWrapper<ChunkPos> hovered = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyDoubleWrapper zoom = new ReadOnlyDoubleWrapper(viewport.scale());

    private MapModel model;
    private WritableImage image;
    private int[] buffer;
    private boolean redrawPending;
    private boolean fitPending;

    private SelectionGesture gesture;
    private boolean panning;
    private double lastX;
    private double lastY;

    private Consumer<GraphicsContext> emptyPainter = gc -> { };

    public MapView(SelectionState selection) {
        this.selection = selection;
        getChildren().add(canvas);
        setFocusTraversable(true);
        setMinSize(200, 200);
        selection.addListener(this::requestRedraw);
        mode.addListener((obs, a, b) -> requestRedraw());
        showTerrain.addListener((obs, a, b) -> requestRedraw());

        setOnMousePressed(this::onPressed);
        setOnMouseDragged(this::onDragged);
        setOnMouseReleased(this::onReleased);
        setOnMouseMoved(e -> setHover(e.getX(), e.getY()));
        setOnMouseExited(e -> {
            if (gesture == null && !panning) {
                hovered.set(null);
                requestRedraw();
            }
        });
        setOnScroll(this::onScroll);
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && gesture != null) {
                gesture = null;
                requestRedraw();
                e.consume();
            }
        });
    }

    // ------------------------------------------------------------------ public API

    /** Shows a save (or nothing, with {@code null}) and fits it in the view. */
    public void setModel(MapModel newModel) {
        this.model = newModel;
        gesture = null;
        hovered.set(null);
        setCursor(newModel == null ? Cursor.DEFAULT : Cursor.CROSSHAIR);
        fitPending = true;
        requestLayout();
        requestRedraw();
    }

    public MapModel model() {
        return model;
    }

    /** Whether the map colours chunks for "delete selected" or "keep selected". */
    public ObjectProperty<ResetMode> modeProperty() {
        return mode;
    }

    /** Whether the chunks' top views (terrain, roofs) are drawn where known. */
    public BooleanProperty showTerrainProperty() {
        return showTerrain;
    }

    /** The chunk under the mouse, or null. */
    public ReadOnlyObjectProperty<ChunkPos> hoveredProperty() {
        return hovered.getReadOnlyProperty();
    }

    /** Pixels per chunk. */
    public ReadOnlyDoubleProperty zoomProperty() {
        return zoom.getReadOnlyProperty();
    }

    /** Draws something when no save is open. */
    public void setEmptyPainter(Consumer<GraphicsContext> painter) {
        this.emptyPainter = painter;
        requestRedraw();
    }

    public void zoomIn() {
        zoomBy(ZOOM_STEP, getWidth() / 2, getHeight() / 2);
    }

    public void zoomOut() {
        zoomBy(1 / ZOOM_STEP, getWidth() / 2, getHeight() / 2);
    }

    /** Fits the saved chunks in the view. */
    public void fit() {
        if (model != null && model.dataBounds().isPresent() && getWidth() > 0 && getHeight() > 0) {
            viewport.fit(model.dataBounds().get(), getWidth(), getHeight(), 24);
            zoom.set(viewport.scale());
            requestRedraw();
        }
    }

    /** Centres the view on an in-game position (squares), zooming in to at least 8 pixels per chunk. */
    public void centerOnSquare(double squareX, double squareY) {
        if (viewport.scale() < 8) {
            viewport.setScale(8, getWidth(), getHeight());
        }
        viewport.centerOn(squareX / WorldGeometry.CHUNK_SIZE_IN_SQUARES, squareY / WorldGeometry.CHUNK_SIZE_IN_SQUARES,
                getWidth(), getHeight());
        zoom.set(viewport.scale());
        requestRedraw();
    }

    /** Position in this view of a (fractional) chunk coordinate. */
    public Point2D toLocal(double chunkX, double chunkY) {
        return new Point2D(viewport.screenX(chunkX), viewport.screenY(chunkY));
    }

    /** Schedules a repaint (coalesced). */
    public void requestRedraw() {
        if (!redrawPending) {
            redrawPending = true;
            Platform.runLater(this::redraw);
        }
    }

    // ------------------------------------------------------------------ layout & painting

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        if (fitPending && getWidth() > 0 && getHeight() > 0) {
            fitPending = false;
            fit();
        }
        requestRedraw();
    }

    private double pixelRatio() {
        if (getScene() != null && getScene().getWindow() != null) {
            return Math.max(1, getScene().getWindow().getOutputScaleX());
        }
        return 1;
    }

    private void redraw() {
        redrawPending = false;
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.web(Palette.css(Palette.OUTSIDE)));
        gc.fillRect(0, 0, w, h);
        if (model == null || model.isEmpty()) {
            emptyPainter.accept(gc);
            return;
        }
        double ratio = pixelRatio();
        int pw = (int) Math.ceil(w * ratio);
        int ph = (int) Math.ceil(h * ratio);
        if (image == null || (int) image.getWidth() != pw || (int) image.getHeight() != ph) {
            image = new WritableImage(pw, ph);
            buffer = new int[pw * ph];
        }
        ResetMode currentMode = mode.get();
        MapRenderer.render(buffer, pw, ph, ratio, viewport, model, selection.selection(), currentMode,
                showTerrain.get());
        image.getPixelWriter().setPixels(0, 0, pw, ph, PixelFormat.getIntArgbPreInstance(), buffer, 0, pw);
        gc.drawImage(image, 0, 0, pw / ratio, ph / ratio);

        drawGrid(gc, w, h, ratio);
        drawPlayers(gc);
        drawHover(gc);
        drawGesture(gc);
    }

    private void drawGrid(GraphicsContext gc, double w, double h, double ratio) {
        ChunkBounds region = model.region().orElseThrow();
        double s = viewport.scale();
        double left = Math.max(0, viewport.screenX(region.minX()));
        double right = Math.min(w, viewport.screenX(region.maxX() + 1));
        double top = Math.max(0, viewport.screenY(region.minY()));
        double bottom = Math.min(h, viewport.screenY(region.maxY() + 1));
        if (left >= right || top >= bottom) {
            return;
        }
        gc.setLineWidth(1 / ratio);
        gc.setLineCap(StrokeLineCap.BUTT);
        // Chunk lines only when chunks are big enough; later with terrain, whose detail they would hide.
        if (s >= (showTerrain.get() ? 24 : 10)) {
            gc.setStroke(Color.web(Palette.CHUNK_LINE));
            strokeLines(gc, 1, left, right, top, bottom, ratio);
        }
        int cell = WorldGeometry.CELL_SIZE_IN_CHUNKS;
        if (s * cell >= 6) {
            gc.setStroke(Color.web(Palette.CELL_LINE));
            strokeLines(gc, cell, left, right, top, bottom, ratio);
        }
    }

    private void strokeLines(GraphicsContext gc, int every, double left, double right, double top, double bottom,
                             double ratio) {
        int firstX = (int) Math.ceil(viewport.chunkX(left) / every) * every;
        for (int x = firstX; viewport.screenX(x) <= right; x += every) {
            double sx = snap(viewport.screenX(x), ratio);
            gc.strokeLine(sx, top, sx, bottom);
        }
        int firstY = (int) Math.ceil(viewport.chunkY(top) / every) * every;
        for (int y = firstY; viewport.screenY(y) <= bottom; y += every) {
            double sy = snap(viewport.screenY(y), ratio);
            gc.strokeLine(left, sy, right, sy);
        }
    }

    private static double snap(double v, double ratio) {
        return (Math.floor(v * ratio) + 0.5) / ratio;
    }

    private void drawPlayers(GraphicsContext gc) {
        gc.setFont(Font.font(null, FontWeight.BOLD, 12));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);
        // Players closer than about two marker widths share one marker and one label.
        List<double[]> positions = new ArrayList<>();
        List<List<String>> names = new ArrayList<>();
        for (MapModel.PlayerMarker p : model.players()) {
            double x = viewport.screenX(p.chunkX());
            double y = viewport.screenY(p.chunkY());
            if (x < -200 || y < -50 || x > getWidth() + 50 || y > getHeight() + 50) {
                continue;
            }
            int group = -1;
            for (int i = 0; i < positions.size(); i++) {
                if (Math.hypot(positions.get(i)[0] - x, positions.get(i)[1] - y) < 28) {
                    group = i;
                    break;
                }
            }
            if (group < 0) {
                positions.add(new double[] {x, y});
                names.add(new ArrayList<>());
                group = positions.size() - 1;
            }
            names.get(group).add(p.name());
        }
        List<double[]> labelBoxes = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            double x = positions.get(i)[0];
            double y = positions.get(i)[1];
            gc.setFill(Color.web(Palette.PLAYER));
            gc.setStroke(Color.web(Palette.PLAYER_OUTLINE));
            gc.setLineWidth(2);
            gc.strokeOval(x - 5, y - 5, 10, 10);
            gc.fillOval(x - 5, y - 5, 10, 10);
            if (viewport.scale() >= 3) {
                List<String> n = names.get(i);
                String label = n.size() <= 3 ? String.join(", ", n)
                        : String.join(", ", n.subList(0, 2)) + " +" + (n.size() - 2);
                // Move the label down while it overlaps an earlier one (approximate text width).
                double width = label.length() * 7.5;
                double ly = y;
                boolean moved = true;
                while (moved) {
                    moved = false;
                    for (double[] b : labelBoxes) {
                        if (x + 9 < b[0] + b[2] && x + 9 + width > b[0] && ly - 8 < b[1] + 16 && ly + 8 > b[1]) {
                            ly = b[1] + 16 + 8;
                            moved = true;
                        }
                    }
                }
                labelBoxes.add(new double[] {x + 9, ly - 8, width});
                gc.setLineWidth(3);
                gc.strokeText(label, x + 9, ly);
                gc.fillText(label, x + 9, ly);
            }
        }
    }

    private void drawHover(GraphicsContext gc) {
        ChunkPos h = hovered.get();
        if (h == null || gesture != null || panning || viewport.scale() < 4) {
            return;
        }
        gc.setStroke(Color.web(Palette.HOVER));
        gc.setLineWidth(1.5);
        gc.strokeRect(viewport.screenX(h.x()), viewport.screenY(h.y()), viewport.scale(), viewport.scale());
    }

    private void drawGesture(GraphicsContext gc) {
        if (gesture == null) {
            return;
        }
        ChunkBounds r = gesture.rectangle();
        double x = viewport.screenX(r.minX());
        double y = viewport.screenY(r.minY());
        double w = r.width() * viewport.scale();
        double h = r.height() * viewport.scale();
        Color c = Color.web(gesture.kind() == SelectionGesture.Kind.ADD ? Palette.DRAG_ADD : Palette.DRAG_REMOVE);
        gc.setFill(c.deriveColor(0, 1, 1, 0.22));
        gc.fillRect(x, y, Math.max(w, 1), Math.max(h, 1));
        gc.setStroke(c);
        gc.setLineWidth(2);
        gc.setLineDashes(6, 4);
        gc.strokeRect(x, y, Math.max(w, 1), Math.max(h, 1));
        gc.setLineDashes((double[]) null);
    }

    // ------------------------------------------------------------------ mouse

    private void onPressed(MouseEvent e) {
        requestFocus();
        if (model == null || model.isEmpty()) {
            return;
        }
        if (e.getButton() == MouseButton.PRIMARY) {
            SelectionGesture.Kind kind = e.isShiftDown() ? SelectionGesture.Kind.REMOVE : SelectionGesture.Kind.ADD;
            gesture = new SelectionGesture(viewport.chunkAt(e.getX(), e.getY()), kind, e.isShortcutDown());
            requestRedraw();
        } else if (e.getButton() == MouseButton.SECONDARY || e.getButton() == MouseButton.MIDDLE) {
            panning = true;
            lastX = e.getX();
            lastY = e.getY();
            setCursor(Cursor.CLOSED_HAND);
        }
    }

    private void onDragged(MouseEvent e) {
        if (panning) {
            viewport.panBy(e.getX() - lastX, e.getY() - lastY);
            lastX = e.getX();
            lastY = e.getY();
            requestRedraw();
        } else if (gesture != null) {
            gesture.update(viewport.chunkAt(e.getX(), e.getY()));
            requestRedraw();
        }
        setHover(e.getX(), e.getY());
    }

    private void onReleased(MouseEvent e) {
        if (panning && (e.getButton() == MouseButton.SECONDARY || e.getButton() == MouseButton.MIDDLE)) {
            panning = false;
            setCursor(Cursor.CROSSHAIR);
        } else if (gesture != null && e.getButton() == MouseButton.PRIMARY) {
            SelectionGesture g = gesture;
            gesture = null;
            g.commit(selection);
            requestRedraw();
        }
    }

    private void onScroll(ScrollEvent e) {
        if (model == null || model.isEmpty() || e.getDeltaY() == 0) {
            return;
        }
        zoomBy(Math.pow(ZOOM_STEP, e.getDeltaY() / 40.0), e.getX(), e.getY());
        setHover(e.getX(), e.getY());
        e.consume();
    }

    private void zoomBy(double factor, double x, double y) {
        viewport.zoomAt(factor, x, y);
        zoom.set(viewport.scale());
        requestRedraw();
    }

    private void setHover(double x, double y) {
        if (model == null || model.isEmpty()) {
            return;
        }
        ChunkPos pos = viewport.chunkAt(x, y);
        if (!pos.equals(hovered.get())) {
            hovered.set(pos);
            if (viewport.scale() >= 4) {
                requestRedraw();
            }
        }
    }
}
