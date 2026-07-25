package dev.spruceworks.settings.listener;

import dev.spruceworks.settings.service.SettingsService;
import dev.spruceworks.settings.toggle.BuiltInToggles;
import dev.spruceworks.settings.visibility.PlayerVisibilityService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Two jobs:
 *
 * <ol>
 *   <li>{@code settings:hide-join-leave} — suppress the join/leave broadcast
 *       for players who opted out of seeing them.</li>
 *   <li>{@code settings:hide-players} rejoin consistency — the single easiest
 *       thing to get wrong in this plugin. {@code hidePlayer} state is per
 *       session and lost on disconnect, so on every join we must re-apply it
 *       in BOTH directions: hide everyone from the joining player if THEY have
 *       it on, and hide the joining player from everyone who has it on.
 *       Miss either half and the setting silently rots.</li>
 * </ol>
 */
public final class JoinQuitListener implements Listener {

    private final SettingsService settings;
    private final PlayerVisibilityService visibility;

    public JoinQuitListener(SettingsService settings, PlayerVisibilityService visibility) {
        this.settings = settings;
        this.visibility = visibility;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();

        // 1. Suppress the broadcast for anyone hiding join/leave messages.
        //    Paper lets us narrow the audience rather than kill the message,
        //    so players who want it still get it.
        if (event.joinMessage() != null) {
            boolean anyoneHiding = Bukkit.getOnlinePlayers().stream()
                    .anyMatch(p -> this.settings.isEnabled(p.getUniqueId(), BuiltInToggles.HIDE_JOIN_LEAVE));
            if (anyoneHiding) {
                var message = event.joinMessage();
                event.joinMessage(null); // suppress the default broadcast...
                broadcastRespectingOptOut(message);        // ...and re-send it selectively
            }
        }

        // 2. Re-apply hide-players in both directions.
        this.visibility.applyOnJoin(joining);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (event.quitMessage() != null) {
            boolean anyoneHiding = Bukkit.getOnlinePlayers().stream()
                    .anyMatch(p -> this.settings.isEnabled(p.getUniqueId(), BuiltInToggles.HIDE_JOIN_LEAVE));
            if (anyoneHiding) {
                var message = event.quitMessage();
                event.quitMessage(null);
                broadcastRespectingOptOut(message);
            }
        }
        this.visibility.forget(event.getPlayer().getUniqueId());
    }

    private void broadcastRespectingOptOut(net.kyori.adventure.text.Component message) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!this.settings.isEnabled(viewer.getUniqueId(), BuiltInToggles.HIDE_JOIN_LEAVE)) {
                viewer.sendMessage(message);
            }
        }
        // Console keeps seeing everything.
        Bukkit.getConsoleSender().sendMessage(message);
    }
}
