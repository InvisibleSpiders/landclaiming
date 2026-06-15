package com.invisiblespiders.havenclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingClaimMergeServiceTest {
    @Test
    void storesAndConsumesPendingMergeConfirmation() {
        PendingClaimMergeService service = new PendingClaimMergeService();
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Set<ClaimChunk> chunks = Set.of(new ClaimChunk(worldId, 1, 0));

        service.put(playerId, "Home", chunks);

        assertThat(service.get(playerId)).contains(new PendingClaimMerge("Home", chunks));
        assertThat(service.consume(playerId)).contains(new PendingClaimMerge("Home", chunks));
        assertThat(service.get(playerId)).isEmpty();
    }

    @Test
    void clearRemovesPendingMergeConfirmation() {
        PendingClaimMergeService service = new PendingClaimMergeService();
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        service.put(playerId, "Home", Set.of(new ClaimChunk(worldId, 1, 0)));

        service.clear(playerId);

        assertThat(service.get(playerId)).isEmpty();
    }
}
