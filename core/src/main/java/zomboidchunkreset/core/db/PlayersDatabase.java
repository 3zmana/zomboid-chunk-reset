package zomboidchunkreset.core.db;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import zomboidchunkreset.core.model.ChunkPos;

/**
 * Read-only access to {@code players.db}.
 *
 * <p>Verified layout: table {@code localPlayers} (single-player, with {@code wx}/{@code wy} chunk
 * columns) and table {@code networkPlayers} (multiplayer server, without chunk columns). Both
 * tables exist in both kinds of saves; only one of them holds rows.
 */
public final class PlayersDatabase {

    /** File name inside the save folder. */
    public static final String FILE_NAME = "players.db";

    private PlayersDatabase() {
    }

    /** Reads every player of both tables. */
    public static List<PlayerRecord> read(Path file) throws IOException {
        List<PlayerRecord> players = new ArrayList<>();
        try (Connection c = ReadOnlySqlite.open(file)) {
            if (ReadOnlySqlite.tableExists(c, "localPlayers")) {
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT name, wx, wy, x, y, z, isDead FROM localPlayers")) {
                    while (rs.next()) {
                        players.add(new PlayerRecord(PlayerRecord.Source.LOCAL, null, rs.getString(1),
                                rs.getDouble(4), rs.getDouble(5), rs.getDouble(6), rs.getBoolean(7),
                                new ChunkPos(rs.getInt(2), rs.getInt(3))));
                    }
                }
            }
            if (ReadOnlySqlite.tableExists(c, "networkPlayers")) {
                try (Statement st = c.createStatement();
                     ResultSet rs = st.executeQuery("SELECT username, name, x, y, z, isDead FROM networkPlayers")) {
                    while (rs.next()) {
                        players.add(new PlayerRecord(PlayerRecord.Source.NETWORK, rs.getString(1), rs.getString(2),
                                rs.getDouble(3), rs.getDouble(4), rs.getDouble(5), rs.getBoolean(6), null));
                    }
                }
            }
        } catch (SQLException e) {
            throw new IOException("Cannot read " + file + ": " + e.getMessage(), e);
        }
        return players;
    }
}
