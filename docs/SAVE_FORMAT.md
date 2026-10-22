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

The file does **not** contain its own coordinates: the path is the only source. Deletion always
removes whole files. The rest of the file is only **read** to draw the map (next section).

## Chunk content and sprite ids — verified, read only

Used only to draw the map; nothing here is ever written. The layout follows the description of the
[pzdataspec](https://github.com/cff29546/pzdataspec) project (MIT licence, version-249 description),
then was checked on two real saves (one server, one single-player, 57 030 chunk files).

Verified on those files:

- After the header come blending and attachment state, levels, floor blood, then the **64 square
  columns** of the chunk. Column `i` is at local `x = i / 8`, `y = i % 8`. Each column starts with a
  64-bit mask of the levels present (bit `level + 32`), then one record per level, lowest first.
- Each square record holds erosion data, a flag byte, its objects, optional extra data (dead bodies,
  table, trap position) and a visibility byte. Objects have no size prefix: every object type must be
  decoded to reach the next square. Items inside containers and entity components do have size
  prefixes and are skipped.
- After the squares: erosion state of the chunk, generators, vehicles (none in the reference files),
  loot respawn hour and spawned rooms. **Decoding ends exactly at the last byte of all 57 030 files.**
- Hutch: the integer before the animal count is the size of the animal block, count included
  (one occurrence).
- The first object of each ground square is a floor tile in 99.99 % of 3.6 million squares.
- Roofs are stored at their real square on upper levels (no visual offset): about 84 % of roof squares lie
  over a building floor, the rest being eaves.

**Sprite ids.** Each object stores the id of its tile. Ids are not listed in the save
(`WorldDictionary` holds no sprite table in the reference saves); they follow from the game's
definition files (`media/*.tiles`). Tile `i` of the tilesheet numbered `n` has the id:

| Definition file | Id |
|---|---|
| `newtiledefinitions.tiles` | `(n − 1) × 1024 + i` |
| `tiledefinitions_erosion.tiles` | `1048576 + (n − 1) × 512 + i` |
| `jumbo_trees_big.tiles` | `2097152 + (n − 1) × 512 + i` |
| `jumbo_trees.tiles` | `2621440 + (n − 1) × 512 + i` |

Every sprite id found in the reference saves resolves with this scheme (5.6 million objects), and tree
objects always resolve to tree tilesheets.

`.tiles` layout (little-endian): `tdef`, int version, int tilesheet count; per tilesheet a name and an
image name (each ended by `\n`), int columns, rows, tilesheet number, tile count; per tile an int
property count and `key\n value\n` pairs. All bytes of the seven vanilla files are accounted for.

Not verified / not supported:

- Ids of `tiledefinitions_overlays.tiles` and `tiledefinitions_b42chunkcaching.tiles` (never seen in the
  reference saves), and of tiles added by mods (each mod numbers its own files).
- `tiledefinitions_noiseworks.patch.tiles` gives partial property lists; the program merges them into
  the original properties (merging or ignoring the patch gives the same map colours).
- Object types absent from the reference saves are decoded from the description only. Characters,
  animals and vehicles stored inside a chunk are not supported: decoding of that chunk stops there.
- Other world versions: a chunk is used only if its decoding ends exactly at the end of the file.

The application ships a small table (sprite id → kind of tile, about 3 KB) derived once from the
vanilla `.tiles` files; no tile name and no game file is included. See
[DEVELOPMENT.md](DEVELOPMENT.md#tile-table) to rebuild it.

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
