package com.nick.landclaims.plugin.selection;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SelectionServiceTest {
    @Test
    void secondSelectionReturnsExpandedRectangleAndClearsFirstCorner() {
        ClaimService claimService = new ClaimService();
        SelectionService selectionService = new SelectionService(claimService);
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        assertThat(selectionService.select(playerId, new ClaimChunk(worldId, 1, 1))).isEmpty();

        assertThat(selectionService.select(playerId, new ClaimChunk(worldId, 2, 3)))
                .contains(Set.of(
                        new ClaimChunk(worldId, 1, 1),
                        new ClaimChunk(worldId, 1, 2),
                        new ClaimChunk(worldId, 1, 3),
                        new ClaimChunk(worldId, 2, 1),
                        new ClaimChunk(worldId, 2, 2),
                        new ClaimChunk(worldId, 2, 3)
                ));
        assertThat(selectionService.select(playerId, new ClaimChunk(worldId, 5, 5))).isEmpty();
    }
}
