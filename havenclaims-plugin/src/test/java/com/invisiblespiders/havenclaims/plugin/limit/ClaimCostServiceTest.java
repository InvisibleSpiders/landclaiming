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
    void quoteIncludesExistingPlayerBlocksWhenPricingOverLimitSelection() {
        ClaimIndex claimIndex = new ClaimIndex();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        // Existing claim: chunk (0,0) only — bounding region [0,0] to [15,15] = 256 blocks
        claimIndex.add(claim(ownerId, worldId, Set.of(new ClaimChunk(worldId, 0, 0))));
        ClaimCostService service = new ClaimCostService(
                claimIndex,
                limitOf(300),
                new ClaimCostConfig(true, 100.0, 60)
        );

        // selected region: chunk (1,0) — [16,0] to [31,15] = 256 blocks
        ClaimRegion region = new ClaimRegion(worldId, 16, 0, 31, 15);
        ClaimCostQuote quote = service.quotePlayerClaim(ownerId, region);

        // existing = region().area() of the single existing claim = 16*16 = 256
        int existingArea = new ClaimRegion(worldId, 0, 0, 15, 15).area();
        assertThat(quote.allowedBlocks()).isEqualTo(300);
        assertThat(quote.existingBlocks()).isEqualTo(existingArea);
        assertThat(quote.selectedBlocks()).isEqualTo(region.area());
        assertThat(quote.proposedTotalBlocks()).isEqualTo(existingArea + region.area());
        // 256+256=512, limit=300, overage=212
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
        // Single chunk (0,0): region [0,0] to [15,15] = 256 blocks
        index.add(claim(ownerId, worldId, Set.of(new ClaimChunk(worldId, 0, 0))));
        ClaimCostService service = new ClaimCostService(
                index, limitOf(1000),
                new ClaimCostConfig(true, 100.0, 60));

        // existingTotal = 256 blocks, limit = 1000 — well below limit, refund = 0
        assertThat(service.computeDeletionRefund(ownerId, 256)).isEqualTo(0.0);
    }

    @Test
    void computeDeletionRefundCoversOnlyOverLimitBlocks() {
        ClaimIndex index = new ClaimIndex();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        // Single chunk region [0,0] to [15,15] = 256 blocks
        index.add(claim(ownerId, worldId, Set.of(new ClaimChunk(worldId, 0, 0))));
        ClaimCostService service = new ClaimCostService(
                index, limitOf(200),
                new ClaimCostConfig(true, 100.0, 60));

        // existingTotal=256, limit=200, overageBefore=56 (cost=5600)
        // After removing 100 blocks: existingTotal-100=156, 156<=200, overageAfter=0 (cost=0)
        // refund = 5600 - 0 = 5600
        assertThat(service.computeDeletionRefund(ownerId, 100)).isEqualTo(5600.0);
    }

    @Test
    void computeDeletionRefundPartialOverage() {
        ClaimIndex index = new ClaimIndex();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        // Single chunk region [0,0] to [15,15] = 256 blocks
        index.add(claim(ownerId, worldId, Set.of(new ClaimChunk(worldId, 0, 0))));
        ClaimCostService service = new ClaimCostService(
                index, limitOf(200),
                new ClaimCostConfig(true, 100.0, 60));

        // existingTotal=256, limit=200, overageBefore=56 (cost=5600)
        // After removing 6 blocks: existingTotal-6=250, overageAfter=50 (cost=5000)
        // refund = 5600 - 5000 = 600
        assertThat(service.computeDeletionRefund(ownerId, 6)).isEqualTo(600.0);
    }

    private static Claim claim(UUID ownerId, UUID worldId, Set<ClaimChunk> chunks) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        // Derive bounding region from chunk set
        int minCX = chunks.stream().mapToInt(ClaimChunk::chunkX).min().orElse(0);
        int minCZ = chunks.stream().mapToInt(ClaimChunk::chunkZ).min().orElse(0);
        int maxCX = chunks.stream().mapToInt(ClaimChunk::chunkX).max().orElse(0);
        int maxCZ = chunks.stream().mapToInt(ClaimChunk::chunkZ).max().orElse(0);
        ClaimRegion region = new ClaimRegion(worldId, minCX * 16, minCZ * 16, maxCX * 16 + 15, maxCZ * 16 + 15);
        return new Claim(UUID.randomUUID(), "Existing", OwnerType.PLAYER, ownerId, region, Map.of(), now, now);
    }
}
