package dev.spruceworks.settings.placeholder;

import dev.spruceworks.settings.SpruceSettingsPlugin;
import java.util.Locale;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * One placeholder per toggle, resolved dynamically so third-party toggles
 * registered through the API work without any extra wiring:
 *
 * <pre>
 * %sprucesettings_&lt;namespace&gt;_&lt;name&gt;%        -> "true" / "false"
 * %sprucesettings_pretty_&lt;namespace&gt;_&lt;name&gt;% -> the configured on/off wording
 * </pre>
 *
 * Colons are not legal in placeholder syntax, so the first underscore after the
 * prefix stands in for the {@code :} in a toggle key —
 * {@code %sprucesettings_settings_hide-players%} maps to
 * {@code settings:hide-players}.
 */
public final class SettingsPlaceholderExpansion extends PlaceholderExpansion {

    private final SpruceSettingsPlugin plugin;

    public SettingsPlaceholderExpansion(SpruceSettingsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "sprucesettings";
    }

    @Override
    public String getAuthor() {
        return "SpruceWorks";
    }

    @Override
    public String getVersion() {
        return this.plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "";
        }
        String key = params.toLowerCase(Locale.ROOT);
        boolean pretty = false;
        if (key.startsWith("pretty_")) {
            pretty = true;
            key = key.substring("pretty_".length());
        }
        String toggleKey = toToggleKey(key);
        if (toggleKey == null || this.plugin.settingsService().toggle(toggleKey).isEmpty()) {
            return null; // unknown placeholder — let PAPI show it unresolved
        }
        boolean enabled = this.plugin.settingsService().isEnabled(player.getUniqueId(), toggleKey);
        if (!pretty) {
            return String.valueOf(enabled);
        }
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(this.plugin.messages().get(enabled ? "gui-state-on" : "gui-state-off"));
    }

    /** {@code settings_hide-players} -> {@code settings:hide-players}. */
    private String toToggleKey(String params) {
        int underscore = params.indexOf('_');
        if (underscore <= 0 || underscore == params.length() - 1) {
            return null;
        }
        return params.substring(0, underscore) + ":" + params.substring(underscore + 1);
    }
}
