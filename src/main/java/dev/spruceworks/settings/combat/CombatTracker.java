package dev.spruceworks.settings.combat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers when each player last took damage, so {@link CombatGate} can decide
 * whether enabling PvP protection right now would be an escape hatch.
 *
 * <p>In-memory only and deliberately so: a restart clearing combat state is
 * harmless (worst case a player enables protection a few seconds earlier than
 * they otherwise could), and persisting it would mean a disk write on every
 * hit.
 */
public final class CombatTracker {

    private final Map<UUID, Instant> lastDamage = new ConcurrentHashMap<>();

    public void recordDamage(UUID player, Instant at) {
        this.lastDamage.put(player, at);
    }

    /** Null if this player has not been damaged since the last restart. */
    public Instant lastDamageAt(UUID player) {
        return this.lastDamage.get(player);
    }

    public void forget(UUID player) {
        this.lastDamage.remove(player);
    }

    /**
     * Drops entries older than the combat window so the map cannot grow without
     * bound on a long-running server. Call periodically.
     */
    public void sweep(Instant now, int windowSeconds) {
        if (windowSeconds <= 0) {
            this.lastDamage.clear();
            return;
        }
        this.lastDamage.entrySet()
                .removeIf(e -> CombatGate.remainingSeconds(e.getValue(), now, windowSeconds) == 0);
    }

    /** Visible for tests/diagnostics. */
    public int trackedCount() {
        return this.lastDamage.size();
    }
}
