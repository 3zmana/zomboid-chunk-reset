# Project Zomboid Build 42 save format (as verified)

This document describes only what ZomboidChunkReset relies on, and how each point was checked.
Everything marked **verified** was confirmed on real save files; everything marked **unknown** is
not used by the program, or is handled conservatively.

Reference material: one dedicated-server save (about 56,500 chunks, 83 cells) and one
single-player save (about 500 chunks, 4 cells), both Build 42 with world version 249.
The checks are automated in `core/src/test/java/.../RealSaveIntegrationTest.java` and can be re-run
on any save (see the README).

## Grid and coordinates — verified

| Unit   | Size                              | Coordinates used by                          |
|--------|-----------------------------------|----------------------------------------------|
| Square | 1 tile                            | in-game X/Y, players, vehicles (`x`, `y`)    |
| Chunk  | 8 × 8 squares                     | `map/`, `isoregiondata/`, `vehicles.wx/wy`, `localPlayers.wx/wy` |
| Cell   | 32 × 32 chunks = 256 × 256 squares | `zpop/`, `apop/`, `chunkdata/`, `metagrid/` |

- `chunk = floor(square / 8)`, `cell = floor(chunk / 32)`.
- Evidence: for every vehicle (548 + 4) and every single-player record, the stored chunk equals
  `floor(position / 8)` (none matches `/ 10`, the Build 41 chunk size), and the chunk file exists.
  No cell holds more than 1,024 chunk files, and the 83 cells that contain chunks are exactly the
  83 `metacell` files.
- No negative coordinate was seen. The code uses floor division so negatives would still be handled.

## Save folder location

- Single-player: `Zomboid/Saves/<game mode>/<save name>/` (the save name is a date by default).
- Server: `Zomboid/Saves/Multiplayer/<server name>/`.

The program does not rely on the path (saves are often copied before editing); it detects the kind
of save from its content (see below).

## `map/<chunkX>/<chunkY>.bin` — verified

One file per saved chunk. The folder name is the chunk X, the file name the chunk Y.
A chunk that was never loaded has no file; deleting a file makes the game generate that chunk again.

Header, big-endian:

| Offset | Size | Field           | Observed                                      |
|-------:|-----:|-----------------|-----------------------------------------------|
| 0      | 1    | flag            | always 0                                      |
| 1      | 4    | world version   | 249                                           |
| 5      | 4    | declared length | equals the file size in every file            |
| 9      | 8    | presumed checksum | not verified                                |

The file does **not** contain its own coordinates: the path is the only source. The rest of the
file is not decoded; the program only deletes whole files.

## `isoregiondata/` — verified layout

- `datachunk_<chunkX>_<chunkY>.bin`: same chunk coordinates as `map/`. Header (big-endian ints):
  length (= file size), version, chunk X, chunk Y. Every file name matched its header.
- `RegionHeader.bin`: `int version`, `int count`, then `count` pairs of 16-bit values listing the
  `datachunk` files. **Each pair is stored Y first, then X** (reverse of the file names). The file
  size is always `8 + 4 × count`. Header and files listed exactly the same chunks in both saves.
- Consequence: deleting a `datachunk` file requires rewriting `RegionHeader.bin` without that entry
  (see [DELETION_RULES.md](DELETION_RULES.md)).
- Unknown: why only some chunks have region data (347 of 56,512 and 63 of 518), and how the game
  reacts to a header entry whose file is missing.

## Per-cell files — names verified, content unknown

| Folder      | File name               | Notes                                                        |
|-------------|-------------------------|--------------------------------------------------------------|
| `zpop/`     | `zpop_<cellX>_<cellY>.bin` | zombie population; also exists for cells **without** chunks (server save) |
| `zpop/`     | `zpop_ids.bin`, `zpop_virtual.bin` | global, not per cell                               |
| `apop/`     | `apop_<cellX>_<cellY>.bin` | animal population (Build 42)                              |
| `chunkdata/`| `chunkdata_<cellX>_<cellY>.bin` | variable size; does **not** map one byte per existing chunk |
| `metagrid/` | `metacell_<cellX>_<cellY>.bin` | 1 byte each in the reference saves                    |

`chunkdata`, `apop` and `metacell` files were only found for cells that have saved chunks.
Since their content is not decoded, they can only be kept or deleted as a whole.

## `vehicles.db` — verified (SQLite)

`vehicles(id INTEGER PRIMARY KEY, wx INTEGER, wy INTEGER, x FLOAT, y FLOAT, inMeta BOOLEAN,
worldversion INTEGER, data BLOB)` with `wx = floor(x / 8)`, `wy = floor(y / 8)`.

## `players.db` — verified (SQLite), never modified

Both tables exist in both kinds of saves; only one holds rows:

- `localPlayers(id, name, wx, wy, x, y, z, worldversion, data, isDead)` — single-player;
  `wx`/`wy` are chunk coordinates.
- `networkPlayers(id, world, username, playerIndex, name, steamid, x, y, z, worldversion, data,
  isDead, splitUsername)` — server; `username` is the account, `name` the character; no chunk columns.

Empty `players.db-journal` / `vehicles.db-journal` files were present while the game was stopped:
their presence does **not** mean the game is running.

## Single-player vs server — verified differences

| Only in the server save                        | Only in the single-player save                     |
|------------------------------------------------|----------------------------------------------------|
| `map_visited_server/<account>.zip` (one per player) | `map_visited.bin`                             |
| `servermap_symbols.bin`                        | `map_symbols.bin`, `map_ver.bin`, `mods.txt`, `thumb.png`, `map.bin`, `metadata.bin`, `statistics.bin` |
| rows in `networkPlayers`                       | rows in `localPlayers`                             |

The chunk, region, cell and vehicle formats are identical. Not verified: the local cache kept by
multiplayer clients.

## Global files containing world positions — not modified

Samples of these files contain world coordinates, but their formats are not fully decoded:
`gos_farming.bin`, `gos_campfire.bin`, `gos_trap.bin`, `gos_feedingTrough.bin`,
`gos_rainbarrel.bin` (global object systems: crops, campfires, traps, troughs, rain barrels),
`map_basements.bin` (basement placements), `entity_data.bin`, `reanimated.bin`, `map_meta.bin`
(includes at least one record with an owner and members: a faction or safehouse).

Unknown: what the game does with entries of these files that point into regenerated chunks.
The program leaves them untouched and warns about them.

## Other files

`map_zone.bin`, `map_animals.bin`, `map_t.bin`, `map_worldgen.bin`, `erosion.ini`, `WorldDictionary*`,
`iTrack.bin`, `recorded_media.bin`, `radio/` and the others are global and are never touched.
