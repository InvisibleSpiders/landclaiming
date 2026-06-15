package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public record ClaimModeTool(
        String id,
        int slot,
        Supplier<ItemStack> itemFactory,
        boolean enabled,
        String disabledMessageKey,
        ClaimModeToolHandler handler
) {
    public ClaimModeTool {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (slot < 0 || slot > 8) {
            throw new IllegalArgumentException("slot must be in hotbar range 0-8");
        }
        itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
        disabledMessageKey = disabledMessageKey == null ? "" : disabledMessageKey;
        handler = Objects.requireNonNull(handler, "handler");
    }

    @FunctionalInterface
    public interface ClaimModeToolHandler {
        void handle(Player player, PlayerInteractEvent event);
    }
}
