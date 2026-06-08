package com.nick.landclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimCostServiceTest {
    @Test
    void quoteIncludesExistingPlayerChunksWhenPricingOverLimitSelection() {
        FakeClaimRepository repository = new FakeClaimRepository();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        repository.claims.add(claim(ownerId, worldId, Set.of(
                new ClaimChunk(worldId, 0, 0),
                new ClaimChunk(worldId, 1, 0)
        )));
        ClaimCostService service = new ClaimCostService(
                repository,
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
        FakeClaimRepository repository = new FakeClaimRepository();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        ClaimCostService service = new ClaimCostService(
                repository,
                new LimitService(3, Map.of("landclaims.limit.vip", 10)),
                new ClaimCostConfig(true, ClaimCostConfig.PricingMode.FLAT, 100.0, 100.0, 2.0)
        );

        ClaimCostQuote quote = service.quotePlayerClaim(
                ownerId,
                Set.of("landclaims.limit.vip"),
                Set.of(new ClaimChunk(worldId, 0, 0), new ClaimChunk(worldId, 1, 0))
        );

        assertThat(quote.allowedChunks()).isEqualTo(10);
        assertThat(quote.overageChunks()).isZero();
        assertThat(quote.cost()).isZero();
    }

    private static Claim claim(UUID ownerId, UUID worldId, Set<ClaimChunk> chunks) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new Claim(UUID.randomUUID(), "Existing", OwnerType.PLAYER, ownerId, worldId, chunks, Map.of(), now, now);
    }

    private static final class FakeClaimRepository implements ClaimRepository {
        private final List<Claim> claims = new ArrayList<>();

        @Override
        public void initialize() {
        }

        @Override
        public void saveClaim(Claim claim) {
            claims.add(claim);
        }

        @Override
        public Optional<Claim> findClaimAt(UUID worldId, int chunkX, int chunkZ) {
            return Optional.empty();
        }

        @Override
        public Optional<Claim> findClaimById(UUID claimId) {
            return Optional.empty();
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
