package dev.spruceworks.settings.api;

import java.util.List;
import java.util.Objects;
import org.bukkit.Material;

/**
 * One toggle entry in the {@code /settings} GUI.
 *
 * <p>Built by another plugin and handed to {@link SpruceSettingsAPI#register}.
 * Instances are immutable and safe to hold onto.
 *
 * <p>Keys are namespaced {@code owner:name} (e.g. {@code sprucebounty:broadcasts})
 * so two plugins can never collide. SpruceSettings' own toggles use the
 * {@code settings:} namespace.
 */
public final class SettingToggle {

    private final String key;
    private final String displayName;
    private final List<String> description;
    private final Material icon;
    private final boolean defaultValue;
    private final String permission;

    private SettingToggle(Builder builder) {
        this.key = builder.key;
        this.displayName = builder.displayName;
        this.description = List.copyOf(builder.description);
        this.icon = builder.icon;
        this.defaultValue = builder.defaultValue;
        this.permission = builder.permission;
    }

    public static Builder builder(String key) {
        return new Builder(key);
    }

    /** Namespaced identifier, e.g. {@code sprucebounty:broadcasts}. Also the storage key. */
    public String key() {
        return this.key;
    }

    /** MiniMessage display name shown on the GUI item. */
    public String displayName() {
        return this.displayName;
    }

    /** MiniMessage lore lines explaining what the toggle does. */
    public List<String> description() {
        return this.description;
    }

    public Material icon() {
        return this.icon;
    }

    /** Value used for a player who has never set this toggle. */
    public boolean defaultValue() {
        return this.defaultValue;
    }

    /** Permission required to see/change this toggle, or null if everyone may. */
    public String permission() {
        return this.permission;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SettingToggle other && this.key.equals(other.key);
    }

    @Override
    public int hashCode() {
        return this.key.hashCode();
    }

    @Override
    public String toString() {
        return "SettingToggle[" + this.key + "]";
    }

    public static final class Builder {

        private final String key;
        private String displayName;
        private List<String> description = List.of();
        private Material icon = Material.PAPER;
        private boolean defaultValue;
        private String permission;

        private Builder(String key) {
            this.key = requireNamespacedKey(key);
            this.displayName = key;
        }

        /** MiniMessage. */
        public Builder displayName(String displayName) {
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            return this;
        }

        /** MiniMessage lore lines. */
        public Builder description(List<String> description) {
            this.description = Objects.requireNonNull(description, "description");
            return this;
        }

        public Builder icon(Material icon) {
            this.icon = Objects.requireNonNull(icon, "icon");
            return this;
        }

        public Builder defaultValue(boolean defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        /** Null means no permission required. */
        public Builder permission(String permission) {
            this.permission = permission;
            return this;
        }

        public SettingToggle build() {
            return new SettingToggle(this);
        }

        private static String requireNamespacedKey(String key) {
            Objects.requireNonNull(key, "key");
            int colon = key.indexOf(':');
            if (colon <= 0 || colon == key.length() - 1) {
                throw new IllegalArgumentException(
                        "Toggle key must be namespaced as owner:name (got: '" + key + "')");
            }
            if (!key.equals(key.toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("Toggle key must be lowercase (got: '" + key + "')");
            }
            return key;
        }
    }
}
