# Changelog

## 0.11.0

- **Help > About**: version and link to the source repository.
- New README presentation (icon, badges, download and documentation tables).

## 0.10.0 — terrain map

- The map shows the save seen from above, tile by tile: roads, water, grass, tall grass, trees, sand and
  dirt, walls and fences, and building roofs. It is read in the background from the chunk files only
  (read-only, no game files needed) and kept in memory, so scanning again after a deletion is quick.
- Chunks to be deleted are tinted red and protected chunks green; **View > Show terrain and roofs**
  (Ctrl+T) switches back to the plain view. New legend.
- Chunk content decoder and sprite id scheme documented in `docs/SAVE_FORMAT.md` and verified on real
  saves; credit to the pzdataspec project.

## 0.9.0 — first version

- Opens Project Zomboid Build 42 saves, single-player and dedicated server, and checks their
  consistency while scanning.
- Chunk map with zoom and panning, cell borders, players, and coordinates under the mouse.
- Selection with the mouse (rectangles, whole cells, deselection), by in-game coordinates, select all,
  invert, clear, undo and redo.
- Two actions: delete the selected chunks, or keep them and delete all the others.
- Optional reset of zombie and animal populations, iso regions and vehicles.
- Live preview, simulation, and deletion after an explicit confirmation, with progress and
  cancellation that leave the save consistent.
- Warnings: at start-up, before each deletion, when the game or a server seems to be running, and when
  players stand in the area to reset.
- Export and import of selections (JSON, with optional in-game coordinates).
- English and French interface; other languages can be added as files.
- Windows (zip with .exe) and Linux (AppImage) packages built by GitHub Actions.
