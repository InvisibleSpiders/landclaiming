package com.invisiblespiders.havenclaims.plugin.claimmode;

import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public final class ClaimModeListener implements Listener {
    private static final int PLAYER_HOTBAR_START_RAW_SLOT = 36;
    private static final int PLAYER_HOTBAR_END_RAW_SLOT = 44;
    private static final int PLAYER_OFFHAND_SLOT = 40;
    private static final int PLAYER_OFFHAND_RAW_SLOT = 45;

    private final ClaimModeService claimModeService;
    private final ClaimModeCommandGuard commandGuard;
    private final MessageService messageService;

    public ClaimModeListener(
            ClaimModeService claimModeService,
            ClaimModeCommandGuard commandGuard,
            MessageService messageService
    ) {
        this.claimModeService = Objects.requireNonNull(claimModeService, "claimModeService");
        this.commandGuard = Objects.requireNonNull(commandGuard, "commandGuard");
        this.messageService = messageService;
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isActive(player) || !commandGuard.isBlocked(event.getMessage())) {
            return;
        }

        String command = ClaimModeConfig.normalizeCommandLabel(event.getMessage());
        event.setCancelled(true);
        player.sendMessage(message("claim-mode.blocked-command", Map.of("command", command)));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!isActive(player)) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(message("claim-mode.blocked-drop", Map.of()));
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player) || !isActive(player)) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(message("claim-mode.blocked-pickup", Map.of()));
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!isActive(player)) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(message("claim-mode.blocked-swap", Map.of()));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isActive(player)) {
            return;
        }

        if (touchesGuardedClickSlot(event)
                || isClaimModeTool(event.getCurrentItem())
                || isClaimModeTool(event.getCursor())) {
            event.setCancelled(true);
            player.sendMessage(message("claim-mode.blocked-inventory", Map.of()));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isActive(player)) {
            return;
        }

        boolean touchesGuardedSlot = event.getRawSlots().stream()
                .anyMatch(slot -> touchesGuardedRawSlot(event, slot));
        boolean movesClaimModeTool = event.getNewItems().values().stream().anyMatch(this::isClaimModeTool)
                || isClaimModeTool(event.getOldCursor())
                || isClaimModeTool(event.getCursor());
        if (touchesGuardedSlot || movesClaimModeTool) {
            event.setCancelled(true);
            player.sendMessage(message("claim-mode.blocked-inventory", Map.of()));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isActive(player) || !isClaimModeTool(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        claimModeService.toolRegistry().resolve(event.getItem()).ifPresent(tool -> {
            if (tool.enabled()) {
                tool.handler().handle(player, event);
                return;
            }
            player.sendMessage(message(tool.disabledMessageKey(), Map.of()));
        });
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isActive(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isActive(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        claimModeService.exit(event.getPlayer(), ClaimModeService.ExitReason.LOGOUT);
    }

    @SuppressWarnings("removal")
    static boolean touchesHotbar(int slot, int rawSlot, ClickType clickType, InventoryAction action) {
        return (slot >= 0 && slot <= 8)
                || slot == PLAYER_OFFHAND_SLOT
                || (rawSlot >= PLAYER_HOTBAR_START_RAW_SLOT && rawSlot <= PLAYER_HOTBAR_END_RAW_SLOT)
                || rawSlot == PLAYER_OFFHAND_RAW_SLOT
                || clickType == ClickType.NUMBER_KEY
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.HOTBAR_MOVE_AND_READD;
    }

    private static boolean touchesGuardedClickSlot(InventoryClickEvent event) {
        return event.getSlotType() == InventoryType.SlotType.QUICKBAR
                || isBottomOffhandSlot(event)
                || event.getClick() == ClickType.NUMBER_KEY
                || isHotbarAction(event.getAction());
    }

    private static boolean isBottomOffhandSlot(InventoryClickEvent event) {
        return event.getSlot() == PLAYER_OFFHAND_SLOT
                && Objects.equals(event.getClickedInventory(), event.getView().getBottomInventory());
    }

    private static boolean touchesGuardedRawSlot(InventoryDragEvent event, int rawSlot) {
        return event.getView().getSlotType(rawSlot) == InventoryType.SlotType.QUICKBAR
                || (event.getView().convertSlot(rawSlot) == PLAYER_OFFHAND_SLOT
                && Objects.equals(event.getView().getInventory(rawSlot), event.getView().getBottomInventory()));
    }

    @SuppressWarnings("removal")
    private static boolean isHotbarAction(InventoryAction action) {
        return action == InventoryAction.HOTBAR_SWAP || action == InventoryAction.HOTBAR_MOVE_AND_READD;
    }

    private boolean isActive(Player player) {
        return claimModeService.isInClaimMode(player.getUniqueId());
    }

    private boolean isClaimModeTool(ItemStack item) {
        return claimModeService.toolRegistry().isClaimModeTool(item);
    }

    private Component message(String key, Map<String, String> placeholders) {
        if (messageService == null) {
            return claimModeService.fallbackMessage();
        }
        return messageService.render(key, placeholders);
    }
}
