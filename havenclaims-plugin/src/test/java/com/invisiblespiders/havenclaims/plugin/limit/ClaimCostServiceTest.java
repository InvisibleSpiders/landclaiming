package com.invisiblespiders.havenclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimCostServiceTest {
    private static LimitService limitOf(int limit) {
        return new LimitService(limit, new ClaimLimitRepository() {
            @Override public OptionalInt getLimit(UUID id) { return OptionalInt.empty(); }
            @Override public void setLimit(UUID id, int limit) {}
            @Override public void updateLimit(UUID id, int defaultLimit, java.util.function.IntUnaryOperator op) {}
        });
    }

    private static LimitService limitOf(UUID player, int limit) {
        return new LimitService(99, new ClaimLimitRepository() {
            @Override public OptionalInt getLimit(UUID id) { return id.equals(player) ? OptionalInt.of(limit) : OptionalInt.empty(); }
            @Override public void setLimit(UUID id, int lim) {}
            @Override public void updateLimit(UUID id, int defaultLimit, java.util.function.IntUnaryOperator op) {}
        });
    }

    /** A 1-block-wide region at chunk-column x=cx for testing */
    private static ClaimRegion singleChunkRegion(UUID worldId, int cx, int cz) {
        return new ClaimRegion(worldId, cx * 16, cz * 16, cx * 16 + 15, cz * 16 + 15);
    }

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
                limitOf(3),
                new ClaimCostConfig(true, 100.0, 60)
        );

        // region covering chunks (2,0) and (3,0): 2 chunks × 256 blocks each = 512 selectedBlocks
        ClaimRegion region = new ClaimRegion(worldId, 2 * 16, 0, 3 * 16 + 15, 15);
        ClaimCostQuote quote = service.quotePlayerClaim(ownerId, region);

        assertThat(quote.allowedBlocks()).isEqualTo(3);
        assertThat(quote.existingBlocks()).isEqualTo(2); // claimChunks().size() stand-in
        assertThat(quote.selectedBlocks()).isEqualTo(region.area());
        assertThat(quote.proposedTotalBlocks()).isEqualTo(2 + region.area());
        assertThat(quote.overageBlocks()).isGreaterThan(0);
    }

    @Test
    void quoteUsesPlayerDBLimitNotDefault() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        ClaimCostService service = new ClaimCostService(
                new ClaimIndex(),
                limitOf(ownerId, 10),
                new ClaimCostConfig(true, 100.0, 60)
        );

        // 1-block region — well within limit of 10
        ClaimRegion region = new ClaimRegion(worldId, 0, 0, 0, 0);
        ClaimCostQuote quote = service.quotePlayerClaim(ownerId, region);

        assertThat(quote.allowedBlocks()).isEqualTo(10);
        assertThat(quote.overageBlocks()).isZero();
    }

    @Test
    void reloadUpdatesConfig() {
        ClaimIndex index = new ClaimIndex();
        ClaimCostService service = new ClaimCostService(
                index, limitOf(5),
                new ClaimCostConfig(true, 100.0, 60));
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        service.reload(new ClaimCostConfig(true, 999.0, 60));

        // 1-block region: overageBlocks = 1 - 5 = 0, so use a region > 5 blocks
        // 6-block region: 6 blocks, limit 5, overage 1 → cost = 999.0
        ClaimRegion region = new ClaimRegion(worldId, 0, 0, 5, 0); // 6 blocks wide × 1 tall
        ClaimCostQuote quote = service.quotePlayerClaim(ownerId, region);
        assertThat(quote.cost()).isEqualTo(999.0);
    }

    @Test
    void computeDeletionRefundIsZeroWhenBelowLimit() {
        ClaimIndex index = new ClaimIndex();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        index.add(claim(ownerId, worldId, Set.of(
                new ClaimChunk(worldId, 0, 0),
                new ClaimChunk(worldId, 1, 0),
                new ClaimChunk(worldId, 2, 0))));
        ClaimCostService service = new ClaimCostService(
                index, limitOf(10),
                new ClaimCostConfig(true, 100.0, 60));

        assertThat(service.computeDeletionRefund(ownerId, 3)).isEqualTo(0.0);
    }

    @Test
    void computeDeletionRefundCoversOnlyOverLimitChunks() {
        ClaimIndex index = new ClaimIndex();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Set<ClaimChunk> chunks = new java.util.HashSet<>();
        for (int i = 0; i < 15; i++) chunks.add(new ClaimChunk(worldId, i, 0));
        index.add(claim(ownerId, worldId, Set.copyOf(chunks)));
        ClaimCostService service = new ClaimCostService(
                index, limitOf(10),
                new ClaimCostConfig(true, 100.0, 60));

        // 15 chunks, limit 10. Removing 8: overageBefore=5(500), overageAfter=0(0) → 500
        assertThat(service.computeDeletionRefund(ownerId, 8)).isEqualTo(500.0);
    }

    @Test
    void computeDeletionRefundPartialOverage() {
        ClaimIndex index = new ClaimIndex();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Set<ClaimChunk> chunks = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++) chunks.add(new ClaimChunk(worldId, i, 0));
        index.add(claim(ownerId, worldId, Set.copyOf(chunks)));
        ClaimCostService service = new ClaimCostService(
                index, limitOf(10),
                new ClaimCostConfig(true, 100.0, 60));

        // 20 chunks, limit 10. Removing 5: overageBefore=10(1000), overageAfter=5(500) → 500
        assertThat(service.computeDeletionRefund(ownerId, 5)).isEqualTo(500.0);
    }

    private static Claim claim(UUID ownerId, UUID worldId, Set<ClaimChunk> chunks) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new Claim(UUID.randomUUID(), "Existing", OwnerType.PLAYER, ownerId, worldId, chunks, Map.of(), now, now);
    }
}
