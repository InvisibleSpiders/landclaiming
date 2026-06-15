package com.invisiblespiders.havenclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimCostServiceTest {
    @Test
    void quoteIncludesExistingPlayerChunksWhenPricingOverLimitSelection() {
        ClaimIndex claimIndex = new ClaimIndex();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        claimIndex.add(claim(ownerId, worldId, Set.of(
                new ClaimChunk(worldId, 0, 0),
                new ClaimChunk(worldId, 1, 0)
        )));
        ClaimCostService service = new ClaimCostService(
                claimIndex,
                new LimitService(3, Map.of()),
                new ClaimCostConfig(true, ClaimCostConfig.PricingMode.FLAT, 100.0, 100.0, 2.0)
        );

        ClaimCostQuote quote = service.quotePlayerClaim(
                ownerId,
                Set.of(),
                Set.of(new ClaimChunk(worldId, 2, 0), new ClaimChunk(worldId, 3, 0))
        );

        assertThat(quote.allowedChunks()).isEqualTo(3);
        assertThat(quote.existingChunks()).isEqualTo(2);
        assertThat(quote.proposedTotalChunks()).isEqualTo(4);
        assertThat(quote.overageChunks()).isEqualTo(1);
        assertThat(quote.cost()).isEqualTo(100.0);
    }

    @Test
    void quoteUsesHighestMatchingLimitPermission() {
        ClaimCostService service = new ClaimCostService(
                new ClaimIndex(),
                new LimitService(3, Map.of("landclaims.limit.vip", 10)),
                new ClaimCostConfig(true, ClaimCostConfig.PricingMode.FLAT, 100.0, 100.0, 2.0)
        );

        ClaimCostQuote quote = service.quotePlayerClaim(
                UUID.randomUUID(),
                Set.of("landclaims.limit.vip"),
                Set.of(new ClaimChunk(UUID.randomUUID(), 0, 0), new ClaimChunk(UUID.randomUUID(), 1, 0))
        );

        assertThat(quote.allowedChunks()).isEqualTo(10);
        assertThat(quote.overageChunks()).isZero();
        assertThat(quote.cost()).isZero();
    }

    private static Claim claim(UUID ownerId, UUID worldId, Set<ClaimChunk> chunks) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new Claim(UUID.randomUUID(), "Existing", OwnerType.PLAYER, ownerId, worldId, chunks, Map.of(), now, now);
    }
}
