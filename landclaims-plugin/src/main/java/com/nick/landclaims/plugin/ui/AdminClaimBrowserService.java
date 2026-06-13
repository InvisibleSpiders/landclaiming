package com.nick.landclaims.plugin.ui;

import com.nick.landclaims.plugin.admin.AdminClaimService;
import com.nick.landclaims.plugin.admin.DisbandResult;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.economy.ClaimPaymentService;
import com.nick.landclaims.plugin.limit.ClaimCostService;
import com.nick.landclaims.plugin.message.MessageService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AdminClaimBrowserService {
    private final AdminClaimService adminClaimService;
    private final ClaimCostService claimCostService;
    private final ClaimPaymentService claimPaymentService;
    private final MessageService messageService;

    public AdminClaimBrowserService(
            AdminClaimService adminClaimService,
            ClaimCostService claimCostService,
            ClaimPaymentService claimPaymentService,
            MessageService messageService
    ) {
        this.adminClaimService = Objects.requireNonNull(adminClaimService, "adminClaimService");
        this.claimCostService = Objects.requireNonNull(claimCostService, "claimCostService");
        this.claimPaymentService = Objects.requireNonNull(claimPaymentService, "claimPaymentService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    public void openBrowse(Player admin, UUID targetPlayerId) {
        List<Claim> claims = adminClaimService.listPlayerClaims(targetPlayerId);
        if (claims.isEmpty()) {
            admin.sendMessage(messageService.render("admin.userclaims.browse.empty",
                    Map.of("player", targetPlayerId.toString())));
            return;
        }
        List<Claim> displayed = claims.subList(0, Math.min(claims.size(), 45));
        int claimRows = (int) Math.ceil(displayed.size() / 9.0);
        int size = (claimRows + 1) * 9;

        BrowseHolder holder = new BrowseHolder(targetPlayerId);
        Inventory inv = Bukkit.createInventory(holder,
                size,
                Component.text("Claims: " + targetPlayerId.toString().substring(0, 8)));
        holder.setInventory(inv);

        // slot 0: Disband All button
        ItemStack disbandAll = new ItemStack(Material.TNT);
        ItemMeta disbandMeta = disbandAll.getItemMeta();
        disbandMeta.displayName(Component.text("Disband All").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        disbandMeta.lore(List.of(
                Component.text("Left-click: open disband confirmation")
                        .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        disbandAll.setItemMeta(disbandMeta);
        inv.setItem(0, disbandAll);

        // slots 9+: one item per claim
        for (int i = 0; i < displayed.size(); i++) {
            inv.setItem(9 + i, claimItem(displayed.get(i)));
        }

        admin.openInventory(inv);
    }

    public void openDisbandConfirmation(Player admin, UUID targetPlayerId) {
        ConfirmHolder holder = new ConfirmHolder(targetPlayerId);
        Inventory inv = Bukkit.createInventory(holder, 9,
                Component.text("Disband: confirm").color(NamedTextColor.RED));
        holder.setInventory(inv);

        // slot 1: refund
        ItemStack refundItem = new ItemStack(Material.LIME_WOOL);
        ItemMeta refundMeta = refundItem.getItemMeta();
        refundMeta.displayName(Component.text("Refund + Disband").color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        refundMeta.lore(List.of(Component.text("Deletes all claims and refunds economy cost.")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        refundItem.setItemMeta(refundMeta);
        inv.setItem(1, refundItem);

        // slot 4: cancel
        ItemStack cancelItem = new ItemStack(Material.GRAY_WOOL);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.displayName(Component.text("Cancel").color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        cancelItem.setItemMeta(cancelMeta);
        inv.setItem(4, cancelItem);

        // slot 7: no refund
        ItemStack noRefundItem = new ItemStack(Material.RED_WOOL);
        ItemMeta noRefundMeta = noRefundItem.getItemMeta();
        noRefundMeta.displayName(Component.text("Disband (No Refund)").color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        noRefundMeta.lore(List.of(Component.text("Deletes all claims. No refund.")
                .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        noRefundItem.setItemMeta(noRefundMeta);
        inv.setItem(7, noRefundItem);

        admin.openInventory(inv);
    }

    public void handleBrowseClick(InventoryClickEvent event, UUID targetPlayerId) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        int slot = event.getRawSlot();

        if (slot == 0) {
            openDisbandConfirmation(admin, targetPlayerId);
            return;
        }

        if (slot < 9) return;

        List<Claim> claims = adminClaimService.listPlayerClaims(targetPlayerId);
        int claimIndex = slot - 9;
        if (claimIndex >= claims.size()) return;
        Claim claim = claims.get(claimIndex);

        boolean shift = event.isShiftClick();
        if (shift) {
            adminClaimService.deletePlayerClaim(claim.id());
            admin.closeInventory();
            openBrowse(admin, targetPlayerId);
        } else {
            teleportToClaim(admin, claim);
        }
    }

    public void handleConfirmClick(InventoryClickEvent event, UUID targetPlayerId) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        int slot = event.getRawSlot();

        if (slot == 1) {
            DisbandResult result = adminClaimService.disbandPlayerClaims(
                    targetPlayerId, true, claimCostService, claimPaymentService);
            admin.closeInventory();
            admin.sendMessage(messageService.render("admin.userclaims.disband.success",
                    Map.of("count", String.valueOf(result.claimsDeleted()),
                           "amount", claimPaymentService.format(result.totalRefunded()))));
        } else if (slot == 4) {
            admin.closeInventory();
        } else if (slot == 7) {
            DisbandResult result = adminClaimService.disbandPlayerClaims(
                    targetPlayerId, false, claimCostService, claimPaymentService);
            admin.closeInventory();
            admin.sendMessage(messageService.render("admin.userclaims.disband.no-refund-success",
                    Map.of("count", String.valueOf(result.claimsDeleted()))));
        }
    }

    private void teleportToClaim(Player admin, Claim claim) {
        World world = Bukkit.getWorld(claim.worldId());
        if (world == null) {
            admin.sendMessage(Component.text("World not loaded.").color(NamedTextColor.RED));
            return;
        }
        ClaimChunk first = claim.claimChunks().iterator().next();
        int blockX = first.chunkX() * 16 + 8;
        int blockZ = first.chunkZ() * 16 + 8;
        int blockY = world.getHighestBlockYAt(blockX, blockZ) + 1;
        admin.teleport(new Location(world, blockX + 0.5, blockY, blockZ + 0.5));
    }

    private ItemStack claimItem(Claim claim) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(claim.name()).color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        String worldName = claim.worldId().toString();
        World world = Bukkit.getWorld(claim.worldId());
        if (world != null) worldName = world.getName();
        meta.lore(List.of(
                Component.text("Chunks: " + claim.claimChunks().size())
                        .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("World: " + worldName)
                        .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Left-click: Teleport")
                        .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("Shift+Left-click: Delete")
                        .color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }
}
