# ZomboidChunkReset

A desktop tool, in the spirit of MCASelector for Minecraft, to reset chunks of a
**Project Zomboid Build 42** save, so that the game generates them again (for example after adding mods
that bring new buildings, loot or map content), while protecting the areas you choose, such as your
base. Works with single-player saves and dedicated-server saves. Windows and Linux.

## ⚠️ Safety

**This program deletes files from your save, and deleted files cannot be recovered. Always work on a
copy of the save, or make a backup first.** Close the game, or stop the server, before editing its save.

The program never makes backups itself. Instead it reminds you to work on a copy at every start, asks
for an explicit confirmation (with a box to tick) before every deletion, warns when Project Zomboid
seems to be running, never modifies `players.db`, and leaves the save consistent even if a deletion is
cancelled.

## Download

Get the latest version from the [Releases](../../releases) page:

- **Windows**: `ZomboidChunkReset-<version>-windows-x64.zip`. Extract it and run `ZomboidChunkReset.exe`.
  Java is included; nothing to install. The program is not signed: if SmartScreen warns, choose
  *More info* > *Run anyway*.
- **Linux**: `ZomboidChunkReset-<version>-x86_64.AppImage`. Make it executable (`chmod +x`) and run it.

## Quick start

1. **File > Open save…** and choose one save folder, for example
   `Zomboid/Saves/Multiplayer/<server name>` (or drop the folder on the window).
2. Select your base on the map (left drag), or with **Edit > Select by coordinates…** using in-game
   coordinates.
3. Choose **Keep the selected chunks, delete all the others**. The map turns red where chunks will be
   deleted and green where they are protected.
4. Check the preview, and **Simulate** if you want the exact list of what would be deleted.
5. **Delete…**, confirm, and let the program scan the save again.

## Features

- Chunk map with zoom, panning, cell borders, players, and coordinates under the mouse (chunk, cell,
  in-game X/Y).
- Selection with the mouse (rectangles, whole cells, deselection), by in-game coordinates, select all,
  invert, clear, undo/redo.
- Delete the selection, or keep it and delete everything else.
- Optional reset of zombie and animal populations, iso regions and vehicles, following
  [rules verified on real saves](docs/DELETION_RULES.md).
- Live preview, simulation, confirmation, progress with cancellation.
- Export and import of selections as JSON, to reuse protected areas from one save to another.
- English and French interface; other languages can be added as simple text files.

## Documentation

- [User guide](docs/USER_GUIDE.md)
- [Deletion rules](docs/DELETION_RULES.md)
- [Save format, as verified on real files](docs/SAVE_FORMAT.md)
- [Development: building, tests, packaging, CI, releases](docs/DEVELOPMENT.md)
- [Changelog](CHANGELOG.md)

## Building from source

Requires JDK 21; everything else is downloaded by Gradle.

```
./gradlew test               # tests (gradlew.bat on Windows)
./gradlew :app:run           # run from source
./gradlew :app:packageWindows   # Windows zip (on Windows)
./gradlew :app:packageLinux -Pappimagetool=/path/to/appimagetool   # AppImage (on Linux)
```

GitHub Actions builds both packages on every push and attaches them to a draft release for tags
`v*`. See [DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Known limitations

- Build 42 saves only (verified on world version 249).
- Global objects (crops, traps, campfires…), basements and safehouse/faction records are stored in
  global files that are not modified: entries in reset areas may remain.
- Running-game detection is a heuristic and cannot see a server running on another machine.

## License

[MIT](LICENSE)
