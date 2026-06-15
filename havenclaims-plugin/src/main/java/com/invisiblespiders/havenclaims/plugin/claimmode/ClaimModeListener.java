package com.invisiblespiders.havenclaims.plugin.claimmode;

import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public final class ClaimModeListener implements Listener {
    private static final int PLAYER_OFFHAND_SLOT = 40;

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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!isActive(player)) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage(message("claim-mode.blocked-swap", Map.of()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isActive(player)) {
            return;
        }

        if (touchesNonPlayerTopInventory(event)
                || touchesGuardedClickSlot(event)
                || isClaimModeTool(event.getCurrentItem())
                || isClaimModeTool(event.getCursor())) {
            event.setCancelled(true);
            player.sendMessage(message("claim-mode.blocked-inventory", Map.of()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isActive(player)) {
            return;
        }

        boolean touchesExternalInventory = event.getRawSlots().stream()
                .anyMatch(slot -> touchesNonPlayerTopInventory(event.getView(), slot));
        boolean touchesGuardedSlot = event.getRawSlots().stream()
                .anyMatch(slot -> touchesGuardedRawSlot(event, slot));
        boolean movesClaimModeTool = event.getNewItems().values().stream().anyMatch(this::isClaimModeTool)
                || isClaimModeTool(event.getOldCursor())
                || isClaimModeTool(event.getCursor());
        if (touchesExternalInventory || touchesGuardedSlot || movesClaimModeTool) {
            event.setCancelled(true);
            player.sendMessage(message("claim-mode.blocked-inventory", Map.of()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isActive(player)) {
            return;
        }

        ItemStack item = event.getItem();
        if (isClaimModeTool(item)) {
            cancelBaseInteraction(event);
            claimModeService.toolRegistry().resolve(item).ifPresent(tool -> {
                if (tool.enabled()) {
                    tool.handler().handle(player, event);
                    return;
                }
                player.sendMessage(message(tool.disabledMessageKey(), Map.of()));
            });
            return;
        }

        if (isContainerInteraction(event)) {
            cancelBaseInteraction(event);
            player.sendMessage(message("claim-mode.blocked-inventory", Map.of()));
        }
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

    private static boolean touchesGuardedClickSlot(InventoryClickEvent event) {
        return event.getSlotType() == InventoryType.SlotType.QUICKBAR
                || isBottomOffhandSlot(event)
                || event.getClick() == ClickType.SWAP_OFFHAND
                || event.getClick() == ClickType.NUMBER_KEY
                || event.getHotbarButton() == PLAYER_OFFHAND_SLOT
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

    private static boolean touchesNonPlayerTopInventory(InventoryClickEvent event) {
        InventoryView view = event.getView();
        if (!hasNonPlayerTopInventory(view)) {
            return false;
        }
        return Objects.equals(event.getClickedInventory(), view.getTopInventory())
                || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || touchesNonPlayerTopInventory(view, event.getRawSlot());
    }

    private static boolean touchesNonPlayerTopInventory(InventoryView view, int rawSlot) {
        if (!hasNonPlayerTopInventory(view) || rawSlot < 0) {
            return false;
        }
        return Objects.equals(view.getInventory(rawSlot), view.getTopInventory());
    }

    private static boolean hasNonPlayerTopInventory(InventoryView view) {
        if (view == null) {
            return false;
        }
        InventoryType type = view.getType();
        if (type != null && "CRAFTING".equals(type.name())) {
            return false;
        }
        Inventory top = view.getTopInventory();
        Inventory bottom = view.getBottomInventory();
        return top != null && bottom != null && !Objects.equals(top, bottom);
    }

    private static boolean isContainerInteraction(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return false;
        }
        return block.getState() instanceof Container || block.getType() == Material.ENDER_CHEST;
    }

    private static void cancelBaseInteraction(PlayerInteractEvent event) {
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
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
