package zomboidchunkreset.core.db;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.sqlite.SQLiteConfig;

import zomboidchunkreset.core.model.ChunkPos;

/**
 * Read-only access to {@code vehicles.db}.
 *
 * <p>Verified layout: table {@code vehicles(id, wx, wy, x, y, inMeta, worldversion, data)} where
 * {@code wx}/{@code wy} are chunk coordinates ({@code floor(x / 8)}, {@code floor(y / 8)}).
 */
public final class VehiclesDatabase {

    /** File name inside the save folder. */
    public static final String FILE_NAME = "vehicles.db";

    private VehiclesDatabase() {
    }

    /**
     * A write transaction on {@code vehicles.db}, opened at the start of a reset so that the
     * database lock is held (and checked) before any file is deleted. Rolled back on close unless
     * {@link #commit()} was called.
     */
    public static final class Deletion implements AutoCloseable {

        private final Connection connection;
        private boolean committed;

        private Deletion(Connection connection) {
            this.connection = connection;
        }

        /**
         * Opens the database for writing and takes the write lock.
         *
         * @throws IOException if the database is locked (for example by a running game) or not writable
         */
        public static Deletion begin(Path file) throws IOException {
            SQLiteConfig config = new SQLiteConfig();
            config.setBusyTimeout(2000);
            config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
            Connection c = null;
            try {
                c = config.createConnection("jdbc:sqlite:" + file.toAbsolutePath());
                c.setAutoCommit(false);
                // Forces the write lock now rather than at the first real change.
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("DELETE FROM vehicles WHERE 0");
                }
                return new Deletion(c);
            } catch (SQLException e) {
                if (c != null) {
                    try {
                        c.close();
                    } catch (SQLException ignored) {
                        // already failing
                    }
                }
                throw new IOException("Cannot lock " + file + " for writing: " + e.getMessage(), e);
            }
        }

        /**
         * Deletes the given rows (matched on id and stored chunk, so that a row changed since the
         * scan is left alone).
         *
         * @return number of rows deleted
         */
        public int delete(Collection<VehicleRecord> vehicles) throws IOException {
            int deleted = 0;
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM vehicles WHERE id = ? AND wx = ? AND wy = ?")) {
                for (VehicleRecord v : vehicles) {
                    ps.setLong(1, v.id());
                    ps.setInt(2, v.storedChunk().x());
                    ps.setInt(3, v.storedChunk().y());
                    deleted += ps.executeUpdate();
                }
            } catch (SQLException e) {
                throw new IOException("Cannot delete vehicles: " + e.getMessage(), e);
            }
            return deleted;
        }

        /** Makes the deletions permanent. */
        public void commit() throws IOException {
            try {
                connection.commit();
                committed = true;
            } catch (SQLException e) {
                throw new IOException("Cannot commit vehicle deletions: " + e.getMessage(), e);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                if (!committed) {
                    connection.rollback();
                }
                connection.close();
            } catch (SQLException e) {
                throw new IOException("Cannot close vehicles.db: " + e.getMessage(), e);
            }
        }
    }

    /** Reads every vehicle position. */
    public static List<VehicleRecord> read(Path file) throws IOException {
        List<VehicleRecord> vehicles = new ArrayList<>();
        try (Connection c = ReadOnlySqlite.open(file)) {
            if (!ReadOnlySqlite.tableExists(c, "vehicles")) {
                return vehicles;
            }
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id, wx, wy, x, y FROM vehicles")) {
                while (rs.next()) {
                    vehicles.add(new VehicleRecord(rs.getLong(1), new ChunkPos(rs.getInt(2), rs.getInt(3)),
                            rs.getDouble(4), rs.getDouble(5)));
                }
            }
        } catch (SQLException e) {
            throw new IOException("Cannot read " + file + ": " + e.getMessage(), e);
        }
        return vehicles;
    }
}
