# User guide

ZomboidChunkReset deletes chunks from a Project Zomboid **Build 42** save so that the game generates
them again, for example after adding mods that bring new buildings, loot or map content, while
keeping the areas you choose (your base). It works with single-player and dedicated-server saves.

## 1. Before you start

> ⚠️ **Work on a copy of your save, or back it up first.** Deleted files cannot be recovered and the
> program does not make backups itself.

- **Close the game, or stop the server**, before editing its save. A running game keeps writing its
  save and would undo or corrupt your changes.
- Saves are folders:
  - single-player: `Zomboid/Saves/<game mode>/<save name>/` (the name is a date by default);
  - server: `Zomboid/Saves/Multiplayer/<server name>/`.
  The `Zomboid` folder is in your user folder (`C:\Users\<you>\Zomboid` on Windows).
- To work on a copy: copy the whole save folder elsewhere, open the copy in this program, then put the
  edited copy back in place of the original (keep the original until you have checked the result in game).

## 2. Installing

**Windows**: download `ZomboidChunkReset-<version>-windows-x64.zip`, extract it anywhere, and run
`ZomboidChunkReset.exe` in the extracted folder. Nothing else is needed (Java is included). The program
is not signed, so Windows SmartScreen may warn about an unknown publisher: choose *More info*, then
*Run anyway*.

**Linux**: download `ZomboidChunkReset-<version>-x86_64.AppImage`, make it executable
(`chmod +x ZomboidChunkReset-*.AppImage`) and run it. If your system lacks FUSE, run it with
`--appimage-extract-and-run`.

A reminder to work on a copy is shown every time the program starts.

## 3. Opening a save

Use **File > Open save…** and choose the save folder itself (the one containing `map`, `players.db`…),
or drop the folder on the window. The program scans the save (a few seconds for tens of thousands of
chunks) without changing anything.

The **Save** section then shows the kind of save (single-player or server, detected from its content),
the number of chunks and their size, cells, players and vehicles. If the scan found inconsistencies, a
line with a **Details…** button lists them.

If Project Zomboid seems to be running, a red warning appears in this section (see §8).

## 4. The map

The map is made of **chunks** (8 × 8 tiles). Thick lines are **cell** borders (32 × 32 chunks,
256 × 256 tiles). Only chunks the game has saved are drawn; the dark checkerboard is land never
generated.

### Terrain view (default)

Right after opening a save, the program reads the content of every saved chunk in the background
(the status bar shows the progress) and draws it seen from above, one pixel per tile when zoomed in:
roads, water, grass, tall grass, trees, sand and dirt, walls and fences, and the **roofs** of buildings.
Upper floors and flat roofs are grey. This reading never changes anything in the save, and needs no
game files. Tiles added by mods are drawn in a neutral grey.

The colours also show what the current action would do:

| Look | Meaning |
|---|---|
| Natural colours | chunk kept |
| Tinted red | chunk that will be deleted |
| Tinted green | selected chunk protected ("keep" mode) |
| Plain slate blue | chunk whose content could not be read (yet) |
| Dark red / dark green | selected position without a saved chunk |

**View > Show terrain and roofs** (**Ctrl+T**) switches to the plain view.

### Plain view

Each chunk is one colour: slate blue when kept, red when it will be deleted, green when protected.
Lighter shades mean bigger chunk files, which makes built-up areas visible.

Yellow dots are players (with their names when zoomed in).

Navigation: **mouse wheel** to zoom around the cursor, **right or middle drag** to move, **Ctrl+0** to
fit the whole save, **Ctrl+G** to go to in-game coordinates.

The status bar shows what is under the mouse: chunk and cell coordinates, the in-game X/Y range of the
chunk (the same coordinates as the in-game map and admin tools), its file size or "never generated",
and players standing there.

## 5. Selecting

| Action | How |
|---|---|
| Select a chunk / a rectangle | left click / left drag |
| Deselect | Shift + left click or drag |
| Select whole cells | Ctrl + left click or drag (Cmd on macOS) |
| Select by in-game coordinates | **Edit > Select by coordinates…** (Ctrl+R) |
| Select all chunks / invert / clear | Ctrl+A / Ctrl+I / Ctrl+D |
| Undo / redo | Ctrl+Z / Ctrl+Y |

**Select by coordinates** takes two opposite corners in in-game coordinates (tiles) and selects every
chunk touching that rectangle; the dialog shows the resulting chunks before you confirm. Selections can
be saved and loaded (see §9).

## 6. Choosing the action

- **Delete the selected chunks**: only the selection is reset.
- **Keep the selected chunks, delete all the others**: protects the selection (your base) and resets
  everything else.

**Also reset** (all ticked by default, for the cleanest regeneration):

