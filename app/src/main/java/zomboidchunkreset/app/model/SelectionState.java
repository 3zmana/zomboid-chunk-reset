package zomboidchunkreset.app.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

import zomboidchunkreset.core.selection.ChunkSelection;

/**
 * The current selection with undo/redo. Every change goes through {@link #edit} or
 * {@link #replace}, which record history and notify listeners. No JavaFX dependency.
 */
public final class SelectionState {

    /** Maximum number of undo steps kept. */
    public static final int HISTORY_LIMIT = 50;

    private ChunkSelection current = new ChunkSelection();
    private final Deque<ChunkSelection> undo = new ArrayDeque<>();
    private final Deque<ChunkSelection> redo = new ArrayDeque<>();
    private final List<Runnable> listeners = new ArrayList<>();

    /** The live selection. Do not modify it directly: use {@link #edit}. */
    public ChunkSelection selection() {
        return current;
    }

    /** A copy that can be handed to another thread. */
    public ChunkSelection snapshot() {
        return current.copy();
    }

    /** Applies a change; records an undo step only if the selection really changed. */
    public void edit(Consumer<ChunkSelection> change) {
        ChunkSelection before = current.copy();
        change.accept(current);
        if (!current.equals(before)) {
            push(before);
            fire();
        }
    }

    /** Replaces the whole selection (for example after an import). Undoable. */
    public void replace(ChunkSelection selection) {
        if (!selection.equals(current)) {
            push(current);
            current = selection.copy();
            fire();
        }
    }

    /** Starts over with an empty selection and no history (for example when another save is opened). */
    public void reset() {
        current = new ChunkSelection();
        undo.clear();
        redo.clear();
        fire();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void undo() {
        if (canUndo()) {
            redo.push(current);
            current = undo.pop();
            fire();
        }
    }

    public void redo() {
        if (canRedo()) {
            undo.push(current);
            current = redo.pop();
            fire();
        }
    }

    /** Called after every change, undo or redo. */
    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private void push(ChunkSelection before) {
        undo.push(before);
        while (undo.size() > HISTORY_LIMIT) {
            undo.removeLast();
        }
        redo.clear();
    }

    private void fire() {
        for (Runnable l : List.copyOf(listeners)) {
            l.run();
        }
    }
}
