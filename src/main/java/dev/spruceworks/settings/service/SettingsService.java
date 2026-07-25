package dev.spruceworks.settings.service;

import dev.spruceworks.settings.api.SettingToggle;
import dev.spruceworks.settings.api.SpruceSettingsAPI;
import dev.spruceworks.settings.storage.SettingsStorage;
import dev.spruceworks.settings.toggle.BuiltInToggles;
import dev.spruceworks.settings.util.SchedulerAdapter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * Core state: the toggle registry plus an in-memory cache of every player's
 * values, backed by {@link SettingsStorage}.
 *
 * <p>Reads are served from the cache so {@link #isEnabled} is cheap enough to
 * call inside event handlers; writes update the cache immediately and are
 * persisted off the main thread through the scheduler wrapper.
 */
public final class SettingsService implements SpruceSettingsAPI {

    private final SettingsStorage storage;
    private final SchedulerAdapter scheduler;
    private final Logger logger;

    /** Registration order is GUI order, so LinkedHashMap, not HashMap. */
    private final Map<String, SettingToggle> registry = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Boolean>> cache = new ConcurrentHashMap<>();

    public SettingsService(SettingsStorage storage, SchedulerAdapter scheduler, Logger logger) {
        this.storage = storage;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    /** Warm the cache from disk at boot. */
    public void loadFromStorage() {
        this.cache.clear();
        this.storage.loadAll().forEach((id, values) -> this.cache.put(id, new ConcurrentHashMap<>(values)));
        this.logger.info("Loaded stored settings for {} player(s).", this.cache.size());
    }

    // ---- registry ----

    /** Registers one of our own toggles; bypasses the reserved-namespace check. */
    public void registerBuiltIn(SettingToggle toggle) {
        this.registry.put(toggle.key(), toggle);
    }

    /**
     * Removes one of our own toggles. Needed when a config master switch is
     * turned off at runtime — without this, {@code /settingsadmin reload} would
     * leave a disabled feature still clickable in the GUI.
     */
    public void removeBuiltIn(String key) {
        this.registry.remove(key);
    }

    @Override
    public void register(SettingToggle toggle) {
        if (toggle.key().startsWith(BuiltInToggles.NAMESPACE + ":")) {
            throw new IllegalArgumentException(
                    "The '" + BuiltInToggles.NAMESPACE + ":' namespace is reserved for SpruceSettings itself; "
                            + "use your own plugin name as the namespace (got: '" + toggle.key() + "')");
        }
        SettingToggle previous = this.registry.put(toggle.key(), toggle);
        if (previous == null) {
            this.logger.info("Registered third-party toggle '{}'.", toggle.key());
        } else {
            this.logger.debug("Re-registered toggle '{}' (definition replaced).", toggle.key());
        }
    }

    @Override
    public void registerSimple(String key,
                               String displayName,
                               java.util.List<String> description,
                               String iconMaterial,
                               boolean defaultValue,
                               String permission) {
        org.bukkit.Material icon;
        try {
            icon = org.bukkit.Material.valueOf(iconMaterial);
        } catch (IllegalArgumentException | NullPointerException e) {
            // A renamed/removed Material must not cost the caller their whole
            // registration — a wrong icon is far cheaper than a missing toggle.
            this.logger.warn("Toggle '{}' asked for unknown Material '{}'; falling back to PAPER.",
                    key, iconMaterial);
            icon = org.bukkit.Material.PAPER;
        }
        register(SettingToggle.builder(key)
                .displayName(displayName)
                .description(description == null ? java.util.List.of() : description)
                .icon(icon)
                .defaultValue(defaultValue)
                .permission(permission)
                .build());
    }

    /** Drops every non-built-in toggle. Used on reload so stale definitions do not linger. */
    public void unregisterThirdParty() {
        this.registry.keySet().removeIf(k -> !k.startsWith(BuiltInToggles.NAMESPACE + ":"));
    }

    @Override
    public Optional<SettingToggle> toggle(String key) {
        return Optional.ofNullable(this.registry.get(key));
    }

    @Override
    public Collection<SettingToggle> toggles() {
        return java.util.List.copyOf(this.registry.values());
    }

    // ---- per-player state ----

    @Override
    public boolean isEnabled(UUID player, String key) {
        Map<String, Boolean> values = this.cache.get(player);
        if (values != null) {
            Boolean stored = values.get(key);
            if (stored != null) {
                return stored;
            }
        }
        // Never set by this player — fall back to the toggle's declared default.
        // An unknown key resolves to false rather than throwing, so a caller
        // whose registration silently failed still behaves predictably.
        return this.registry.containsKey(key) && this.registry.get(key).defaultValue();
    }

    /** Sets a value, updating the cache now and persisting asynchronously. */
    public void set(UUID player, String key, boolean enabled) {
        this.cache.computeIfAbsent(player, k -> new ConcurrentHashMap<>()).put(key, enabled);
        this.scheduler.runAsync(() -> {
            try {
                this.storage.save(player, key, enabled);
            } catch (RuntimeException e) {
                this.logger.error("Failed to persist setting {} for {}", key, player, e);
            }
        });
    }

    /** Convenience for the GUI: flip and return the new value. */
    public boolean toggleValue(UUID player, String key) {
        boolean next = !isEnabled(player, key);
        set(player, key, next);
        return next;
    }

    /** Admin reset — clears cache and storage for one player. */
    public void clear(UUID player) {
        this.cache.remove(player);
        this.scheduler.runAsync(() -> {
            try {
                this.storage.clear(player);
            } catch (RuntimeException e) {
                this.logger.error("Failed to clear settings for {}", player, e);
            }
        });
    }
}
