package com.nick.landclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.plugin.economy.ClaimPaymentService;
import com.nick.landclaims.plugin.economy.NoopEconomyService;
import com.nick.landclaims.plugin.limit.ClaimCostConfig;
import com.nick.landclaims.plugin.limit.ClaimCostService;
import com.nick.landclaims.plugin.limit.ClaimLimitRepository;
import com.nick.landclaims.plugin.limit.LimitService;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

class ClaimManagementServiceTest {
    private static LimitService limitOf(int limit) {
        return new LimitService(limit, new ClaimLimitRepository() {
            @Override public OptionalInt getLimit(UUID id) { return OptionalInt.empty(); }
            @Override public void setLimit(UUID id, int lim) {}
            @Override public void updateLimit(UUID id, int defaultLimit, IntUnaryOperator op) {}
        });
    }

    // -------------------------------------------------------------------------
    // renameClaim tests
    // -------------------------------------------------------------------------

    @Test
    void renameClaimSucceedsWhenPlayerOwnsIt() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim original = claim(ownerId, worldId, "OldName");

        FakeClaimRepository repository = new FakeClaimRepository();
        repository.claims.add(original);
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.add(original);

        ClaimManagementService service = new ClaimManagementService(repository, claimIndex);
        ClaimManagementResult result = service.renameClaim(ownerId, original.id(), "NewName", 32);

        assertThat(result.success()).isTrue();
        assertThat(result.messageKey()).isEqualTo("claim.manage.renamed");

