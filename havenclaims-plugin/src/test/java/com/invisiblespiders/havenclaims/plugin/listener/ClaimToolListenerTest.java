package com.invisiblespiders.havenclaims.plugin.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimService;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeService;
import com.invisiblespiders.havenclaims.plugin.selection.SelectionService;
import com.invisiblespiders.havenclaims.plugin.tool.ClaimToolService;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class ClaimToolListenerTest {
    @Test
    void selectionChunkUsesClickedBlockChunkWhenPresent() {
        Chunk clickedChunk = mock(Chunk.class);
        Chunk playerChunk = mock(Chunk.class);
        Block clickedBlock = mock(Block.class);
        when(clickedBlock.getChunk()).thenReturn(clickedChunk);

        Chunk result = ClaimToolListener.selectionChunk(clickedBlock, playerChunk);

        assertThat(result).isSameAs(clickedChunk);
    }

    @Test
    void selectionChunkFallsBackToPlayerChunkWithoutClickedBlock() {
        Chunk playerChunk = mock(Chunk.class);

        Chunk result = ClaimToolListener.selectionChunk(null, playerChunk);

        assertThat(result).isSameAs(playerChunk);
    }

    @Test
    void shouldClearWhenSwitchingFromClaimToolToNormalItem() {
        ClaimToolService toolService = mock(ClaimToolService.class);
        ItemStack claimTool = mock(ItemStack.class);
        ItemStack normalItem = mock(ItemStack.class);
        when(toolService.isClaimTool(claimTool)).thenReturn(true);
        when(toolService.isClaimTool(normalItem)).thenReturn(false);

        assertThat(ClaimToolListener.shouldClearOnToolSwitch(toolService, claimTool, normalItem)).isTrue();
    }

    @Test
    void shouldNotClearWhenSwitchingBetweenNonClaimTools() {
        ClaimToolService toolService = mock(ClaimToolService.class);
        ItemStack first = mock(ItemStack.class);
        ItemStack second = mock(ItemStack.class);
        when(toolService.isClaimTool(first)).thenReturn(false);
        when(toolService.isClaimTool(second)).thenReturn(false);

        assertThat(ClaimToolListener.shouldClearOnToolSwitch(toolService, first, second)).isFalse();
    }

    @Test
    void ignoresClaimToolSelectionOutsideClaimModeWhenModeServiceIsAvailable() {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        SelectionService selectionService = new SelectionService(new ClaimService());
        ClaimToolService toolService = mock(ClaimToolService.class);
        ClaimModeService claimModeService = mock(ClaimModeService.class);
        ClaimToolListener listener = new ClaimToolListener(toolService, selectionService, claimModeService);
        ItemStack claimModeTool = mock(ItemStack.class);
        Player player = playerAt(playerId, worldId, 0, 0);
        when(toolService.isClaimTool(claimModeTool)).thenReturn(true);
        when(claimModeService.isInClaimMode(playerId)).thenReturn(false);

        PlayerInteractEvent first = interact(player, claimModeTool, block(chunk(worldId, 1, 0)));
        PlayerInteractEvent second = interact(player, claimModeTool, block(chunk(worldId, 2, 0)));
        listener.onPlayerInteract(first);
        listener.onPlayerInteract(second);

        assertThat(first.isCancelled()).isFalse();
        assertThat(second.isCancelled()).isFalse();
        assertThat(selectionService.pendingSelection(playerId)).isEmpty();
    }

    @Test
    void processesClaimToolSelectionInsideClaimModeWithClaimPermission() {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        SelectionService selectionService = new SelectionService(new ClaimService());
        ClaimToolService toolService = mock(ClaimToolService.class);
        ClaimModeService claimModeService = mock(ClaimModeService.class);
        ClaimToolListener listener = new ClaimToolListener(toolService, selectionService, claimModeService);
        ItemStack claimModeTool = mock(ItemStack.class);
        Player player = playerAt(playerId, worldId, 0, 0);
        when(player.hasPermission("havenclaims.claim")).thenReturn(true);
        when(player.getEffectivePermissions()).thenReturn(Set.of());
        when(toolService.isClaimTool(claimModeTool)).thenReturn(true);
        when(claimModeService.isInClaimMode(playerId)).thenReturn(true);

        PlayerInteractEvent first = interact(player, claimModeTool, block(chunk(worldId, 1, 0)));
        PlayerInteractEvent second = interact(player, claimModeTool, block(chunk(worldId, 2, 0)));
        listener.onPlayerInteract(first);
        listener.onPlayerInteract(second);

        assertThat(first.isCancelled()).isTrue();
        assertThat(second.isCancelled()).isTrue();
        assertThat(selectionService.pendingSelection(playerId))
                .contains(Set.of(new ClaimChunk(worldId, 1, 0), new ClaimChunk(worldId, 2, 0)));
    }

    private static PlayerInteractEvent interact(Player player, ItemStack item, Block block) {
        return new PlayerInteractEvent(
                player,
                Action.RIGHT_CLICK_BLOCK,
                item,
                block,
                BlockFace.UP,
                EquipmentSlot.HAND
        );
    }

    private static Player playerAt(UUID playerId, UUID worldId, int chunkX, int chunkZ) {
        Player player = mock(Player.class);
        Location location = mock(Location.class);
        Chunk chunk = chunk(worldId, chunkX, chunkZ);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getLocation()).thenReturn(location);
        when(location.getChunk()).thenReturn(chunk);
        return player;
    }

    private static Block block(Chunk chunk) {
        Block block = mock(Block.class);
        when(block.getChunk()).thenReturn(chunk);
        return block;
    }

    private static Chunk chunk(UUID worldId, int chunkX, int chunkZ) {
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(world.getUID()).thenReturn(worldId);
        when(chunk.getX()).thenReturn(chunkX);
        when(chunk.getZ()).thenReturn(chunkZ);
        return chunk;
    }
}
