package dev.spruceworks.settings.gui;

import java.util.List;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marks an inventory as ours and remembers which toggle sits in which slot.
 *
 * <p>Using a holder rather than matching on the inventory title means a player
 * cannot spoof our GUI by renaming a container, and title changes in
 * messages.yml never break click handling.
 */
public final class SettingsGuiHolder implements InventoryHolder {

    private final List<String> slotToToggleKey;
    private Inventory inventory;

    public SettingsGuiHolder(List<String> slotToToggleKey) {
        this.slotToToggleKey = slotToToggleKey;
    }

    /** Toggle key at this raw slot, or null if the slot is decoration. */
    public String toggleKeyAt(int slot) {
        if (slot < 0 || slot >= this.slotToToggleKey.size()) {
            return null;
        }
        return this.slotToToggleKey.get(slot);
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}
