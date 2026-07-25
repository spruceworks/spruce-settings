package dev.spruceworks.settings.gui;

import dev.spruceworks.settings.SpruceSettingsPlugin;
import dev.spruceworks.settings.api.SettingToggle;
import dev.spruceworks.settings.combat.CombatGate;
import dev.spruceworks.settings.toggle.BuiltInToggles;
import java.time.Instant;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Handles clicks in the {@code /settings} GUI, including the combat gate that
 * stops {@code pvp-opt-out} being used as an escape hatch mid-fight.
 */
public final class GuiListener implements Listener {

    private final SpruceSettingsPlugin plugin;

    public GuiListener(SpruceSettingsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SettingsGuiHolder holder)) {
            return;
        }
        // Our inventory: nothing in it may ever be picked up or moved.
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof SettingsGuiHolder)) {
            return; // clicked their own inventory below the GUI
        }

        String key = holder.toggleKeyAt(event.getRawSlot());
        if (key == null) {
            return; // decoration slot
        }
        SettingToggle toggle = this.plugin.settingsService().toggle(key).orElse(null);
        if (toggle == null) {
            return; // registered toggle vanished (plugin unloaded mid-session)
        }
        if (toggle.permission() != null && !player.hasPermission(toggle.permission())) {
            this.plugin.messages().send(player, "no-permission");
            return;
        }

        boolean current = this.plugin.settingsService().isEnabled(player.getUniqueId(), key);

        // ---- combat gate, pvp-opt-out only, enabling only ----
        if (BuiltInToggles.PVP_OPT_OUT.equals(key) && !current) {
            int window = this.plugin.configManager().config().getInt("pvp-opt-out.combat-window-seconds", 15);
            Instant lastDamage = this.plugin.combatTracker().lastDamageAt(player.getUniqueId());
            Instant now = Instant.now();
            if (!CombatGate.canEnableProtection(lastDamage, now, window)) {
                long left = CombatGate.remainingSeconds(lastDamage, now, window);
                this.plugin.messages().send(player, "pvp-blocked-in-combat",
                        Placeholder.unparsed("seconds", String.valueOf(left)));
                return;
            }
        }

        boolean next = this.plugin.settingsService().toggleValue(player.getUniqueId(), key);

        // hide-players needs immediate re-application, not just a stored value.
        if (BuiltInToggles.HIDE_PLAYERS.equals(key)) {
            this.plugin.visibilityService().applyForViewer(player);
        }

        this.plugin.messages().send(player, next ? "toggle-enabled" : "toggle-disabled",
                Placeholder.component("toggle",
                        net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                .deserialize(toggle.displayName())));

        SettingsGui.refresh(this.plugin, player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SettingsGuiHolder) {
            event.setCancelled(true);
        }
    }
}
