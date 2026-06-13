package com.nick.landclaims.plugin.listener;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.message.MessageService;
import com.nick.landclaims.plugin.selection.DoubleCrouchClearService;
import com.nick.landclaims.plugin.selection.SelectionService;
import com.nick.landclaims.plugin.tool.ClaimToolService;
import com.nick.landclaims.plugin.visual.BorderColor;
import com.nick.landclaims.plugin.visual.ClaimBorderColorService;
import com.nick.landclaims.plugin.visual.ChunkBorderVisualService;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
import org.bukkit.permissions.PermissionAttachmentInfo;

public class ClaimToolListener implements Listener {
    private static final String CLAIM_TOOL_PERMISSION = "landclaims.tool.use";

    private final ClaimToolService claimToolService;
    private final SelectionService selectionService;
    private final DoubleCrouchClearService doubleCrouchClearService;
    private final ChunkBorderVisualService chunkBorderVisualService;
    private final ClaimBorderColorService claimBorderColorService;
    private final ClaimIndex claimIndex;
    private final MessageService messageService;
    private boolean clearOnToolSwitch;
    private boolean doubleCrouchClearEnabled;

    public ClaimToolListener(ClaimToolService claimToolService, SelectionService selectionService) {
        this(claimToolService, selectionService, null, null, null, null, null, false, false);
    }

    public ClaimToolListener(
            ClaimToolService claimToolService,
            SelectionService selectionService,
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
        this.doubleCrouchClearService = doubleCrouchClearService;
        this.chunkBorderVisualService = chunkBorderVisualService;
        this.claimBorderColorService = claimBorderColorService;
        this.claimIndex = claimIndex;
        this.messageService = messageService;
        this.clearOnToolSwitch = clearOnToolSwitch;
        this.doubleCrouchClearEnabled = doubleCrouchClearEnabled;
    }

    public void reload(boolean newClearOnToolSwitch, boolean newDoubleCrouchClearEnabled) {
        this.clearOnToolSwitch = newClearOnToolSwitch;
        this.doubleCrouchClearEnabled = newDoubleCrouchClearEnabled;
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
        ClaimChunk selectedChunk = new ClaimChunk(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        Optional<Claim> clickedClaim = claimAt(selectedChunk);
        if (clickedClaim.isPresent() && !isOwnerClaim(player, clickedClaim.orElseThrow())) {
            showBorder(player, Set.of(selectedChunk), BorderColor.RED);
            player.sendMessage(message("claim.tool.point-claimed-other"));
            return;
        }
        if (clickedClaim.isPresent()) {
            player.sendMessage(message("claim.tool.point-claimed-own"));
        }

        selectionService.select(player, chunk).ifPresentOrElse(
                chunks -> {
                    Set<ClaimChunk> normalizedChunks = normalizeSelection(player, chunks);
                    if (normalizedChunks.isEmpty()) {
                        selectionService.replacePendingSelection(player.getUniqueId(), Set.of());
                        clickedClaim.ifPresent(claim -> showBorder(player, claim.claimChunks(), BorderColor.GOLD));
                        player.sendMessage(message("claim.tool.selection-only-existing"));
                        return;
                    }
                    if (!normalizedChunks.equals(chunks)) {
                        selectionService.replacePendingSelection(player.getUniqueId(), normalizedChunks);
                        player.sendMessage(message("claim.tool.selection-existing-trimmed"));
                    }
                    showBorder(player, normalizedChunks, previewColor(player, normalizedChunks));
                    sendSelectionComplete(player, normalizedChunks);
                },
                () -> {
                    Set<ClaimChunk> chunks = Set.of(selectedChunk);
                    if (clickedClaim.isPresent()) {
                        showBorder(player, clickedClaim.orElseThrow().claimChunks(), BorderColor.GOLD);
                    } else {
                        showBorder(player, chunks, previewColor(player, chunks));
                    }
                    player.sendMessage(message("claim.tool.first-corner-selected"));
                }
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

    private void sendSelectionComplete(Player player, Set<ClaimChunk> chunks) {
        player.sendMessage(message("claim.tool.selection-complete", java.util.Map.of(
                "chunk_count", String.valueOf(chunks.size())
        )));
    }

    private void sendMissingPermission(Player player) {
        player.sendMessage(message("command.tool.no-permission"));
    }

    private void sendSelectionCleared(Player player) {
        player.sendMessage(message("command.selection.cleared"));
    }

    private Set<ClaimChunk> normalizeSelection(Player player, Set<ClaimChunk> chunks) {
        if (claimIndex == null) {
            return chunks;
        }
        Set<ClaimChunk> newChunks = chunks.stream()
                .filter(chunk -> claimAt(chunk)
                        .filter(claim -> isOwnerClaim(player, claim))
                        .isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        return newChunks.size() == chunks.size() ? chunks : newChunks;
    }

    private Optional<Claim> claimAt(ClaimChunk chunk) {
        if (claimIndex == null) {
            return Optional.empty();
        }
        return claimIndex.findAt(chunk);
    }

    private boolean isOwnerClaim(Player player, Claim claim) {
        return claim.owner() == OwnerType.PLAYER && player.getUniqueId().equals(claim.ownerUuid());
    }

    private void showBorder(Player player, Set<ClaimChunk> chunks, BorderColor color) {
        if (chunkBorderVisualService != null) {
            chunkBorderVisualService.showSelection(player, chunks, color);
        }
    }

    private BorderColor previewColor(Player player, Set<ClaimChunk> chunks) {
        if (claimBorderColorService == null) {
            return BorderColor.GREEN;
        }
        return claimBorderColorService.colorForPlayerSelection(
                player.getUniqueId(),
                Optional.empty(),
                chunks,
                permissionNodes(player)
        );
    }

    private Set<String> permissionNodes(Player player) {
        return player.getEffectivePermissions().stream()
                .filter(PermissionAttachmentInfo::getValue)
                .map(PermissionAttachmentInfo::getPermission)
                .collect(Collectors.toUnmodifiableSet());
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
