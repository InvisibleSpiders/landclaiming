package com.nick.landclaims.plugin.listener;

import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.selection.DoubleCrouchClearService;
import com.nick.landclaims.plugin.selection.SelectionService;
import com.nick.landclaims.plugin.tool.ClaimToolService;
import java.util.Objects;
import java.util.Set;
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
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class ClaimToolListener implements Listener {
    private static final String CLAIM_TOOL_PERMISSION = "landclaims.tool.use";

    private final ClaimToolService claimToolService;
    private final SelectionService selectionService;
    private final DoubleCrouchClearService doubleCrouchClearService;
    private final boolean clearOnToolSwitch;
    private final boolean doubleCrouchClearEnabled;

    public ClaimToolListener(ClaimToolService claimToolService, SelectionService selectionService) {
        this(claimToolService, selectionService, null, false, false);
    }

    public ClaimToolListener(
            ClaimToolService claimToolService,
            SelectionService selectionService,
            DoubleCrouchClearService doubleCrouchClearService,
            boolean clearOnToolSwitch,
            boolean doubleCrouchClearEnabled
    ) {
        this.claimToolService = Objects.requireNonNull(claimToolService, "claimToolService");
        this.selectionService = Objects.requireNonNull(selectionService, "selectionService");
        this.doubleCrouchClearService = doubleCrouchClearService;
        this.clearOnToolSwitch = clearOnToolSwitch;
        this.doubleCrouchClearEnabled = doubleCrouchClearEnabled;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !isSelectionAction(event.getAction())) {
            return;
        }

        ItemStack itemStack = event.getItem();
        if (!claimToolService.isClaimTool(itemStack)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission(CLAIM_TOOL_PERMISSION)) {
            sendMissingPermission(player);
            return;
        }

        Chunk chunk = selectionChunk(event.getClickedBlock(), player.getLocation().getChunk());
        selectionService.select(player, chunk).ifPresentOrElse(
                chunks -> sendSelectionComplete(player, chunks),
                () -> player.sendMessage(Component.text("First claim corner selected.", NamedTextColor.YELLOW))
        );
    }

    @EventHandler
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
        if (!player.hasPermission(CLAIM_TOOL_PERMISSION)) {
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
        if (shouldClearOnToolSwitch(claimToolService, previousItem, newItem)) {
            selectionService.clear(player);
            sendSelectionCleared(player);
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (!doubleCrouchClearEnabled || doubleCrouchClearService == null || !event.isSneaking()) {
            return;
        }

        Player player = event.getPlayer();
        if (doubleCrouchClearService.recordCrouch(player.getUniqueId())) {
            selectionService.clear(player);
            sendSelectionCleared(player);
        }
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

    private void sendSelectionComplete(Player player, Set<ClaimChunk> chunks) {
        player.sendMessage(Component.text("Claim selection contains ", NamedTextColor.GREEN)
                .append(Component.text(chunks.size(), NamedTextColor.YELLOW))
                .append(Component.text(" chunks.", NamedTextColor.GREEN)));
    }

    private void sendMissingPermission(Player player) {
        player.sendMessage(Component.text("You do not have permission to use the claim tool.", NamedTextColor.RED));
    }

    private void sendSelectionCleared(Player player) {
        player.sendMessage(Component.text("Claim selection cleared.", NamedTextColor.YELLOW));
    }
}
