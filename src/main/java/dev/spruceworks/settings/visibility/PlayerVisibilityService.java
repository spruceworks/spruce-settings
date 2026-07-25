package dev.spruceworks.settings.visibility;

import dev.spruceworks.settings.service.SettingsService;
import dev.spruceworks.settings.toggle.BuiltInToggles;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Owns every {@code hidePlayer}/{@code showPlayer} call for
 * {@code settings:hide-players}.
 *
 * <p>Bukkit's visibility state is per-session and per-viewer-pair, so it has to
 * be re-established on each join. Keeping all of it in one class means the
 * "who can see whom" rule is stated exactly once:
 *
 * <blockquote>Viewer V sees target T unless V has hide-players on.</blockquote>
 *
 * <p>Note it is deliberately one-directional: enabling hide-players hides
 * others <em>from you</em>; it does not hide <em>you</em> from them. Hiding
 * yourself from others is a vanish feature, which is a moderation tool and out
 * of scope here.
 */
public final class PlayerVisibilityService {

    private final Plugin plugin;
    private final SettingsService settings;

    public PlayerVisibilityService(Plugin plugin, SettingsService settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    /** Re-establish both directions of visibility for a player who just joined. */
    public void applyOnJoin(Player joining) {
        boolean joiningHides = hidesPlayers(joining.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(joining.getUniqueId())) {
                continue;
            }
            // Direction 1: does the joining player see this existing player?
            setVisible(joining, other, !joiningHides);
            // Direction 2: does this existing player see the joining player?
            setVisible(other, joining, !hidesPlayers(other.getUniqueId()));
        }
    }

    /** Apply a just-changed hide-players value for one viewer against everyone online. */
    public void applyForViewer(Player viewer) {
        boolean hide = hidesPlayers(viewer.getUniqueId());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(viewer.getUniqueId())) {
                setVisible(viewer, other, !hide);
            }
        }
    }

    /** Re-apply for every online player — used after a config/API reload. */
    public void refreshAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            applyForViewer(viewer);
        }
    }

    public void forget(UUID player) {
        // Nothing persisted per session; the stored toggle value is the source of
        // truth and is re-applied on next join.
    }

    private boolean hidesPlayers(UUID viewer) {
        return this.settings.isEnabled(viewer, BuiltInToggles.HIDE_PLAYERS);
    }

    private void setVisible(Player viewer, Player target, boolean visible) {
        if (visible) {
            viewer.showPlayer(this.plugin, target);
        } else {
            viewer.hidePlayer(this.plugin, target);
        }
    }
}
