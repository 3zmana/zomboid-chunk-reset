# Deletion rules

How ZomboidChunkReset turns a selection into deletions. The rules follow from the verified save
format ([SAVE_FORMAT.md](SAVE_FORMAT.md)); where the game's behaviour is unknown, the program
chooses the option that keeps the save in a state already observed in real saves.

Code: `core/src/main/java/zomboidchunkreset/core/reset/` (`ResetPlanner` computes, `ResetExecutor`
simulates or applies).

## Reset area

- **Delete selected**: the reset area is the selection.
- **Keep selected**: the reset area is everything outside the selection (protect a base, reset the rest).

Chunks are always reset whole (8 × 8 squares). A selection may include positions without a chunk file.

## What is deleted

| Data | Rule | Option |
|------|------|--------|
| `map/<x>/<y>.bin` | every chunk file in the reset area; `map/<x>/` folders left empty are removed | always |
| `chunkdata_X_Y.bin`, `metacell_X_Y.bin` | only for **fully reset cells** (below) | always |
| `zpop_X_Y.bin` | only for fully reset cells | "zombie population", on by default |
| `apop_X_Y.bin` | only for fully reset cells | "animal population", on by default |
| `isoregiondata/datachunk_X_Y.bin` | every file in the reset area; `RegionHeader.bin` is rewritten without them | "iso regions", on by default |
| `vehicles.db` rows | every row whose stored chunk (`wx`, `wy`) is in the reset area | "vehicles", on by default |
| `players.db` | **never modified**; players standing in the reset area are listed as a warning | — |
| global files (`gos_*.bin`, `map_basements.bin`, `map_meta.bin`, ...) | **never modified**; a warning says they may keep entries for reset areas | — |

### Fully reset cell

Per-cell files cannot be edited (their content is not decoded), only kept or deleted whole.
A cell (32 × 32 chunks) is fully reset when **no chunk file of it remains** after the reset, and
either at least one of its chunk files is deleted, or the whole cell lies inside the reset area
(this covers cells that only have a `zpop` file).

A cell that keeps even one chunk keeps all its per-cell files. In practice: around a protected
base, within the same 256 × 256 squares, chunks are regenerated but that cell's zombie and animal
populations are not reset.

Why `chunkdata` and `metacell` are always deleted with their cell: in the reference saves, a cell
without chunk files never has these files, so deleting them restores a known state. Keeping
`zpop` is also a known state (server saves have `zpop` files for cells without chunks), hence the option.

## Running game

Before a deletion, and when a save is opened, the program looks for signs that Project Zomboid is
running (`core/.../safety/RunningGameDetector`): a process whose executable starts with
`ProjectZomboid`, a Java process whose executable lives in a folder containing `zomboid`, a process whose
arguments name the game's main classes or `-Dzomboid.` options, or one of the files the game rewrites
regularly (`map_t.bin`, `map_meta.bin`, `players.db`, `map_zone.bin`, `reanimated.bin`) modified in the
last two minutes. This only produces warnings: the user decides. The real run independently refuses
to start if the game holds `vehicles.db` (see below). The process names are heuristics still to be
confirmed on every kind of installation.

## Safety of a run

Before anything else, both the simulation and the real run check that:
- every planned file still exists (otherwise the save changed since the scan: scan again);
- `RegionHeader.bin` is unchanged since the scan;
- the folders and files to delete are writable (including the read-only attribute on Windows).

If a check fails, nothing is changed. The **simulation** stops there and reports what would be done.

The **real run** works in this order, so that a stop (error or cancellation) still leaves a
consistent save:

1. take the write lock on `vehicles.db` (if vehicles are to be deleted). If the game or the server
   holds the database, the run aborts with no change;
2. rewrite `RegionHeader.bin` atomically (temporary file, then rename), then delete the `datachunk` files;
3. delete the chunk files, then remove the `map/<x>/` folders left empty;
4. delete the per-cell files, but only for planned cells whose chunk files are all really gone;
5. delete the vehicle rows, but only of chunks that are really gone, then commit.

Steps 4 and 5 run even after a stop, but then only for chunks actually deleted (cells that never
had chunks are left for a later run). A stop can therefore leave fewer chunks deleted than planned,
but never per-cell files or vehicles that disagree with the chunks.

## Known unknowns

- How the game treats entries of the global files (crops, traps, troughs, campfires, basements,
  safehouses or factions) that point into regenerated chunks.
- Whether a `RegionHeader.bin` with zero entries (written when every region chunk is reset) is
  read like an absent one. The format allows it.
- Where a player whose chunk was reset reappears (the chunk is generated again around them).
