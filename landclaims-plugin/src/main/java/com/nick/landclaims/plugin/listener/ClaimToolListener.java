package com.nick.landclaims.plugin.listener;

import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.selection.SelectionService;
import com.nick.landclaims.plugin.tool.ClaimToolService;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ClaimToolListener implements Listener {
    private final ClaimToolService claimToolService;
    private final SelectionService selectionService;

    public ClaimToolListener(ClaimToolService claimToolService, SelectionService selectionService) {
        this.claimToolService = Objects.requireNonNull(claimToolService, "claimToolService");
        this.selectionService = Objects.requireNonNull(selectionService, "selectionService");
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
        Chunk chunk = player.getLocation().getChunk();
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
        player.performCommand("claims menu");
    }

    private boolean isSelectionAction(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private void sendSelectionComplete(Player player, Set<ClaimChunk> chunks) {
        player.sendMessage(Component.text("Claim selection contains ", NamedTextColor.GREEN)
                .append(Component.text(chunks.size(), NamedTextColor.YELLOW))
                .append(Component.text(" chunks.", NamedTextColor.GREEN)));
    }
}