- **Zombie population** and **animal population**: these are stored per *cell*. They are reset only
  for cells that lose **all** their chunks. A cell that keeps even one chunk (for example the cell of
  your base) keeps its populations, while its other chunks are still regenerated.
- **Iso regions**: region data of the reset chunks; the region index (`RegionHeader.bin`) is updated.
- **Vehicles**: vehicles parked in reset chunks are removed from `vehicles.db`, so that they are not left
  floating over regenerated land or duplicated by new spawns.

Players (`players.db`) are never modified. Two technical per-cell files (`chunkdata`, `metacell`) are
always removed together with a fully reset cell. The detailed rules are in
[DELETION_RULES.md](DELETION_RULES.md).

## 7. Preview and simulation

The **Preview** in the side panel updates as you select: chunks and data deleted, chunks kept, fully
reset cells, per-cell files, iso-region files and vehicles. Warnings below it tell you, for example,
that players stand in the area to reset, or that the whole save would be deleted.

**Simulate (changes nothing)** runs every pre-check of a real deletion on the current selection and
shows exactly what would be deleted. Nothing is written.

## 8. Deleting

**Delete…** opens a confirmation window with:

- a red warning if Project Zomboid seems to be running (a process named `ProjectZomboid…`, a Java
  process started from a Project Zomboid folder, or a save written less than two minutes ago);
- the summary of what will be deleted, and the warnings;
- the reminder to work on a copy, and a box to tick ("I am working on a copy of this save, or I have a
  backup of it"). The **Delete** button stays disabled until it is ticked. When the whole save would be
  reset, a second box must be ticked too.

The deletion then runs with a progress bar. **Cancel** stops it: files already deleted stay deleted,
but the save is left consistent (per-cell files and vehicles always match the chunks actually deleted).
If the game holds the vehicle database, the deletion stops before changing anything.

A report follows, then the save is scanned again; your selection is kept.

## 9. After deleting: in game

- Deleted chunks are generated again **when someone comes near them**, with the mods currently active.
- Around a protected base, generated land meets the old land at chunk borders: seams (abrupt changes of
  terrain or vegetation) may be visible.
- Global objects (crops, traps, campfires, troughs, rain barrels), basements and safehouse or faction
  records live in global files that this program does not modify; entries pointing into reset areas may
  remain. Players' map knowledge is also kept, so reset areas still look explored on the in-game map.
- On a server, players may need to delete their local map cache to see regenerated areas correctly
  (not verified).

## 10. Selection files

**File > Export the selection…** saves the selection and the action mode to a JSON file;
**File > Import a selection…** loads one (replacing the current selection; Ctrl+Z undoes it). This is
handy to reuse your protected base on another copy of the same server. A file can also be written by
hand with in-game coordinates:

```json
{
  "format": "zomboid-chunk-reset-selection",
  "version": 1,
  "mode": "KEEP_SELECTED",
  "squares": [ { "x1": 12005, "y1": 2410, "x2": 12090, "y2": 2470 } ]
}
```

Exported files use `chunks` (chunk coordinates) instead of `squares`; both can be combined.

## 11. Languages

The interface is in English by default; French is included (**Language** menu, remembered). To add a
language, use **Language > Add a language…**: it shows the folder for language files and can write an
English template. Rename it `messages_<code>.properties` (for example `messages_de.properties`),
translate the values after the `=` signs, set `language.name`, and restart. Missing strings appear in
English.

Settings and language files are stored in `%APPDATA%\ZomboidChunkReset` (Windows),
`~/.config/zomboidchunkreset` (Linux) or `~/Library/Application Support/ZomboidChunkReset` (macOS).

## 12. Troubleshooting

| Problem | What to do |
|---|---|
| "This folder is not a Project Zomboid save" | Choose the folder of one save (it contains a `map` folder), not `Saves` or `Multiplayer`. |
| Red "Project Zomboid seems to be running" warning | Close the game or stop the server. The warning also appears if the save was written in the last two minutes; wait, then use **File > Scan again**. |
| "vehicles.db is locked or cannot be written" | The game or the server still uses the save: close it and try again. Nothing was changed. |
| "The save changed since it was scanned" | Something modified the save after it was opened: **File > Scan again**, then retry. |
| Scan problems listed under **Details…** | The save already had inconsistencies (for example from a manual edit). Review them before deleting. |
| Windows SmartScreen warning | The program is not signed: *More info* > *Run anyway*. |

## 13. Known limitations

- Only Build 42 saves are supported (verified on world version 249).
- Running-game detection is a heuristic, and cannot see a server on another machine.
- The game's handling of global-object entries in regenerated areas is not known (§9).
- On the map, tiles added by mods are grey, and a few rare objects (characters or animals stored in a
  chunk) stop the reading of that chunk: its remaining tiles take the chunk's most common colour.
