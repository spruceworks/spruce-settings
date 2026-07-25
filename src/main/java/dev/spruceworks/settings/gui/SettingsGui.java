package dev.spruceworks.settings.gui;

import dev.spruceworks.settings.SpruceSettingsPlugin;
import dev.spruceworks.settings.api.SettingToggle;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Builds and opens the {@code /settings} GUI.
 *
 * <p>Only toggles the viewer actually has permission for are shown — a greyed
 * out row of things they cannot use is worse than a shorter menu.
 */
public final class SettingsGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int ROW = 9;
    private static final int MAX_ROWS = 6;

    private final SpruceSettingsPlugin plugin;
    private final Player viewer;

    public SettingsGui(SpruceSettingsPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    public void open() {
        List<SettingToggle> visible = this.plugin.settingsService().toggles().stream()
                .filter(t -> t.permission() == null || this.viewer.hasPermission(t.permission()))
                .toList();

        int rows = Math.min(MAX_ROWS, Math.max(1, (visible.size() + ROW - 1) / ROW));
        int size = rows * ROW;

        List<String> slotMap = new ArrayList<>(java.util.Collections.nCopies(size, null));
        SettingsGuiHolder holder = new SettingsGuiHolder(slotMap);
        Inventory inventory = Bukkit.createInventory(holder, size,
                this.plugin.messages().get("gui-title"));
        holder.setInventory(inventory);

        int slot = 0;
        for (SettingToggle toggle : visible) {
            if (slot >= size) {
                // More toggles than one page holds. Paging is a v1.1 item; for now
                // log it rather than silently dropping entries.
                this.plugin.getSLF4JLogger().warn(
                        "More registered toggles ({}) than fit in the {}-slot GUI — {} not shown. "
                                + "Paging is not implemented yet.",
                        visible.size(), size, visible.size() - size);
                break;
            }
            inventory.setItem(slot, buildItem(toggle));
            slotMap.set(slot, toggle.key());
            slot++;
        }

        this.viewer.openInventory(inventory);
    }

    private ItemStack buildItem(SettingToggle toggle) {
        boolean enabled = this.plugin.settingsService().isEnabled(this.viewer.getUniqueId(), toggle.key());
        ItemStack item = new ItemStack(toggle.icon());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(decorate(MM.deserialize(toggle.displayName())));

        List<Component> lore = new ArrayList<>();
        for (String line : toggle.description()) {
            lore.add(decorate(MM.deserialize(line)));
        }
        lore.add(Component.empty());
        lore.add(decorate(this.plugin.messages().get(
                enabled ? "gui-state-on" : "gui-state-off")));
        lore.add(decorate(this.plugin.messages().get("gui-click-hint")));
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    /** Strips the italic default Minecraft applies to renamed items. */
    private Component decorate(Component component) {
        return component.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
    }

    /** Rebuilds the open GUI in place after a click, so state flips visibly. */
    public static void refresh(SpruceSettingsPlugin plugin, Player viewer) {
        new SettingsGui(plugin, viewer).open();
    }
}
