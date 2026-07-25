package dev.spruceworks.settings.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;

/**
 * SQLite backend. One connection held for the plugin's lifetime and every
 * method synchronized: SQLite serializes writes anyway, so a pooling library
 * would add a dependency and buy nothing for a single embedded file.
 *
 * <p>WAL mode so a read never blocks behind a write.
 */
public final class SqliteSettingsStorage implements SettingsStorage {

    private static final int SCHEMA_VERSION = 1;

    private final File dataFolder;
    private final Logger logger;
    private Connection connection;

    public SqliteSettingsStorage(File dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    @Override
    public synchronized void open() {
        try {
            if (!this.dataFolder.exists() && !this.dataFolder.mkdirs()) {
                throw new StorageException("Could not create the plugin data folder: " + this.dataFolder);
            }
            File dbFile = new File(this.dataFolder, "settings.db");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement st = this.connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("""
                        CREATE TABLE IF NOT EXISTS schema_version (
                            version INTEGER NOT NULL
                        )
                        """);
                st.execute("""
                        CREATE TABLE IF NOT EXISTS player_settings (
                            player_uuid TEXT    NOT NULL,
                            toggle_key  TEXT    NOT NULL,
                            enabled     INTEGER NOT NULL,
                            updated_at  INTEGER NOT NULL,
                            PRIMARY KEY (player_uuid, toggle_key)
                        )
                        """);
            }
            int current = currentVersion();
            if (current == 0) {
                try (PreparedStatement ps =
                             this.connection.prepareStatement("INSERT INTO schema_version (version) VALUES (?)")) {
                    ps.setInt(1, SCHEMA_VERSION);
                    ps.executeUpdate();
                }
                this.logger.info("Initialized SpruceSettings database schema v{}.", SCHEMA_VERSION);
            } else if (current != SCHEMA_VERSION) {
                // No migrations exist yet; when they do, they branch from here.
                this.logger.warn("SpruceSettings database reports schema v{} but this build expects v{}.",
                        current, SCHEMA_VERSION);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to open the SpruceSettings SQLite database", e);
        }
    }

    private int currentVersion() throws SQLException {
        try (Statement st = this.connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            return rs.next() ? rs.getInt("version") : 0;
        }
    }

    @Override
    public synchronized void close() {
        if (this.connection == null) {
            return;
        }
        try {
            this.connection.close();
        } catch (SQLException e) {
            this.logger.error("Failed to close the SpruceSettings database cleanly", e);
        } finally {
            this.connection = null;
        }
    }

    @Override
    public synchronized Map<String, Boolean> load(UUID player) {
        Map<String, Boolean> out = new HashMap<>();
        String sql = "SELECT toggle_key, enabled FROM player_settings WHERE player_uuid = ?";
        try (PreparedStatement ps = this.connection.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("toggle_key"), rs.getInt("enabled") != 0);
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load settings for " + player, e);
        }
        return out;
    }

    @Override
    public synchronized Map<UUID, Map<String, Boolean>> loadAll() {
        Map<UUID, Map<String, Boolean>> out = new HashMap<>();
        String sql = "SELECT player_uuid, toggle_key, enabled FROM player_settings";
        try (Statement st = this.connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                UUID id;
                try {
                    id = UUID.fromString(rs.getString("player_uuid"));
                } catch (IllegalArgumentException malformed) {
                    // One bad row must not sink the whole boot-time cache warm.
                    this.logger.warn("Skipping settings row with a malformed player_uuid: {}",
                            rs.getString("player_uuid"));
                    continue;
                }
                out.computeIfAbsent(id, k -> new HashMap<>())
                        .put(rs.getString("toggle_key"), rs.getInt("enabled") != 0);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load all player settings", e);
        }
        return out;
    }

    @Override
    public synchronized void save(UUID player, String toggleKey, boolean enabled) {
        String sql = """
                INSERT INTO player_settings (player_uuid, toggle_key, enabled, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(player_uuid, toggle_key)
                DO UPDATE SET enabled = excluded.enabled, updated_at = excluded.updated_at
                """;
        try (PreparedStatement ps = this.connection.prepareStatement(sql)) {
            ps.setString(1, player.toString());
            ps.setString(2, toggleKey);
            ps.setInt(3, enabled ? 1 : 0);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to save setting " + toggleKey + " for " + player, e);
        }
    }

    @Override
    public synchronized void clear(UUID player) {
        try (PreparedStatement ps =
                     this.connection.prepareStatement("DELETE FROM player_settings WHERE player_uuid = ?")) {
            ps.setString(1, player.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to clear settings for " + player, e);
        }
    }
}
