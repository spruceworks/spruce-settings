package dev.spruceworks.settings.listener;

import dev.spruceworks.settings.combat.CombatTracker;
import dev.spruceworks.settings.config.ConfigManager;
import dev.spruceworks.settings.service.SettingsService;
import dev.spruceworks.settings.toggle.BuiltInToggles;
import java.time.Instant;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Enforces {@code settings:pvp-opt-out} and feeds {@link CombatTracker}.
 *
 * <p>Protection is symmetric on purpose: an opted-out player can neither be hit
 * by another player nor hit one. One-way protection would be strictly better
 * than not opting out, which turns a comfort setting into a combat advantage.
 *
 * <p>Only player-vs-player damage is affected. Mobs, fall damage, lava and the
 * rest are untouched — this is not a god-mode toggle.
 */
public final class PvpListener implements Listener {

    private final ConfigManager configManager;
    private final SettingsService settings;
    private final CombatTracker combatTracker;

    public PvpListener(ConfigManager configManager, SettingsService settings, CombatTracker combatTracker) {
        this.configManager = configManager;
        this.settings = settings;
        this.combatTracker = combatTracker;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return; // not PvP (mob/environment), or self-damage
        }

        if (pvpOptOutEnabled()) {
            boolean victimProtected = this.settings.isEnabled(victim.getUniqueId(), BuiltInToggles.PVP_OPT_OUT);
            boolean attackerProtected = this.settings.isEnabled(attacker.getUniqueId(), BuiltInToggles.PVP_OPT_OUT);
            if (victimProtected || attackerProtected) {
                event.setCancelled(true);
                return; // cancelled: no real combat happened, so nothing to record
            }
        }

        // Real PvP damage landed — both parties are now "in combat" for gate purposes.
        Instant now = Instant.now();
        this.combatTracker.recordDamage(victim.getUniqueId(), now);
        this.combatTracker.recordDamage(attacker.getUniqueId(), now);
    }

    /** Unwraps projectiles so a bow shot counts as its shooter, not as an arrow. */
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player direct) {
            return direct;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private boolean pvpOptOutEnabled() {
        return this.configManager.config().getBoolean("pvp-opt-out.enabled", true);
    }
}
