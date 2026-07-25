package dev.spruceworks.settings.combat;

import java.time.Duration;
import java.time.Instant;

/**
 * Decides whether a player may switch PvP protection ON right now.
 *
 * <p>Without this, {@code pvp-opt-out} is an escape hatch: take damage, open
 * {@code /settings}, flip protection on, walk away from the fight. The gate
 * blocks enabling protection for {@code windowSeconds} after the most recent
 * damage taken.
 *
 * <p><b>Only enabling is gated.</b> Turning protection OFF mid-combat makes a
 * player MORE vulnerable, never less, so there is nothing to exploit and it
 * stays allowed at all times — see {@link #canDisableProtection()}.
 *
 * <p>Pure functions over an injected {@code now}: no Bukkit types, no clock
 * reads, fully unit-testable.
 */
public final class CombatGate {

    private CombatGate() {
    }

    /**
     * @param lastDamageAt when the player last took damage, or null if never
     * @param now          current instant
     * @param windowSeconds combat window; {@code <= 0} disables the gate entirely
     * @return true if the player may turn PvP protection on
     */
    public static boolean canEnableProtection(Instant lastDamageAt, Instant now, int windowSeconds) {
        if (windowSeconds <= 0) {
            return true; // gate disabled by config
        }
        if (lastDamageAt == null) {
            return true; // never been hit
        }
        if (now.isBefore(lastDamageAt)) {
            // Clock went backwards, or damage is recorded in the future. Treat as
            // in-combat: failing closed here costs a player one menu click, while
            // failing open hands them the exact exploit this gate exists to stop.
            return false;
        }
        return remainingSeconds(lastDamageAt, now, windowSeconds) == 0;
    }

    /** Disabling protection is never gated — it can only make the player more vulnerable. */
    public static boolean canDisableProtection() {
        return true;
    }

    /**
     * Whole seconds left before protection may be enabled, rounded UP so a
     * message never says "0 seconds" while still blocked. Returns 0 when the
     * window has fully elapsed.
     */
    public static long remainingSeconds(Instant lastDamageAt, Instant now, int windowSeconds) {
        if (windowSeconds <= 0 || lastDamageAt == null) {
            return 0;
        }
        Instant clearAt = lastDamageAt.plusSeconds(windowSeconds);
        if (!now.isBefore(clearAt)) {
            return 0;
        }
        long millis = Duration.between(now, clearAt).toMillis();
        return (millis + 999) / 1000; // ceiling
    }
}
