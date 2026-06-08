package com.nick.landclaims.plugin.visual;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimCreationService;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.ClaimService;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import com.nick.landclaims.plugin.limit.ClaimCostConfig;
import com.nick.landclaims.plugin.limit.ClaimCostService;
import com.nick.landclaims.plugin.limit.LimitService;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimBorderColorServiceTest {
    @Test
    void redWhenSelectionIsBlockedByAnotherClaimBuffer() {
        TestContext context = TestContext.create(10, Map.of());
        UUID otherOwnerId = UUID.randomUUID();
        context.addClaim(claim(otherOwnerId, context.worldId, Set.of(new ClaimChunk(context.worldId, 0, 0)), OwnerType.PLAYER));

        BorderColor color = context.service.colorForPlayerSelection(
                context.ownerId,
                "Home",
                Set.of(new ClaimChunk(context.worldId, 3, 0)),
                Set.of()
        );

        assertThat(color).isEqualTo(BorderColor.RED);
    }

    @Test
    void yellowWhenSelectionCanMergeWithSameNamedOwnerClaim() {
        TestContext context = TestContext.create(10, Map.of());
        context.addClaim(claim(context.ownerId, context.worldId, Set.of(new ClaimChunk(context.worldId, 0, 0)), OwnerType.PLAYER, "Home"));

        BorderColor color = context.service.colorForPlayerSelection(
                context.ownerId,
                "Home",
                Set.of(new ClaimChunk(context.worldId, 1, 0)),
                Set.of()
        );

        assertThat(color).isEqualTo(BorderColor.YELLOW);
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
        TestContext context = TestContext.create(10, Map.of());

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
        return new Claim(UUID.randomUUID(), name, ownerType, ownerId, worldId, chunks, Map.of(), now, now);
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
                    repository,
                    new LimitService(defaultLimit, permissionLimits),
                    new ClaimCostConfig(true, ClaimCostConfig.PricingMode.FLAT, 100.0, 100.0, 2.0)
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
        public void initialize() {
        }

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
