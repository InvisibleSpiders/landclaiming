package com.invisiblespiders.havenclaims.plugin.ui;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ConfirmHolder implements InventoryHolder {
    private final UUID targetPlayerId;
    private Inventory inventory;

    public ConfirmHolder(UUID targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }

    public UUID getTargetPlayerId() { return targetPlayerId; }

    public void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
