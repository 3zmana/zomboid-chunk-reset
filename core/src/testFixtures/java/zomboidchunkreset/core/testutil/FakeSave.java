package zomboidchunkreset.core.testutil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import zomboidchunkreset.core.format.CellFileType;
import zomboidchunkreset.core.format.ChunkFileHeader;
import zomboidchunkreset.core.format.IsoRegionChunkFile;
import zomboidchunkreset.core.format.RegionHeader;
import zomboidchunkreset.core.model.CellPos;
import zomboidchunkreset.core.model.ChunkPos;

/**
 * Builds synthetic save folders that follow the verified Build 42 layout. Contains no real game
 * data: names and positions are invented.
 */
public final class FakeSave {

    /** World version found in the reference saves. */
    public static final int WORLD_VERSION = 249;

    /** Table definitions copied from the reference saves. */
    private static final String PLAYERS_SCHEMA = """
            CREATE TABLE localPlayers (id   INTEGER PRIMARY KEY NOT NULL,name STRING,wx    INTEGER,wy    INTEGER,\
            x    FLOAT,y    FLOAT,z    FLOAT,worldversion    INTEGER,data BLOB,isDead BOOLEAN);
            CREATE TABLE networkPlayers (id   INTEGER PRIMARY KEY NOT NULL,world TEXT,username TEXT,\
            playerIndex   INTEGER,name STRING,steamid STRING,x    FLOAT,y    FLOAT,z    FLOAT,worldversion    INTEGER,\
            data BLOB,isDead BOOLEAN,splitUsername STRING);
            CREATE INDEX inpusername ON networkPlayers (username);
            """;
    private static final String VEHICLES_SCHEMA = """
            CREATE TABLE vehicles (id   INTEGER PRIMARY KEY NOT NULL,wx    INTEGER,wy    INTEGER,x    FLOAT,\
            y    FLOAT,inMeta BOOLEAN NULL DEFAULT FALSE,worldversion    INTEGER,data BLOB);
            CREATE INDEX ivwx ON vehicles (wx);
            CREATE INDEX ivwy ON vehicles (wy);
            """;

    private final Path root;

    private FakeSave(Path root) {
        this.root = root;
    }

    /** Creates an empty save (with a {@code map/} folder) in {@code root}. */
    public static FakeSave create(Path root) {
        try {
            Files.createDirectories(root.resolve("map"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new FakeSave(root);
    }

    public Path root() {
        return root;
    }

    /** Adds a valid chunk file with {@code payload} bytes after the header. */
    public FakeSave chunk(int x, int y, int payload) {
        int size = ChunkFileHeader.SIZE + payload;
        byte[] content = new byte[size];
        byte[] header = new ChunkFileHeader(0, WORLD_VERSION, size, 0x1234_5678L).toBytes();
        System.arraycopy(header, 0, content, 0, header.length);
        return chunkRaw(x, y, content);
    }

    /** Adds a valid chunk file. */
    public FakeSave chunk(int x, int y) {
        return chunk(x, y, 32);
    }

    /** Adds valid chunks for every position of an inclusive rectangle. */
    public FakeSave chunks(int minX, int minY, int maxX, int maxY) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                chunk(x, y);
            }
        }
        return this;
    }

    /** Adds a chunk file with arbitrary content. */
    public FakeSave chunkRaw(int x, int y, byte[] content) {
        return file("map/" + x + "/" + y + ".bin", content);
    }

    /** Adds a per-cell file. */
    public FakeSave cellFile(CellFileType type, int cellX, int cellY) {
        return file(type.folder() + "/" + type.fileName(new CellPos(cellX, cellY)), new byte[] {1});
    }

    /** Adds a valid {@code datachunk} file. */
    public FakeSave isoChunk(int x, int y) {
        int size = 92;
        ByteBuffer b = ByteBuffer.allocate(size).putInt(size).putInt(WORLD_VERSION).putInt(x).putInt(y);
        return file("isoregiondata/" + IsoRegionChunkFile.fileName(new ChunkPos(x, y)), b.array());
    }

    /** Writes {@code RegionHeader.bin} listing the given chunks. */
    public FakeSave regionHeader(List<ChunkPos> chunks) {
        return file("isoregiondata/" + RegionHeader.FILE_NAME, new RegionHeader(WORLD_VERSION, chunks).toBytes());
    }

    /** Adds an arbitrary file. */
    public FakeSave file(String relative, byte[] content) {
        try {
            Path p = root.resolve(relative);
            Files.createDirectories(p.getParent());
            Files.write(p, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /** Creates an empty folder. */
    public FakeSave folder(String relative) {
        try {
            Files.createDirectories(root.resolve(relative));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    /** Creates {@code players.db} with one single-player character. */
    public FakeSave localPlayer(String characterName, double x, double y, int storedWx, int storedWy) {
        execute("players.db", PLAYERS_SCHEMA, "INSERT INTO localPlayers (name, wx, wy, x, y, z, worldversion, data, isDead)"
                + " VALUES (?, ?, ?, ?, ?, 0, 249, x'00', 0)", characterName, storedWx, storedWy, x, y);
        return this;
    }

    /** Adds a server player to {@code players.db}. */
    public FakeSave networkPlayer(String account, String characterName, double x, double y) {
        execute("players.db", PLAYERS_SCHEMA, "INSERT INTO networkPlayers (world, username, playerIndex, name, steamid,"
                + " x, y, z, worldversion, data, isDead, splitUsername) VALUES ('world', ?, 0, ?, '0', ?, ?, 0, 249,"
                + " x'00', 0, '')", account, characterName, x, y);
        return this;
    }

    /** Adds a vehicle to {@code vehicles.db}. */
    public FakeSave vehicle(int storedWx, int storedWy, double x, double y) {
        execute("vehicles.db", VEHICLES_SCHEMA, "INSERT INTO vehicles (wx, wy, x, y, inMeta, worldversion, data)"
                + " VALUES (?, ?, ?, ?, 0, 249, x'00')", storedWx, storedWy, x, y);
        return this;
    }

    private void execute(String db, String schema, String insert, Object... args) {
        Path file = root.resolve(db);
        boolean fresh = !Files.exists(file);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath())) {
            if (fresh) {
                try (Statement st = c.createStatement()) {
                    for (String sql : schema.split(";")) {
                        if (!sql.isBlank()) {
                            st.execute(sql);
                        }
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                for (int i = 0; i < args.length; i++) {
                    ps.setObject(i + 1, args[i]);
                }
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
