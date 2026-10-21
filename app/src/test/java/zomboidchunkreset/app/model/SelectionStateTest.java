package zomboidchunkreset.app.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import zomboidchunkreset.app.map.SelectionGesture;
import zomboidchunkreset.core.model.ChunkBounds;
import zomboidchunkreset.core.model.ChunkPos;
import zomboidchunkreset.core.selection.ChunkSelection;

class SelectionStateTest {

    @Test
    void undoAndRedo() {
        SelectionState s = new SelectionState();
        int[] notifications = {0};
        s.addListener(() -> notifications[0]++);
        assertFalse(s.canUndo());

        s.edit(sel -> sel.addRect(new ChunkBounds(0, 0, 1, 1)));
        s.edit(sel -> sel.add(new ChunkPos(5, 5)));
        assertEquals(5, s.selection().size());
        assertEquals(2, notifications[0]);

        s.undo();
        assertEquals(4, s.selection().size());
        assertTrue(s.canRedo());
        s.undo();
        assertTrue(s.selection().isEmpty());
        assertFalse(s.canUndo());
        s.redo();
        assertEquals(4, s.selection().size());

        // A new edit drops the redo history.
        s.edit(sel -> sel.add(new ChunkPos(9, 9)));
        assertFalse(s.canRedo());
        assertEquals(6, notifications[0]);
    }

    @Test
    void editsThatChangeNothingAreNotRecorded() {
        SelectionState s = new SelectionState();
        s.edit(sel -> sel.add(new ChunkPos(1, 1)));
        s.edit(sel -> sel.add(new ChunkPos(1, 1)));
        s.edit(sel -> sel.remove(new ChunkPos(2, 2)));
        s.undo();
        assertFalse(s.canUndo());
    }

    @Test
    void historyIsLimited() {
        SelectionState s = new SelectionState();
        for (int i = 0; i < SelectionState.HISTORY_LIMIT + 20; i++) {
            int x = i;
            s.edit(sel -> sel.add(new ChunkPos(x, 0)));
        }
        int undos = 0;
        while (s.canUndo()) {
            s.undo();
            undos++;
        }
        assertEquals(SelectionState.HISTORY_LIMIT, undos);
        assertEquals(20, s.selection().size());
    }

    @Test
    void replaceAndReset() {
        SelectionState s = new SelectionState();
        s.replace(ChunkSelection.of(List.of(new ChunkPos(3, 3))));
        assertEquals(1, s.selection().size());
        s.undo();
        assertTrue(s.selection().isEmpty());
        s.redo();
        s.reset();
        assertTrue(s.selection().isEmpty());
        assertFalse(s.canUndo());
        assertFalse(s.canRedo());
    }

    @Test
    void snapshotsAreIndependent() {
        SelectionState s = new SelectionState();
        s.edit(sel -> sel.add(new ChunkPos(1, 1)));
        ChunkSelection snap = s.snapshot();
        s.edit(sel -> sel.add(new ChunkPos(2, 2)));
        assertEquals(1, snap.size());
    }

    @Test
    void gestures() {
        SelectionState s = new SelectionState();
        SelectionGesture add = new SelectionGesture(new ChunkPos(10, 10), SelectionGesture.Kind.ADD, false);
        add.update(new ChunkPos(7, 12)); // dragging up-left works too
        assertEquals(new ChunkBounds(7, 10, 10, 12), add.rectangle());
        add.commit(s);
        assertEquals(12, s.selection().size());

        SelectionGesture remove = new SelectionGesture(new ChunkPos(8, 11), SelectionGesture.Kind.REMOVE, false);
        remove.commit(s); // a click
        assertEquals(11, s.selection().size());
        assertFalse(s.selection().contains(new ChunkPos(8, 11)));

        SelectionGesture cells = new SelectionGesture(new ChunkPos(40, -5), SelectionGesture.Kind.ADD, true);
        cells.update(new ChunkPos(70, 3));
        assertEquals(new ChunkBounds(32, -32, 95, 31), cells.rectangle());
    }
}
