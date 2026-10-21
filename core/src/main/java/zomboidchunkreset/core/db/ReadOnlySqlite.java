package zomboidchunkreset.core.db;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.sqlite.SQLiteConfig;

/** Opens the game's SQLite databases strictly read-only. */
final class ReadOnlySqlite {

    private ReadOnlySqlite() {
    }

    static Connection open(Path file) throws IOException {
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        // The game may hold the database while running: wait a little instead of failing at once.
        config.setBusyTimeout(2000);
        try {
            return config.createConnection("jdbc:sqlite:" + file.toAbsolutePath());
        } catch (SQLException e) {
            throw new IOException("Cannot open " + file + " read-only: " + e.getMessage(), e);
        }
    }

    static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            st.setString(1, table);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }
}
