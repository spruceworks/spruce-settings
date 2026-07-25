package dev.spruceworks.settings.listener;

import dev.spruceworks.settings.service.SettingsService;
import dev.spruceworks.settings.toggle.BuiltInToggles;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * {@code settings:hide-public-chat} — removes opted-out players from the
 * recipient set of each public chat message.
 *
 * <p>Removing recipients rather than cancelling the event matters: the message
 * still reaches everyone else, and the sender never learns who is hiding chat.
 *
 * <p>Runs at LOW priority so chat-formatting plugins downstream still see the
 * event normally; we only shrink the audience.
 */
public final class ChatVisibilityListener implements Listener {

    private final SettingsService settings;

    public ChatVisibilityListener(SettingsService settings) {
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        // The sender always sees their own message, even with chat hidden —
        // otherwise typing into the void looks like the server ate the message.
        Player sender = event.getPlayer();
        event.viewers().removeIf(viewer ->
                viewer instanceof Player player
                        && !player.getUniqueId().equals(sender.getUniqueId())
                        && this.settings.isEnabled(player.getUniqueId(), BuiltInToggles.HIDE_PUBLIC_CHAT));
    }
}
