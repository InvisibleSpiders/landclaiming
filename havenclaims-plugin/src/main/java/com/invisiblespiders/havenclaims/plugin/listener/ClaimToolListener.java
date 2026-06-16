package com.invisiblespiders.havenclaims.plugin.listener;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeService;
import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import com.invisiblespiders.havenclaims.plugin.selection.DoubleCrouchClearService;
import com.invisiblespiders.havenclaims.plugin.selection.SelectionService;
import com.invisiblespiders.havenclaims.plugin.tool.ClaimToolService;
import com.invisiblespiders.havenclaims.plugin.visual.BorderColor;
import com.invisiblespiders.havenclaims.plugin.visual.ClaimBorderColorService;
import com.invisiblespiders.havenclaims.plugin.visual.ChunkBorderVisualService;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class ClaimToolListener implements Listener {
    private static final String CLAIM_PERMISSION = "havenclaims.claim";

    private final ClaimToolService claimToolService;
    private final SelectionService selectionService;
    private ClaimModeService claimModeService;
    private final DoubleCrouchClearService doubleCrouchClearService;
    private final ChunkBorderVisualService chunkBorderVisualService;
    private final ClaimBorderColorService claimBorderColorService;
    private final ClaimIndex claimIndex;
    private final MessageService messageService;
    private boolean clearOnToolSwitch;
    private boolean doubleCrouchClearEnabled;

    public ClaimToolListener(ClaimToolService claimToolService, SelectionService selectionService) {
        this(claimToolService, selectionService, null, null, null, null, null, null, false, false);
    }

    public ClaimToolListener(
            ClaimToolService claimToolService,
            SelectionService selectionService,
            ClaimModeService claimModeService
    ) {
        this(claimToolService, selectionService, claimModeService, null, null, null, null, null, false, false);
    }

    public ClaimToolListener(
            ClaimToolService claimToolService,
            SelectionService selectionService,
            ClaimModeService claimModeService,
            DoubleCrouchClearService doubleCrouchClearService,
            ChunkBorderVisualService chunkBorderVisualService,
            ClaimBorderColorService claimBorderColorService,
            ClaimIndex claimIndex,
            MessageService messageService,
            boolean clearOnToolSwitch,
            boolean doubleCrouchClearEnabled
    ) {
        this.claimToolService = Objects.requireNonNull(claimToolService, "claimToolService");
        this.selectionService = Objects.requireNonNull(selectionService, "selectionService");
        this.claimModeService = claimModeService;
        this.doubleCrouchClearService = doubleCrouchClearService;
        this.chunkBorderVisualService = chunkBorderVisualService;
        this.claimBorderColorService = claimBorderColorService;
        this.claimIndex = claimIndex;
        this.messageService = messageService;
        this.clearOnToolSwitch = clearOnToolSwitch;
        this.doubleCrouchClearEnabled = doubleCrouchClearEnabled;
    }

    public void setClaimModeService(ClaimModeService claimModeService) {
        this.claimModeService = claimModeService;
    }

    public void reload(boolean newClearOnToolSwitch, boolean newDoubleCrouchClearEnabled) {
        this.clearOnToolSwitch = newClearOnToolSwitch;
        this.doubleCrouchClearEnabled = newDoubleCrouchClearEnabled;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isSelectionAction(event.getAction())) {
            return;
        }

        ItemStack itemStack = event.getItem();
        if (!claimToolService.isClaimTool(itemStack)) {
            return;
        }
        Player player = event.getPlayer();
        if (claimModeService != null && !claimModeService.isInClaimMode(player.getUniqueId())) {
            return;
        }

        handleClaimToolSelection(event);
    }

    public void handleClaimToolSelection(PlayerInteractEvent event) {
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission(CLAIM_PERMISSION)) {
            sendMissingPermission(player);
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            clickedBlock = player.getLocation().getBlock();
        }

        selectionService.select(player, clickedBlock).ifPresentOrElse(
                region -> {
                    showRegionBorder(player, region, BorderColor.GREEN);
                    sendSelectionComplete(player, region);
                },
                () -> player.sendMessage(message("claim.tool.first-corner-selected"))
        );
    }

    private void sendSelectionComplete(Player player, com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion region) {
        player.sendMessage(message("claim.tool.selection-complete", java.util.Map.of(
                "area", String.valueOf(region.area())
        )));
    }

    private void showRegionBorder(Player player, com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion region, BorderColor color) {
        if (chunkBorderVisualService != null) {
            chunkBorderVisualService.showSelection(player, region, color);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        if (!claimToolService.isClaimTool(event.getMainHandItem())
                && !claimToolService.isClaimTool(event.getOffHandItem())) {
            return;
        }

        event.setCancelled(true);
        if (!player.hasPermission(CLAIM_PERMISSION)) {
            sendMissingPermission(player);
            return;
        }

        player.performCommand("claims menu");
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        if (!clearOnToolSwitch) {
            return;
        }

        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        ItemStack previousItem = inventory.getItem(event.getPreviousSlot());
        ItemStack newItem = inventory.getItem(event.getNewSlot());
        if (shouldClearOnToolSwitch(claimToolService, previousItem, newItem) && selectionService.clear(player)) {
            clearBorder(player);
            sendSelectionCleared(player);
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (!doubleCrouchClearEnabled || doubleCrouchClearService == null || !event.isSneaking()) {
            return;
        }

        Player player = event.getPlayer();
        if (doubleCrouchClearService.recordCrouch(player.getUniqueId()) && selectionService.clear(player)) {
            clearBorder(player);
            sendSelectionCleared(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        selectionService.clear(player);
        if (doubleCrouchClearService != null) {
            doubleCrouchClearService.clear(player.getUniqueId());
        }
        clearBorder(player);
    }

    private boolean isSelectionAction(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    static Chunk selectionChunk(Block clickedBlock, Chunk playerChunk) {
        return clickedBlock != null ? clickedBlock.getChunk() : playerChunk;
    }

    static boolean shouldClearOnToolSwitch(ClaimToolService claimToolService, ItemStack previousItem, ItemStack newItem) {
        Objects.requireNonNull(claimToolService, "claimToolService");
        return claimToolService.isClaimTool(previousItem) && !claimToolService.isClaimTool(newItem);
    }

    private void sendMissingPermission(Player player) {
        player.sendMessage(message("command.claim.no-permission"));
    }

    private void sendSelectionCleared(Player player) {
        player.sendMessage(message("command.selection.cleared"));
    }

    private Component message(String key) {
        return message(key, java.util.Map.of());
    }

    private Component message(String key, java.util.Map<String, String> placeholders) {
        if (messageService == null) {
            return Component.text(key, NamedTextColor.YELLOW);
        }
        return messageService.render(key, placeholders);
    }

    private void clearBorder(Player player) {
        if (chunkBorderVisualService != null) {
            chunkBorderVisualService.clear(player.getUniqueId());
        }
    }
}
