package dev.spruceworks.settings.storage;

import java.util.Map;
import java.util.UUID;

/**
 * Persistence for per-player toggle values.
 *
 * <p>SQLite is the only backend in v1. Kept behind this interface so a future
 * MySQL backend is a config change, not a rewrite — same pattern as
 * SpruceBounty's storage layer.
 *
 * <p>Implementations must be safe to call from an async task; the service layer
 * dispatches writes off the main thread.
 */
public interface SettingsStorage {

    /** Open the backend and create/migrate the schema. */
    void open();

    /** Flush and close. Safe to call when never opened. */
    void close();

    /** Every stored toggle value for one player, keyed by toggle key. Absent keys mean "never set". */
    Map<String, Boolean> load(UUID player);

    /** Every stored value for every player — used to warm the cache at boot. */
    Map<UUID, Map<String, Boolean>> loadAll();

    /** Persist one toggle value. */
    void save(UUID player, String toggleKey, boolean enabled);

    /** Drop one player's stored values entirely (admin reset). */
    void clear(UUID player);
}
