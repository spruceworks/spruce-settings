package dev.spruceworks.settings.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The combat gate is the one piece of SpruceSettings that is exploitable if it
 * is even slightly wrong, so it gets boundary-level coverage: at, inside, and
 * outside the window; the exact boundary tick; both toggle directions; and the
 * degenerate inputs (never damaged, gate disabled, clock skew).
 */
class CombatGateTest {

    private static final Instant T0 = Instant.parse("2026-07-25T12:00:00Z");
    private static final int WINDOW = 15;

    @Test
    void neverDamagedPlayerMayEnableProtection() {
        assertTrue(CombatGate.canEnableProtection(null, T0, WINDOW));
    }

    @Test
    void damageJustNowBlocksEnabling() {
        assertFalse(CombatGate.canEnableProtection(T0, T0, WINDOW));
    }

    @Test
    void insideWindowBlocksEnabling() {
        assertFalse(CombatGate.canEnableProtection(T0, T0.plusSeconds(14), WINDOW));
    }

    @Test
    void oneMilliBeforeBoundaryStillBlocks() {
        assertFalse(CombatGate.canEnableProtection(T0, T0.plusSeconds(15).minusMillis(1), WINDOW));
    }

    @Test
    void exactlyAtBoundaryAllows() {
        // clearAt == now: the window has elapsed, so enabling is allowed.
        assertTrue(CombatGate.canEnableProtection(T0, T0.plusSeconds(15), WINDOW));
    }

    @Test
    void afterWindowAllows() {
        assertTrue(CombatGate.canEnableProtection(T0, T0.plusSeconds(16), WINDOW));
    }

    @Test
    void windowOfZeroDisablesTheGate() {
        assertTrue(CombatGate.canEnableProtection(T0, T0, 0));
    }

    @Test
    void negativeWindowDisablesTheGate() {
        assertTrue(CombatGate.canEnableProtection(T0, T0, -5));
    }

    @Test
    void clockSkewFailsClosed() {
        // Damage recorded "in the future" must not be read as an elapsed window.
        assertFalse(CombatGate.canEnableProtection(T0, T0.minusSeconds(30), WINDOW));
    }

    @Test
    void disablingProtectionIsNeverGated() {
        // The whole point: turning protection off mid-combat is not an exploit.
        assertTrue(CombatGate.canDisableProtection());
    }

    @Test
    void remainingSecondsCountsDownAndRoundsUp() {
        assertEquals(15, CombatGate.remainingSeconds(T0, T0, WINDOW));
        assertEquals(10, CombatGate.remainingSeconds(T0, T0.plusSeconds(5), WINDOW));
        // 0.5s left must report 1, never 0, or the message contradicts the block.
        assertEquals(1, CombatGate.remainingSeconds(T0, T0.plusSeconds(15).minusMillis(500), WINDOW));
    }

    @Test
    void remainingSecondsIsZeroOnceElapsed() {
        assertEquals(0, CombatGate.remainingSeconds(T0, T0.plusSeconds(15), WINDOW));
        assertEquals(0, CombatGate.remainingSeconds(T0, T0.plusSeconds(99), WINDOW));
    }

    @Test
    void remainingSecondsIsZeroWhenGateDisabledOrNeverDamaged() {
        assertEquals(0, CombatGate.remainingSeconds(T0, T0, 0));
        assertEquals(0, CombatGate.remainingSeconds(null, T0, WINDOW));
    }

    @Test
    void remainingSecondsNeverReportsZeroWhileStillBlocked() {
        // Invariant sweep: blocked implies remaining > 0, across the whole window.
        for (int ms = 0; ms < WINDOW * 1000; ms += 250) {
            Instant now = T0.plusMillis(ms);
            boolean allowed = CombatGate.canEnableProtection(T0, now, WINDOW);
            long left = CombatGate.remainingSeconds(T0, now, WINDOW);
            assertFalse(allowed, "should still be blocked at +" + ms + "ms");
            assertTrue(left > 0, "remaining must be > 0 while blocked, at +" + ms + "ms");
        }
    }
}
