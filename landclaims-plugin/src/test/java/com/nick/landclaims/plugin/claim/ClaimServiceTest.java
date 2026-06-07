package com.nick.landclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;

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
    void createsAllowedAndDeniedValidationResults() {
        ClaimValidationResult allowed = ClaimValidationResult.allowed();
        ClaimValidationResult denied = ClaimValidationResult.denied("claims.too-close");

        assertThat(allowed.isAllowed()).isTrue();
        assertThat(allowed.messageKey()).isEmpty();
        assertThat(denied.isAllowed()).isFalse();
        assertThat(denied.messageKey()).contains("claims.too-close");
    }
}
