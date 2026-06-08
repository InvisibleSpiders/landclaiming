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

    @Test
    void secondSelectionInDifferentWorldBecomesNewFirstCorner() {
        ClaimService claimService = new ClaimService();
        SelectionService selectionService = new SelectionService(claimService);
        UUID playerId = UUID.randomUUID();
        UUID firstWorldId = UUID.randomUUID();
        UUID secondWorldId = UUID.randomUUID();

        assertThat(selectionService.select(playerId, new ClaimChunk(firstWorldId, 1, 1))).isEmpty();
        assertThat(selectionService.select(playerId, new ClaimChunk(secondWorldId, 4, 4))).isEmpty();

        assertThat(selectionService.select(playerId, new ClaimChunk(secondWorldId, 5, 5)))
                .contains(Set.of(
                        new ClaimChunk(secondWorldId, 4, 4),
                        new ClaimChunk(secondWorldId, 4, 5),
                        new ClaimChunk(secondWorldId, 5, 4),
                        new ClaimChunk(secondWorldId, 5, 5)
                ));
    }

    @Test
    void selectionsAreIsolatedPerPlayer() {
        ClaimService claimService = new ClaimService();
        SelectionService selectionService = new SelectionService(claimService);
        UUID firstPlayerId = UUID.randomUUID();
        UUID secondPlayerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        assertThat(selectionService.select(firstPlayerId, new ClaimChunk(worldId, 0, 0))).isEmpty();
        assertThat(selectionService.select(secondPlayerId, new ClaimChunk(worldId, 10, 10))).isEmpty();

        assertThat(selectionService.select(firstPlayerId, new ClaimChunk(worldId, 1, 0)))
                .contains(Set.of(
                        new ClaimChunk(worldId, 0, 0),
                        new ClaimChunk(worldId, 1, 0)
                ));
        assertThat(selectionService.select(secondPlayerId, new ClaimChunk(worldId, 10, 11)))
                .contains(Set.of(
                        new ClaimChunk(worldId, 10, 10),
                        new ClaimChunk(worldId, 10, 11)
                ));
    }

    @Test
    void completedSelectionRemainsPendingUntilConsumed() {
        ClaimService claimService = new ClaimService();
        SelectionService selectionService = new SelectionService(claimService);
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        assertThat(selectionService.select(playerId, new ClaimChunk(worldId, 0, 0))).isEmpty();
        Set<ClaimChunk> completedSelection = selectionService.select(playerId, new ClaimChunk(worldId, 1, 0))
                .orElseThrow();

        assertThat(selectionService.pendingSelection(playerId)).contains(completedSelection);
        assertThat(selectionService.pendingSelection(playerId)).contains(completedSelection);
        assertThat(selectionService.consumeSelection(playerId)).contains(completedSelection);
        assertThat(selectionService.pendingSelection(playerId)).isEmpty();
    }

    @Test
    void clearRemovesFirstCornerAndCompletedSelection() {
        ClaimService claimService = new ClaimService();
        SelectionService selectionService = new SelectionService(claimService);
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        selectionService.select(playerId, new ClaimChunk(worldId, 0, 0));
        selectionService.select(playerId, new ClaimChunk(worldId, 1, 0));

        selectionService.clear(playerId);

        assertThat(selectionService.pendingSelection(playerId)).isEmpty();
        assertThat(selectionService.consumeSelection(playerId)).isEmpty();
        assertThat(selectionService.select(playerId, new ClaimChunk(worldId, 2, 0))).isEmpty();
    }
}
