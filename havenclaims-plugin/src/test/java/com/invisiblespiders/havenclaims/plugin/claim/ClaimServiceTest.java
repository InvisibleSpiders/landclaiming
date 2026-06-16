package com.invisiblespiders.havenclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.selection.BlockPos;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimServiceTest {
    @Test
    void expandsInclusiveRectangleRegardlessOfCornerOrder() {
        UUID worldId = UUID.randomUUID();
        ClaimService claimService = new ClaimService();

        Set<ClaimChunk> chunks = claimService.expandRectangle(worldId, 0, 0, 2, 1);
        Set<ClaimChunk> reversedChunks = claimService.expandRectangle(worldId, 2, 1, 0, 0);

        assertThat(chunks).containsExactlyInAnyOrder(
                new ClaimChunk(worldId, 0, 0),
                new ClaimChunk(worldId, 0, 1),
                new ClaimChunk(worldId, 1, 0),
                new ClaimChunk(worldId, 1, 1),
                new ClaimChunk(worldId, 2, 0),
                new ClaimChunk(worldId, 2, 1)
        );
        assertThat(reversedChunks).isEqualTo(chunks);
    }

    @Test
    void chunkBufferUsesChebyshevDistanceWithinSameWorldOnly() {
        UUID worldId = UUID.randomUUID();
        ClaimService claimService = new ClaimService();

        ClaimChunk proposed = new ClaimChunk(worldId, 10, 10);

        assertThat(claimService.isWithinChunkBuffer(proposed, new ClaimChunk(worldId, 12, 9), 2)).isTrue();
        assertThat(claimService.isWithinChunkBuffer(proposed, new ClaimChunk(worldId, 13, 10), 2)).isFalse();
        assertThat(claimService.isWithinChunkBuffer(proposed, new ClaimChunk(UUID.randomUUID(), 10, 10), 2)).isFalse();
    }

    @Test
    void chunkBufferRejectsNegativeDistance() {
        UUID worldId = UUID.randomUUID();
        ClaimService claimService = new ClaimService();

        ClaimChunk proposed = new ClaimChunk(worldId, 10, 10);
        ClaimChunk existing = new ClaimChunk(worldId, 11, 10);

        assertThatThrownBy(() -> claimService.isWithinChunkBuffer(proposed, existing, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsAllowedAndDeniedValidationResults() {
        ClaimValidationResult allowed = ClaimValidationResult.allowed();
        ClaimValidationResult denied = ClaimValidationResult.denied("claims.too-close");

        assertThat(allowed.isAllowed()).isTrue();
        assertThat(allowed.messageKey()).isEmpty();
        assertThat(denied.isAllowed()).isFalse();
        assertThat(denied.messageKey()).contains("claims.too-close");
    }

    @Test
    void blockRectangleNormalizesCornerOrder() {
        UUID world = UUID.randomUUID();
        ClaimService service = new ClaimService();
        BlockPos p1 = new BlockPos(world, 10, 20);
        BlockPos p2 = new BlockPos(world, 5, 30);
        ClaimRegion region = service.blockRectangle(p1, p2);
        assertThat(region).isEqualTo(new ClaimRegion(world, 5, 20, 10, 30));
        assertThat(service.blockRectangle(p2, p1)).isEqualTo(region);
    }

    @Test
    void blockRectangleRejectsDifferentWorlds() {
        ClaimService service = new ClaimService();
        BlockPos p1 = new BlockPos(UUID.randomUUID(), 0, 0);
        BlockPos p2 = new BlockPos(UUID.randomUUID(), 10, 10);
        assertThatThrownBy(() -> service.blockRectangle(p1, p2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blockBufferDetectsOverlapAndGap() {
        UUID world = UUID.randomUUID();
        ClaimService service = new ClaimService();
        ClaimRegion a = new ClaimRegion(world, 0, 0, 9, 9);   // blocks 0-9
        ClaimRegion b = new ClaimRegion(world, 20, 0, 29, 9); // blocks 20-29
        // gap between a.maxX(9) and b.minX(20): 20-9-1 = 10 blocks
        assertThat(service.isWithinBlockBuffer(a, b, 10)).isFalse();  // gap == buffer
        assertThat(service.isWithinBlockBuffer(a, b, 11)).isTrue();   // gap < buffer

        // adjacent claims: gap = 0
        ClaimRegion adjacent = new ClaimRegion(world, 10, 0, 19, 9);
        assertThat(service.isWithinBlockBuffer(a, adjacent, 1)).isTrue();
        assertThat(service.isWithinBlockBuffer(a, adjacent, 0)).isFalse();
    }

    @Test
    void blockBufferReturnsFalseAcrossWorlds() {
        ClaimService service = new ClaimService();
        ClaimRegion a = new ClaimRegion(UUID.randomUUID(), 0, 0, 9, 9);
        ClaimRegion b = new ClaimRegion(UUID.randomUUID(), 0, 0, 9, 9);
        assertThat(service.isWithinBlockBuffer(a, b, 1000)).isFalse();
    }
}
