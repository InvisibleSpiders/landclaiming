package com.nick.landclaims.plugin.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nick.landclaims.plugin.tool.ClaimToolService;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
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
}
