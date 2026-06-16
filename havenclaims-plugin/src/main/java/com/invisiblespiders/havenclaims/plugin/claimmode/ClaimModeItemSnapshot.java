package com.invisiblespiders.havenclaims.plugin.claimmode;

import org.bukkit.inventory.ItemStack;

public record ClaimModeItemSnapshot(
        String slot,
        String summary,
        String backup
) {
    public static ClaimModeItemSnapshot from(String slot, ItemStack item) {
        return new ClaimModeItemSnapshot(slot, ClaimModeItemCodec.summary(item), ClaimModeItemCodec.serialize(item));
    }

    public ItemStack restoreItem() {
        return ClaimModeItemCodec.deserialize(backup);
    }

    public boolean empty() {
        return backup == null || backup.isBlank();
    }
}
