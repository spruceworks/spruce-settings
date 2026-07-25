package dev.spruceworks.settings.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * Persistence tests against a real SQLite file in a temp dir — no mocks, so
 * these catch schema and SQL mistakes that a mocked storage layer would hide.
 *
 * <p>The behaviour that matters most: a value written in one "session" is still
 * there after close/reopen. That is exactly the rejoin-persistence guarantee
 * players notice when it breaks.
 */
class SqliteSettingsStorageTest {

    @TempDir
    Path tempDir;

    private SqliteSettingsStorage storage;

    private SqliteSettingsStorage open() {
        this.storage = new SqliteSettingsStorage(
                new File(this.tempDir.toFile(), "data"),
                LoggerFactory.getLogger(SqliteSettingsStorageTest.class));
        this.storage.open();
        return this.storage;
    }

    @AfterEach
    void tearDown() {
        if (this.storage != null) {
            this.storage.close();
        }
    }

    @Test
    void savedValueIsReadBack() {
        SqliteSettingsStorage s = open();
        UUID player = UUID.randomUUID();
        s.save(player, "settings:hide-players", true);

        Map<String, Boolean> loaded = s.load(player);
        assertEquals(1, loaded.size());
        assertTrue(loaded.get("settings:hide-players"));
    }

    @Test
    void valueSurvivesCloseAndReopen() {
        UUID player = UUID.randomUUID();
        SqliteSettingsStorage first = open();
        first.save(player, "settings:hide-public-chat", true);
        first.close();

        // Reopen against the same directory — this is the rejoin case.
        SqliteSettingsStorage second = open();
        assertTrue(second.load(player).get("settings:hide-public-chat"));
    }

    @Test
    void savingSameKeyTwiceUpdatesRatherThanDuplicating() {
        SqliteSettingsStorage s = open();
        UUID player = UUID.randomUUID();
        s.save(player, "settings:pvp-opt-out", true);
        s.save(player, "settings:pvp-opt-out", false);

        Map<String, Boolean> loaded = s.load(player);
        assertEquals(1, loaded.size(), "ON CONFLICT should update in place, not insert a second row");
        assertFalse(loaded.get("settings:pvp-opt-out"));
    }

    @Test
    void playersAreIsolatedFromEachOther() {
        SqliteSettingsStorage s = open();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        s.save(a, "settings:hide-players", true);
        s.save(b, "settings:hide-players", false);

        assertTrue(s.load(a).get("settings:hide-players"));
        assertFalse(s.load(b).get("settings:hide-players"));
    }

    @Test
    void loadAllGroupsByPlayer() {
        SqliteSettingsStorage s = open();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        s.save(a, "settings:hide-players", true);
        s.save(a, "settings:hide-join-leave", true);
        s.save(b, "settings:hide-players", false);

        Map<UUID, Map<String, Boolean>> all = s.loadAll();
        assertEquals(2, all.size());
        assertEquals(2, all.get(a).size());
        assertEquals(1, all.get(b).size());
    }

    @Test
    void unsetKeyIsAbsentRatherThanFalse() {
        // The service distinguishes "never set" (use the toggle default) from
        // "explicitly false", so storage must not invent entries.
        SqliteSettingsStorage s = open();
        UUID player = UUID.randomUUID();
        s.save(player, "settings:hide-players", true);

        assertFalse(s.load(player).containsKey("settings:hide-join-leave"));
    }

    @Test
    void clearRemovesOnlyThatPlayer() {
        SqliteSettingsStorage s = open();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        s.save(a, "settings:hide-players", true);
        s.save(b, "settings:hide-players", true);

        s.clear(a);
        assertTrue(s.load(a).isEmpty());
        assertEquals(1, s.load(b).size());
    }

    @Test
    void loadOfUnknownPlayerIsEmptyNotNull() {
        SqliteSettingsStorage s = open();
        assertTrue(s.load(UUID.randomUUID()).isEmpty());
    }
}
