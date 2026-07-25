package dev.spruceworks.settings.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API of SpruceSettings. Other plugins register their own toggles here
 * and query per-player state.
 *
 * <p><b>Obtain it through Bukkit's ServicesManager, never by casting the
 * plugin instance:</b>
 *
 * <pre>{@code
 * // plugin.yml:  softdepend: [SpruceSettings]
 * //
 * // Check by NAME first. Touching SpruceSettingsAPI.class at all forces the
 * // JVM to resolve it, which throws NoClassDefFoundError when SpruceSettings
 * // is absent — so the guard and the code that touches the type must live in
 * // SEPARATE methods (bytecode verification is lazy per-method, not
 * // per-branch).
 * if (getServer().getPluginManager().getPlugin("SpruceSettings") != null) {
 *     hookSettings();
 * }
 *
 * private void hookSettings() {
 *     RegisteredServiceProvider<SpruceSettingsAPI> rsp =
 *             getServer().getServicesManager().getRegistration(SpruceSettingsAPI.class);
 *     if (rsp == null) {
 *         return;
 *     }
 *     SpruceSettingsAPI api = rsp.getProvider();
 *     api.register(SettingToggle.builder("myplugin:feature")
 *             .displayName("<green>My feature")
 *             .description(List.of("<gray>Turn my feature on or off."))
 *             .icon(Material.LEVER)
 *             .defaultValue(true)
 *             .build());
 * }
 * }</pre>
 *
 * <p>Then read state wherever you need it:
 * {@code api.isEnabled(player.getUniqueId(), "myplugin:feature")}.
 */
public interface SpruceSettingsAPI {

    /**
     * Registers a toggle so it appears in the {@code /settings} GUI.
     *
     * <p>Registering the same key twice replaces the previous definition —
     * safe across a {@code /settingsadmin reload} of the registering plugin.
     *
     * @throws IllegalArgumentException if the key uses the reserved
     *         {@code settings:} namespace, which belongs to SpruceSettings itself
     */
    void register(SettingToggle toggle);

    /**
     * Reflection-friendly registration for callers that do not want any
     * compile-time dependency on SpruceSettings at all.
     *
     * <p>Every parameter is a JDK type, so the whole call can be made through
     * {@code Method.invoke} without ever loading {@link SettingToggle}. That
     * lets a plugin hook SpruceSettings with no shaded jar, no JitPack
     * coordinate, and no version skew — at the cost of compile-time type
     * safety. Prefer {@link #register(SettingToggle)} when you are happy to
     * add a {@code compileOnly} dependency.
     *
     * @param iconMaterial Bukkit {@link org.bukkit.Material} name; falls back to
     *                     PAPER if it does not resolve, so a Material renamed in
     *                     a future Minecraft version degrades to a wrong icon
     *                     rather than a failed registration
     * @param permission   null for "everyone may use it"
     */
    void registerSimple(String key,
                        String displayName,
                        java.util.List<String> description,
                        String iconMaterial,
                        boolean defaultValue,
                        String permission);

    /**
     * Current value for this player, or the toggle's default if they have never
     * changed it (also the result for an unknown key, so a caller whose toggle
     * failed to register still behaves sensibly rather than throwing).
     *
     * <p>Reads from an in-memory cache — safe to call on the main thread and in
     * hot paths such as event handlers.
     */
    boolean isEnabled(UUID player, String key);

    /** Look up a registered toggle definition. */
    Optional<SettingToggle> toggle(String key);

    /** Every registered toggle, built-in and third-party, in registration order. */
    Collection<SettingToggle> toggles();
}
