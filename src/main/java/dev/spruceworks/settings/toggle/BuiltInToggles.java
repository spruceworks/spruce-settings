package dev.spruceworks.settings.toggle;

import dev.spruceworks.settings.api.SettingToggle;
import java.util.List;
import org.bukkit.Material;

/**
 * The four toggles SpruceSettings ships itself, in the reserved
 * {@code settings:} namespace.
 *
 * <p>Deliberately NOT included, and never to be added: client-side particle or
 * sound "master toggles". The server cannot control those, so such a toggle
 * would silently do nothing — worse for the player than having no toggle at
 * all. See SPEC-settings.md NON-GOALS.
 */
public final class BuiltInToggles {

    public static final String NAMESPACE = "settings";

    public static final String HIDE_PUBLIC_CHAT = "settings:hide-public-chat";
    public static final String HIDE_PLAYERS = "settings:hide-players";
    public static final String HIDE_JOIN_LEAVE = "settings:hide-join-leave";
    public static final String PVP_OPT_OUT = "settings:pvp-opt-out";

    private BuiltInToggles() {
    }

    public static SettingToggle hidePublicChat() {
        return SettingToggle.builder(HIDE_PUBLIC_CHAT)
                .displayName("<aqua>Hide public chat</aqua>")
                .description(List.of(
                        "<gray>Stop seeing public chat messages.</gray>",
                        "<gray>Private messages and staff",
                        "<gray>announcements still reach you.</gray>"))
                .icon(Material.PAPER)
                .defaultValue(false)
                .build();
    }

    public static SettingToggle hidePlayers() {
        return SettingToggle.builder(HIDE_PLAYERS)
                .displayName("<aqua>Hide other players</aqua>")
                .description(List.of(
                        "<gray>Hide every other player from view.</gray>",
                        "<gray>Useful on laggy clients or busy hubs.</gray>"))
                .icon(Material.ENDER_PEARL)
                .defaultValue(false)
                .build();
    }

    public static SettingToggle hideJoinLeave() {
        return SettingToggle.builder(HIDE_JOIN_LEAVE)
                .displayName("<aqua>Hide join/leave messages</aqua>")
                .description(List.of("<gray>Stop seeing who joins and leaves.</gray>"))
                .icon(Material.OAK_DOOR)
                .defaultValue(false)
                .build();
    }

    /**
     * Only registered when {@code pvp-opt-out.enabled} is true in config — many
     * servers consider this game-breaking and must be able to remove it
     * outright, not merely permission-gate it.
     */
    public static SettingToggle pvpOptOut() {
        return SettingToggle.builder(PVP_OPT_OUT)
                .displayName("<aqua>PvP protection</aqua>")
                .description(List.of(
                        "<gray>Stop other players damaging you,</gray>",
                        "<gray>and stop yourself damaging them.</gray>",
                        "<dark_gray>Cannot be switched on during combat.</dark_gray>"))
                .icon(Material.SHIELD)
                .defaultValue(false)
                .build();
    }
}
