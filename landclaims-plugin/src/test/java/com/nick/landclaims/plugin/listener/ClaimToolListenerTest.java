package com.nick.landclaims.plugin.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Chunk;
import org.bukkit.block.Block;
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
}
