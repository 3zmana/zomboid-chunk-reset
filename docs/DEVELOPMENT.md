# Development

## Project layout

```
core/   save reading, deletion planning and execution — no user-interface code
  model/      chunk, cell and square coordinates
  format/     chunk headers, RegionHeader.bin, iso-region and per-cell file names
  db/         players.db (read-only) and vehicles.db
  save/       SaveScanner -> SaveIndex, save kind detection, scan issues
  selection/  ChunkSelection (per-cell bitmaps), JSON selection files
  reset/      ResetPlanner (pure computation) and ResetExecutor (simulate / apply)
  safety/     running-game detection
  json/       a small JSON reader/writer
  terrain/    read-only chunk content decoder, tile table, top view of chunks for the map
  src/testFixtures/  FakeSave, FakeChunk: synthetic saves and chunk files for tests (shared with app)
  src/tools/  TileTableGenerator (development only, see "Tile table")
app/    JavaFX interface
  map/        Viewport, MapModel, MapRenderer (pure, tested) and the MapView canvas
  ui/         main window, side panel, dialogs, progress window
  i18n/       language files loader; resources in src/main/resources/i18n/
  settings/   user settings file
  packaging/  icons, AppImage files
docs/   documentation (save format, deletion rules, user guide)
```

Requirements: JDK 21. Gradle and every library (JavaFX included) are downloaded by the Gradle wrapper.

```
./gradlew test          # all tests (gradlew.bat on Windows)
./gradlew :app:run      # start the application from source
```

## Tests

- Unit tests run on synthetic saves built by `FakeSave` and synthetic chunk files built by `FakeChunk`
  (layouts copied from real saves, invented data).
- `RealSaveIntegrationTest`, `RealSaveResetTest` and `RealSaveTerrainTest` run on real saves when
  `ZCR_REAL_SAVES` lists save folders (`;`-separated on Windows, `:` elsewhere). The first only reads
  and checks every format assumption; the second resets temporary copies and checks the result; the
  third decodes every chunk and checks that decoding ends at the end of each file and that ground tiles
  are known floors. All fail if the given saves are modified. Never commit saves: they contain player
  names.
- `app/src/test/java/.../tools/UiScreenshotTool.java` drives the real window with the system mouse and
  writes screenshots. It ends with a real deletion: run it on a copy of a save only. It needs a display
  (for example `xvfb-run` on Linux).

## Tile table

The map colours come from `core/src/main/resources/zomboidchunkreset/core/terrain/tile-table.bin`, a
small table (sprite id → kind of tile) derived from the game's tile definition files. It contains only
numbers. To rebuild it after a game update, from a Project Zomboid installation (never commit game
files):

```
./gradlew :core:generateTileTable -PpzMedia="<Project Zomboid>/media"
```

The id scheme and the classification rules are in `TileTableBuilder` and `TileClassifier`, and are
documented in [SAVE_FORMAT.md](SAVE_FORMAT.md). Run `RealSaveTerrainTest` on real saves afterwards.

## Packaging

The application is packaged with the JDK's own tools, without Gradle plugins:

| Task | Result |
|---|---|
| `:app:jlinkRuntime` | a minimal Java runtime (`app/build/jpackage/runtime`) |
| `:app:jpackageImage` | the self-contained application folder (`app/build/jpackage/image/ZomboidChunkReset`) |
| `:app:packageWindows` | `app/build/distributions/ZomboidChunkReset-<version>-windows-x64.zip` (on Windows) |
| `:app:packageLinux` | `app/build/distributions/ZomboidChunkReset-<version>-x86_64.AppImage` (on Linux) |

Each package must be built on its own system (JavaFX and jpackage are platform-specific).
`packageLinux` needs [appimagetool](https://github.com/AppImage/appimagetool): pass its path with
`-Pappimagetool=/path/to/appimagetool` or the `APPIMAGETOOL` environment variable.

The bundled runtime contains `java.base`, `java.desktop`, `java.logging`, `java.naming`, `java.sql`,
`java.xml`, `jdk.unsupported`, `jdk.localedata` (number formats of other languages) and `jdk.charsets`.
`jdeps` reports `java.base`, `java.desktop`, `java.sql` and `jdk.unsupported` as the strict minimum.

## Continuous integration

`.github/workflows/build.yml` runs on every push to `main`, on pull requests and on tags:

1. Windows (`windows-latest`) and Linux (`ubuntu-22.04`, for a widely compatible AppImage) run the tests;
2. each builds its package and uploads it as a workflow artifact (`ZomboidChunkReset-Windows`,
   `ZomboidChunkReset-Linux`), downloadable from the run's page;
3. on a tag starting with `v`, a **draft** GitHub release is created with both packages attached.

## Releasing

1. Set the version in `gradle.properties` (plain numbers, for example `1.0.0`) and update `CHANGELOG.md`.
2. Commit, then tag and push: `git tag v1.0.0 && git push origin v1.0.0`.
3. When the workflow has finished, review the draft release on GitHub and publish it.

## Translations

Built-in languages are listed in `app/src/main/resources/i18n/languages.properties`; each has a
`messages_<code>.properties` file (UTF-8). `I18nTest` fails if a string used by the code is missing in
English, or if a translation lacks a key or changes the `{0}` placeholders.
