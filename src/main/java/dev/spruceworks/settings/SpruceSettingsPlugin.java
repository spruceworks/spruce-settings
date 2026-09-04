package dev.spruceworks.settings;

import dev.spruceworks.settings.api.SpruceSettingsAPI;
import dev.spruceworks.settings.combat.CombatTracker;
import dev.spruceworks.settings.command.SettingsAdminCommand;
import dev.spruceworks.settings.command.SettingsCommand;
import dev.spruceworks.settings.config.ConfigManager;
import dev.spruceworks.settings.config.Messages;
import dev.spruceworks.settings.gui.GuiListener;
import dev.spruceworks.settings.listener.ChatVisibilityListener;
import dev.spruceworks.settings.listener.JoinQuitListener;
import dev.spruceworks.settings.listener.PvpListener;
import dev.spruceworks.settings.service.SettingsService;
import dev.spruceworks.settings.storage.SettingsStorage;
import dev.spruceworks.settings.storage.SqliteSettingsStorage;
import dev.spruceworks.settings.toggle.BuiltInToggles;
import dev.spruceworks.settings.util.SchedulerAdapter;
import dev.spruceworks.settings.visibility.PlayerVisibilityService;
import java.time.Instant;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpruceSettingsPlugin extends JavaPlugin {

    /** bStats service id — https://bstats.org/plugin/bukkit/SpruceSettings/32935 */
    private static final int BSTATS_SERVICE_ID = 32935;
    private static final long COMBAT_SWEEP_PERIOD_TICKS = 20L * 60 * 5; // 5 minutes

    private ConfigManager configManager;
    private Messages messages;
    private SchedulerAdapter scheduler;
    private SettingsStorage storage;
    private SettingsService settingsService;
    private CombatTracker combatTracker;
    private PlayerVisibilityService visibilityService;
    private Metrics metrics;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.load();
        this.messages = new Messages(this.configManager);
        this.scheduler = new SchedulerAdapter(this);
        this.combatTracker = new CombatTracker();

        this.storage = new SqliteSettingsStorage(getDataFolder(), getSLF4JLogger());
        this.storage.open();

        this.settingsService = new SettingsService(this.storage, this.scheduler, getSLF4JLogger());
        this.settingsService.loadFromStorage();
        this.visibilityService = new PlayerVisibilityService(this, this.settingsService);

        registerBuiltInToggles();

        // Publish the API through the ServicesManager so soft-depending plugins
        // can find it without ever casting our plugin instance.
        getServer().getServicesManager().register(
                SpruceSettingsAPI.class, this.settingsService, this, ServicePriority.Normal);

        SettingsCommand.register(this);
        SettingsAdminCommand.register(this);

        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatVisibilityListener(this.settingsService), this);
        getServer().getPluginManager().registerEvents(
                new JoinQuitListener(this.settingsService, this.visibilityService), this);
        getServer().getPluginManager().registerEvents(
                new PvpListener(this.configManager, this.settingsService, this.combatTracker), this);

        this.scheduler.runTimerAsync(this::sweepCombatTracker,
                COMBAT_SWEEP_PERIOD_TICKS, COMBAT_SWEEP_PERIOD_TICKS);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            hookPlaceholderApi();
        }

        if (BSTATS_SERVICE_ID > 0 && this.configManager.config().getBoolean("metrics", true)) {
            this.metrics = new Metrics(this, BSTATS_SERVICE_ID);
        }

        getSLF4JLogger().info("SpruceSettings enabled — {} toggle(s) registered.",
                this.settingsService.toggles().size());
    }

    /**
     * Separate method so the PlaceholderAPI types it references are only
     * resolved after the by-name presence check above has passed. Bytecode
     * verification is lazy per-method, not per-branch — an early return inside
     * this method would NOT be enough.
     */
    private void hookPlaceholderApi() {
        new dev.spruceworks.settings.placeholder.SettingsPlaceholderExpansion(this).register();
        getSLF4JLogger().info("Hooked into PlaceholderAPI.");
    }

    private void registerBuiltInToggles() {
        this.settingsService.registerBuiltIn(BuiltInToggles.hidePublicChat());
        this.settingsService.registerBuiltIn(BuiltInToggles.hidePlayers());
        this.settingsService.registerBuiltIn(BuiltInToggles.hideJoinLeave());
        // Master switch: servers that consider PvP opt-out game-breaking remove
        // the feature entirely rather than permission-gating it. Note the else
        // branch actively REMOVES it, so flipping the switch off and reloading
        // takes the toggle out of the GUI instead of leaving a dead entry.
        if (this.configManager.config().getBoolean("pvp-opt-out.enabled", true)) {
            this.settingsService.registerBuiltIn(BuiltInToggles.pvpOptOut());
        } else {
            this.settingsService.removeBuiltIn(BuiltInToggles.PVP_OPT_OUT);
            getSLF4JLogger().info("pvp-opt-out is disabled in config — the toggle is not registered.");
        }
    }

    private void sweepCombatTracker() {
        int window = this.configManager.config().getInt("pvp-opt-out.combat-window-seconds", 15);
        this.combatTracker.sweep(Instant.now(), window);
    }

    /**
     * Reload config + messages and rebuild the built-in toggle set, so flipping
     * the pvp-opt-out master switch takes effect without a restart.
     *
     * <p>Third-party registrations are preserved: they belong to other plugins
     * and are not ours to drop.
     */
    public boolean reloadPluginConfig() {
        boolean ok = this.configManager.reload();
        if (ok) {
            registerBuiltInToggles();
            this.visibilityService.refreshAll();
        }
        return ok;
    }

    @Override
    public void onDisable() {
        if (this.storage != null) {
            this.storage.close();
        }
        if (this.metrics != null) {
            this.metrics.shutdown();
        }
    }

    public ConfigManager configManager() {
        return this.configManager;
    }

    public Messages messages() {
        return this.messages;
    }

    public SchedulerAdapter scheduler() {
        return this.scheduler;
    }

    public SettingsService settingsService() {
        return this.settingsService;
    }

    public CombatTracker combatTracker() {
        return this.combatTracker;
    }

    public PlayerVisibilityService visibilityService() {
        return this.visibilityService;
    }
}