        Claim saved = repository.claims.get(0);
        assertThat(saved.name()).isEqualTo("NewName");
        assertThat(saved.id()).isEqualTo(original.id());
        // index should reflect the updated claim
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).contains(saved);
    }

    @Test
    void renameClaimDeniedWhenPlayerDoesNotOwnIt() {
        UUID ownerId = UUID.randomUUID();
        UUID otherPlayerId = UUID.randomUUID();
        Claim claim = claim(ownerId, UUID.randomUUID(), "Home");

        FakeClaimRepository repository = new FakeClaimRepository();
        repository.claims.add(claim);
        ClaimManagementService service = new ClaimManagementService(repository, new ClaimIndex());

        ClaimManagementResult result = service.renameClaim(otherPlayerId, claim.id(), "Stolen", 32);

        assertThat(result.success()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claim.manage.not-owner");
        // claim should not be modified
        assertThat(repository.claims.get(0).name()).isEqualTo("Home");
    }

    @Test
    void renameClaimDeniedWhenNameExceedsMaxLength() {
        UUID ownerId = UUID.randomUUID();
        Claim claim = claim(ownerId, UUID.randomUUID(), "Home");

        FakeClaimRepository repository = new FakeClaimRepository();
        repository.claims.add(claim);
        ClaimManagementService service = new ClaimManagementService(repository, new ClaimIndex());

        String tooLongName = "a".repeat(33);
        ClaimManagementResult result = service.renameClaim(ownerId, claim.id(), tooLongName, 32);

        assertThat(result.success()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claim.manage.invalid-name");
        assertThat(repository.claims.get(0).name()).isEqualTo("Home");
    }

    @Test
    void renameClaimDeniedWhenClaimNotFound() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimManagementService service = new ClaimManagementService(repository, new ClaimIndex());

        ClaimManagementResult result = service.renameClaim(UUID.randomUUID(), UUID.randomUUID(), "NewName", 32);

        assertThat(result.success()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claim.manage.not-found");
    }

    @Test
    void renameClaimDeniedWhenNameIsBlank() {
        UUID ownerId = UUID.randomUUID();
        Claim claim = claim(ownerId, UUID.randomUUID(), "Home");
        FakeClaimRepository repository = new FakeClaimRepository();
        repository.claims.add(claim);
        ClaimManagementService service = new ClaimManagementService(repository, new ClaimIndex());

        ClaimManagementResult result = service.renameClaim(ownerId, claim.id(), "   ", 32);

        assertThat(result.success()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claim.manage.invalid-name");
        assertThat(repository.claims.get(0).name()).isEqualTo("Home");
    }

    // -------------------------------------------------------------------------
    // deleteClaim tests
    // -------------------------------------------------------------------------

    @Test
    void deleteClaimSucceedsAndDoesNotCallRefundWhenWithinLimit() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        // Claim has 2 chunks; limit is 10 => player is within limit, refund = 0
        Claim claim = claim(ownerId, worldId, "Home", Set.of(
                new ClaimChunk(worldId, 0, 0),
                new ClaimChunk(worldId, 1, 0)
        ));

        FakeClaimRepository repository = new FakeClaimRepository();
        repository.claims.add(claim);
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.add(claim);

        ClaimManagementService service = new ClaimManagementService(repository, claimIndex);

        // LimitService with limit=10 means 2-chunk claim is within limit → refund = 0
        ClaimCostService costService = new ClaimCostService(
                claimIndex,
                limitOf(10),
                new ClaimCostConfig(true, ClaimCostConfig.PricingMode.FLAT, 100.0, 100.0, 2.0)
        );
        // NoopEconomyService is always unavailable — so if refund is erroneously attempted,
        // it would log a warning but not throw. The claim should still be deleted successfully.
        ClaimPaymentService paymentService = new ClaimPaymentService(new NoopEconomyService());

        ClaimManagementResult result = service.deleteClaim(ownerId, claim.id(), costService, paymentService);

        assertThat(result.success()).isTrue();
        assertThat(result.messageKey()).isEqualTo("claim.manage.deleted");
        assertThat(repository.claims).isEmpty();
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).isEmpty();
    }

    @Test
    void deleteClaimDeniedWhenPlayerDoesNotOwnIt() {
        UUID ownerId = UUID.randomUUID();
        UUID otherPlayerId = UUID.randomUUID();
        Claim claim = claim(ownerId, UUID.randomUUID(), "Home");

        FakeClaimRepository repository = new FakeClaimRepository();
        repository.claims.add(claim);
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.add(claim);

        ClaimManagementService service = new ClaimManagementService(repository, claimIndex);
        ClaimCostService costService = new ClaimCostService(
                claimIndex,
                limitOf(10),
                new ClaimCostConfig(true, ClaimCostConfig.PricingMode.FLAT, 100.0, 100.0, 2.0)
        );
        ClaimPaymentService paymentService = new ClaimPaymentService(new NoopEconomyService());

        ClaimManagementResult result = service.deleteClaim(otherPlayerId, claim.id(), costService, paymentService);

        assertThat(result.success()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claim.manage.not-owner");
        assertThat(repository.claims).hasSize(1);
    }

    @Test
    void deleteClaimDeniedWhenClaimNotFound() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimManagementService service = new ClaimManagementService(repository, new ClaimIndex());
        ClaimCostService costService = new ClaimCostService(
                new ClaimIndex(),
                limitOf(10),
                new ClaimCostConfig(true, ClaimCostConfig.PricingMode.FLAT, 100.0, 100.0, 2.0)
        );
        ClaimPaymentService paymentService = new ClaimPaymentService(new NoopEconomyService());

        ClaimManagementResult result = service.deleteClaim(UUID.randomUUID(), UUID.randomUUID(), costService, paymentService);

        assertThat(result.success()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claim.manage.not-found");
    }

    @Test
    void deleteClaimIssuesRefundWhenPlayerIsOverLimit() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        // Player has 5 chunks, limit is 3 → over by 2 → should receive a refund on deletion
        Claim claim = claim(ownerId, worldId, "Home", Set.of(
                new ClaimChunk(worldId, 0, 0),
                new ClaimChunk(worldId, 1, 0),
                new ClaimChunk(worldId, 2, 0),
                new ClaimChunk(worldId, 3, 0),
                new ClaimChunk(worldId, 4, 0)
        ));

        FakeClaimRepository repository = new FakeClaimRepository();
        repository.claims.add(claim);
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.add(claim);

        ClaimManagementService service = new ClaimManagementService(repository, claimIndex);
        ClaimCostService costService = new ClaimCostService(
                claimIndex,
                limitOf(3),
                new ClaimCostConfig(true, ClaimCostConfig.PricingMode.FLAT, 100.0, 100.0, 2.0)
        );
        TrackingEconomyService economyService = new TrackingEconomyService();
        ClaimPaymentService paymentService = new ClaimPaymentService(economyService);

        ClaimManagementResult result = service.deleteClaim(ownerId, claim.id(), costService, paymentService);

        assertThat(result.success()).isTrue();
        assertThat(result.messageKey()).isEqualTo("claim.manage.deleted");
        assertThat(economyService.lastDeposit).isEqualTo(200.0); // 2 over-limit chunks × $100
        assertThat(repository.claims).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Claim claim(UUID ownerId, UUID worldId, String name) {
        return claim(ownerId, worldId, name, Set.of(new ClaimChunk(worldId, 0, 0)));
    }

    private static Claim claim(UUID ownerId, UUID worldId, String name, Set<ClaimChunk> chunks) {
        Instant now = Instant.parse("2026-06-12T00:00:00Z");
        return new Claim(
                UUID.randomUUID(),
                name,
                OwnerType.PLAYER,
                ownerId,
                worldId,
                chunks,
                Map.of(),
                Set.of(),
                Set.of(),
                now,
                now
        );
    }

    private static final class TrackingEconomyService implements com.nick.landclaims.plugin.economy.EconomyService {
        double lastDeposit = 0.0;

        @Override public boolean available() { return true; }
        @Override public boolean withdraw(UUID playerId, double amount) { return true; }
        @Override public boolean deposit(UUID playerId, double amount) { lastDeposit = amount; return true; }
        @Override public String format(double amount) { return String.valueOf(amount); }
    }

    private static final class FakeClaimRepository implements ClaimRepository {
        private final List<Claim> claims = new ArrayList<>();

        @Override
        public void saveClaim(Claim claim) {
            claims.removeIf(existing -> existing.id().equals(claim.id()));
            claims.add(claim);
        }

        @Override
        public void deleteClaim(UUID claimId) {
            claims.removeIf(claim -> claim.id().equals(claimId));
        }

        @Override
        public Optional<Claim> findClaimAt(UUID worldId, int chunkX, int chunkZ) {
            return Optional.empty();
        }

        @Override
        public Optional<Claim> findClaimById(UUID claimId) {
            return claims.stream().filter(c -> c.id().equals(claimId)).findFirst();
        }

        @Override
        public List<Claim> findClaimsByOwner(OwnerType ownerType, UUID ownerUuid) {
            return List.of();
        }

        @Override
        public List<Claim> findAllClaims() {
            return List.copyOf(claims);
        }
    }
}
