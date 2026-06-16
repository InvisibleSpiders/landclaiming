package com.invisiblespiders.havenclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimRegionTest {
    private final UUID world = UUID.randomUUID();

    @Test
    void areaIsInclusiveBlockCount() {
        ClaimRegion region = new ClaimRegion(world, 0, 0, 3, 4);
        assertThat(region.area()).isEqualTo(4 * 5); // (3-0+1)*(4-0+1)
    }

    @Test
    void singleBlockAreaIsOne() {
        assertThat(new ClaimRegion(world, 5, 5, 5, 5).area()).isEqualTo(1);
    }

    @Test
    void containsBlockIncludesBoundary() {
        ClaimRegion r = new ClaimRegion(world, 10, 20, 20, 30);
        assertThat(r.containsBlock(10, 20)).isTrue();
        assertThat(r.containsBlock(20, 30)).isTrue();
        assertThat(r.containsBlock(15, 25)).isTrue();
        assertThat(r.containsBlock(9, 20)).isFalse();
        assertThat(r.containsBlock(21, 25)).isFalse();
    }

    @Test
    void overlappingChunksCoversAllTouchingChunks() {
        // Block (0..15,0..15) → exactly chunk (0,0)
        ClaimRegion single = new ClaimRegion(world, 0, 0, 15, 15);
        assertThat(single.overlappingChunks()).containsExactlyInAnyOrder(
                new ClaimChunk(world, 0, 0));

        // Block (0..16,0..0) → chunks (0,0) and (1,0)
        ClaimRegion twoChunks = new ClaimRegion(world, 0, 0, 16, 0);
        assertThat(twoChunks.overlappingChunks()).containsExactlyInAnyOrder(
                new ClaimChunk(world, 0, 0), new ClaimChunk(world, 1, 0));
    }

    @Test
    void overlappingChunksHandlesNegativeCoordinates() {
        // Block (-1,-1,-1,-1) → chunk (-1,-1) (floorDiv, not >>4)
        ClaimRegion neg = new ClaimRegion(world, -1, -1, -1, -1);
        assertThat(neg.overlappingChunks()).containsExactlyInAnyOrder(
                new ClaimChunk(world, -1, -1));
    }

    @Test
    void constructorRejectsInvertedBounds() {
        assertThatThrownBy(() -> new ClaimRegion(world, 5, 0, 4, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClaimRegion(world, 0, 5, 0, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
