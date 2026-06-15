package com.invisiblespiders.havenclaims.plugin.listener;

import com.invisiblespiders.havenclaims.plugin.ui.AdminClaimBrowserService;
import com.invisiblespiders.havenclaims.plugin.ui.BrowseHolder;
import com.invisiblespiders.havenclaims.plugin.ui.ConfirmHolder;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class AdminClaimBrowserListener implements Listener {
    private final AdminClaimBrowserService browserService;

    public AdminClaimBrowserListener(AdminClaimBrowserService browserService) {
        this.browserService = Objects.requireNonNull(browserService, "browserService");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getClickedInventory();
        if (inv == null) return;
        InventoryHolder holder = inv.getHolder();

        if (holder instanceof BrowseHolder browseHolder) {
            browserService.handleBrowseClick(event, browseHolder.getTargetPlayerId());
        } else if (holder instanceof ConfirmHolder confirmHolder) {
            browserService.handleConfirmClick(event, confirmHolder.getTargetPlayerId());
        }
    }
}
