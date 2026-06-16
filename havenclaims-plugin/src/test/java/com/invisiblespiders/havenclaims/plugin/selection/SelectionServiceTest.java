package com.invisiblespiders.havenclaims.plugin.selection;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SelectionServiceTest {
    private final UUID world = UUID.randomUUID();
    private final SelectionService service = new SelectionService(new ClaimService());

    @Test
    void firstClickStoresCornerReturnsEmpty() {
        BlockPos p1 = new BlockPos(world, 10, 20);
        Optional<ClaimRegion> result = service.select(UUID.randomUUID(), p1);
        assertThat(result).isEmpty();
    }

    @Test
    void secondClickReturnsNormalizedRegion() {
        UUID player = UUID.randomUUID();
        BlockPos p1 = new BlockPos(world, 20, 5);
        BlockPos p2 = new BlockPos(world, 10, 15);
        service.select(player, p1);
        Optional<ClaimRegion> result = service.select(player, p2);
        assertThat(result).contains(new ClaimRegion(world, 10, 5, 20, 15));
    }

    @Test
    void crossWorldClickResetsSelection() {
        UUID player = UUID.randomUUID();
        service.select(player, new BlockPos(world, 0, 0));
        Optional<ClaimRegion> result = service.select(player, new BlockPos(UUID.randomUUID(), 1, 1));
        assertThat(result).isEmpty();
    }

    @Test
    void pendingSelectionReturnsMostRecentCompletedRegion() {
        UUID player = UUID.randomUUID();
        service.select(player, new BlockPos(world, 0, 0));
        service.select(player, new BlockPos(world, 5, 5));
        assertThat(service.pendingSelection(player))
                .contains(new ClaimRegion(world, 0, 0, 5, 5));
    }

    @Test
    void clearRemovesAllSelectionState() {
        UUID player = UUID.randomUUID();
        service.select(player, new BlockPos(world, 0, 0));
        service.select(player, new BlockPos(world, 5, 5));
        service.clear(player);
        assertThat(service.pendingSelection(player)).isEmpty();
    }

    @Test
    void clearReturnsFalseWhenNothingToRemove() {
        assertThat(service.clear(UUID.randomUUID())).isFalse();
    }

    @Test
    void selectionsAreIsolatedPerPlayer() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        service.select(player1, new BlockPos(world, 0, 0));
        service.select(player1, new BlockPos(world, 5, 5));
        assertThat(service.pendingSelection(player2)).isEmpty();
    }

    @Test
    void consumeSelectionClearsState() {
        UUID player = UUID.randomUUID();
        service.select(player, new BlockPos(world, 0, 0));
        service.select(player, new BlockPos(world, 5, 5));
        assertThat(service.consumeSelection(player)).isPresent();
        assertThat(service.pendingSelection(player)).isEmpty();
    }
}
