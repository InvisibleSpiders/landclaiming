package com.invisiblespiders.havenclaims.plugin.visual;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimCreationService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimService;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import com.invisiblespiders.havenclaims.plugin.flag.FlagRegistry;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimCostConfig;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimCostService;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimLimitRepository;
import com.invisiblespiders.havenclaims.plugin.limit.LimitService;
import com.invisiblespiders.havenclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimBorderColorServiceTest {
    @Test
    void redWhenSelectionIsWithinBlockBufferOfAnotherClaim() {
        TestContext context = TestContext.create(10, Map.of());
        UUID otherOwnerId = UUID.randomUUID();
        context.addClaim(claim(otherOwnerId, context.worldId, Set.of(new ClaimChunk(context.worldId, 0, 0)), OwnerType.PLAYER));

        // Chunk (1,0) is adjacent to chunk (0,0) — 0-block gap, within 3-block buffer → RED
        BorderColor color = context.service.colorForPlayerSelection(
                context.ownerId,
                "Home",
                Set.of(new ClaimChunk(context.worldId, 1, 0)),
                Set.of()
        );

        assertThat(color).isEqualTo(BorderColor.RED);
    }

    @Test
    void aquaWhenSelectionBordersSameNamedOwnerClaimMergeDisabled() {
        // In Phase 1, findMergeTargets always returns empty (merge disabled).
        // Adjacent same-named own claim → no YELLOW merge color, cost applies instead.
        TestContext context = TestContext.create(10, Map.of());
        context.addClaim(claim(context.ownerId, context.worldId, Set.of(new ClaimChunk(context.worldId, 0, 0)), OwnerType.PLAYER, "Home"));

        BorderColor color = context.service.colorForPlayerSelection(
                context.ownerId,
                "Home",
                Set.of(new ClaimChunk(context.worldId, 2, 0)),
                Set.of()
        );

        assertThat(color).isEqualTo(BorderColor.AQUA);
    }

    @Test
    void yellowWhenUnnamedPreviewBordersOwnerClaim() {
        TestContext context = TestContext.create(10, Map.of());
        context.addClaim(claim(context.ownerId, context.worldId, Set.of(new ClaimChunk(context.worldId, 0, 0)), OwnerType.PLAYER, "Home"));

        BorderColor color = context.service.colorForPlayerSelection(
                context.ownerId,
                Optional.empty(),
                Set.of(new ClaimChunk(context.worldId, 1, 0)),
                Set.of()
        );

        assertThat(color).isEqualTo(BorderColor.YELLOW);
    }

    @Test
    void aquaWhenSelectionIsClaimableButHasCost() {
        TestContext context = TestContext.create(1, Map.of());

        BorderColor color = context.service.colorForPlayerSelection(
                context.ownerId,
                "Home",
                Set.of(
                        new ClaimChunk(context.worldId, 10, 10),
                        new ClaimChunk(context.worldId, 11, 10)
                ),
                Set.of()
        );

        assertThat(color).isEqualTo(BorderColor.AQUA);
    }

    @Test
    void greenWhenSelectionIsClaimableWithoutMergeOrCost() {
        // 1 chunk = 256 blocks; limit must be >= 256 so no over-limit cost is applied
        TestContext context = TestContext.create(256, Map.of());

        BorderColor color = context.service.colorForPlayerSelection(
                context.ownerId,
                "Home",
                Set.of(new ClaimChunk(context.worldId, 10, 10)),
                Set.of()
        );

        assertThat(color).isEqualTo(BorderColor.GREEN);
    }

    private static Claim claim(UUID ownerId, UUID worldId, Set<ClaimChunk> chunks, OwnerType ownerType) {
        return claim(ownerId, worldId, chunks, ownerType, "Existing");
    }

    private static Claim claim(UUID ownerId, UUID worldId, Set<ClaimChunk> chunks, OwnerType ownerType, String name) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        // Derive bounding region from chunks
        int minCX = chunks.stream().mapToInt(ClaimChunk::chunkX).min().orElse(0);
        int minCZ = chunks.stream().mapToInt(ClaimChunk::chunkZ).min().orElse(0);
        int maxCX = chunks.stream().mapToInt(ClaimChunk::chunkX).max().orElse(0);
        int maxCZ = chunks.stream().mapToInt(ClaimChunk::chunkZ).max().orElse(0);
        ClaimRegion region = new ClaimRegion(worldId, minCX * 16, minCZ * 16, maxCX * 16 + 15, maxCZ * 16 + 15);
        return new Claim(UUID.randomUUID(), name, ownerType, ownerId, region, Map.of(), now, now);
    }

    private static final class TestContext {
        private final UUID ownerId = UUID.randomUUID();
        private final UUID worldId = UUID.randomUUID();
        private final FakeClaimRepository repository = new FakeClaimRepository();
        private final ClaimIndex claimIndex = new ClaimIndex();
        private final ClaimBorderColorService service;

        private TestContext(int defaultLimit, Map<String, Integer> permissionLimits) {
            ClaimCreationService claimCreationService = new ClaimCreationService(
                    repository,
                    claimIndex,
                    new ClaimService(),
                    FlagRegistry.createDefault(),
                    3,
                    3,
                    32
            );
            ClaimCostService claimCostService = new ClaimCostService(
                    claimIndex,
                    new LimitService(defaultLimit, new ClaimLimitRepository() {
                        @Override public OptionalInt getLimit(UUID id) { return OptionalInt.empty(); }
                        @Override public void setLimit(UUID id, int limit) {}
                        @Override public void updateLimit(UUID id, int defaultLimit, java.util.function.IntUnaryOperator op) {}
                    }),
                    new ClaimCostConfig(true, 100.0, 60)
            );
            service = new ClaimBorderColorService(claimCreationService, claimIndex, claimCostService);
        }

        private static TestContext create(int defaultLimit, Map<String, Integer> permissionLimits) {
            return new TestContext(defaultLimit, permissionLimits);
        }

        private void addClaim(Claim claim) {
            repository.claims.add(claim);
            claimIndex.add(claim);
        }
    }

    private static final class FakeClaimRepository implements ClaimRepository {
        private final List<Claim> claims = new ArrayList<>();

        @Override
        public void saveClaim(Claim claim) {
            claims.removeIf(savedClaim -> savedClaim.id().equals(claim.id()));
            claims.add(claim);
        }

        @Override
        public void deleteClaim(UUID claimId) {
            claims.removeIf(claim -> claim.id().equals(claimId));
        }

        @Override
        public Optional<Claim> findClaimAt(UUID worldId, int chunkX, int chunkZ) {
            return claims.stream()
                    .filter(claim -> claim.claimChunks().contains(new ClaimChunk(worldId, chunkX, chunkZ)))
                    .findFirst();
        }

        @Override
        public Optional<Claim> findClaimById(UUID claimId) {
            return claims.stream()
                    .filter(claim -> claim.id().equals(claimId))
                    .findFirst();
        }

        @Override
        public List<Claim> findClaimsByOwner(OwnerType ownerType, UUID ownerUuid) {
            return claims.stream()
                    .filter(claim -> claim.owner() == ownerType)
                    .filter(claim -> ownerUuid.equals(claim.ownerUuid()))
                    .toList();
        }

        @Override
        public List<Claim> findAllClaims() {
            return List.copyOf(claims);
        }
    }
}
