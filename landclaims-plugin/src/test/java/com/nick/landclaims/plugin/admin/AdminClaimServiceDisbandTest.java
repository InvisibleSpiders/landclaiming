package com.nick.landclaims.plugin.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.economy.ClaimPaymentService;
import com.nick.landclaims.plugin.economy.EconomyService;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import com.nick.landclaims.plugin.limit.ClaimCostConfig;
import com.nick.landclaims.plugin.limit.ClaimCostService;
import com.nick.landclaims.plugin.limit.ClaimLimitRepository;
import com.nick.landclaims.plugin.limit.LimitService;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.IntUnaryOperator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminClaimServiceDisbandTest {

    // -------------------------------------------------------------------------
    // Test 1: disbandDeletesAllPlayerClaims
    // -------------------------------------------------------------------------
    @Test
    void disbandDeletesAllPlayerClaims() {
        FakeRepo repo = new FakeRepo();
        ClaimIndex index = new ClaimIndex();
        AdminClaimService service = service(repo, index);

        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim claim1 = playerClaim("Home", ownerId, worldId, 0);
        Claim claim2 = playerClaim("Farm", ownerId, worldId, 1);
        repo.claims.add(claim1);
        repo.claims.add(claim2);
        index.add(claim1);
        index.add(claim2);

        ClaimCostService costService = costService(index, 10, 0.0);
        ClaimPaymentService paymentService = fakePaymentService(new ArrayList<>());

        DisbandResult result = service.disbandPlayerClaims(ownerId, false, costService, paymentService);

        assertThat(result.claimsDeleted()).isEqualTo(2);
        assertThat(repo.deletedClaimIds).containsExactlyInAnyOrder(claim1.id(), claim2.id());
        assertThat(index.findAt(new ClaimChunk(worldId, 0, 0))).isEmpty();
        assertThat(index.findAt(new ClaimChunk(worldId, 1, 0))).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 2: disbandReturnsZeroWhenNoClaimsExist
    // -------------------------------------------------------------------------
    @Test
    void disbandReturnsZeroWhenNoClaimsExist() {
        FakeRepo repo = new FakeRepo();
        ClaimIndex index = new ClaimIndex();
        AdminClaimService service = service(repo, index);

        UUID ownerId = UUID.randomUUID();
        ClaimCostService costService = costService(index, 10, 100.0);
        ClaimPaymentService paymentService = fakePaymentService(new ArrayList<>());

        DisbandResult result = service.disbandPlayerClaims(ownerId, true, costService, paymentService);

        assertThat(result.claimsDeleted()).isEqualTo(0);
        assertThat(result.totalRefunded()).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // Test 3: disbandWithRefundCallsEconomyDeposit
    // 15 chunks total, limit 10 → 5 overage chunks at 100.0/chunk = 500.0 refund
    // -------------------------------------------------------------------------
    @Test
    void disbandWithRefundCallsEconomyDeposit() {
        FakeRepo repo = new FakeRepo();
        ClaimIndex index = new ClaimIndex();
        AdminClaimService service = service(repo, index);

        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        // Build a claim with 15 chunks (5 over the limit of 10) so refund = 5 * 100 = 500
        Set<ClaimChunk> bigChunks = new HashSet<>();
        for (int i = 0; i < 15; i++) {
            bigChunks.add(new ClaimChunk(worldId, i, 0));
        }
        Claim bigClaim = new Claim(
                UUID.randomUUID(),
                "BigClaim",
                OwnerType.PLAYER,
                ownerId,
                worldId,
                bigChunks,
                Map.of(),
                Instant.parse("2026-06-07T00:00:00Z"),
                Instant.parse("2026-06-07T00:00:00Z")
        );
        repo.claims.add(bigClaim);
        index.add(bigClaim);

        List<Double> deposited = new ArrayList<>();
        ClaimCostService costService = costService(index, 10, 100.0);
        ClaimPaymentService paymentService = fakePaymentService(deposited);

        DisbandResult result = service.disbandPlayerClaims(ownerId, true, costService, paymentService);

        assertThat(deposited).containsExactly(500.0);
        assertThat(result.totalRefunded()).isEqualTo(500.0);
    }

    // -------------------------------------------------------------------------
    // Test 4: disbandNoRefundDoesNotCallEconomy
    // -------------------------------------------------------------------------
    @Test
    void disbandNoRefundDoesNotCallEconomy() {
        FakeRepo repo = new FakeRepo();
        ClaimIndex index = new ClaimIndex();
        AdminClaimService service = service(repo, index);

        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        Set<ClaimChunk> bigChunks = new HashSet<>();
        for (int i = 0; i < 15; i++) {
            bigChunks.add(new ClaimChunk(worldId, i, 0));
        }
        Claim bigClaim = new Claim(
                UUID.randomUUID(),
                "BigClaim",
                OwnerType.PLAYER,
                ownerId,
                worldId,
                bigChunks,
                Map.of(),
                Instant.parse("2026-06-07T00:00:00Z"),
                Instant.parse("2026-06-07T00:00:00Z")
        );
        repo.claims.add(bigClaim);
        index.add(bigClaim);

        List<Double> deposited = new ArrayList<>();
        ClaimCostService costService = costService(index, 10, 100.0);
        ClaimPaymentService paymentService = fakePaymentService(deposited);

        DisbandResult result = service.disbandPlayerClaims(ownerId, false, costService, paymentService);

        assertThat(deposited).isEmpty();
        assertThat(result.totalRefunded()).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static AdminClaimService service(FakeRepo repo, ClaimIndex index) {
        return new AdminClaimService(repo, index, FlagRegistry.createDefault(), 32);
    }

    private static ClaimCostService costService(ClaimIndex index, int defaultLimit, double flatCostPerChunk) {
        LimitService limitService = new LimitService(defaultLimit, new ClaimLimitRepository() {
            @Override public OptionalInt getLimit(UUID id) { return OptionalInt.empty(); }
            @Override public void setLimit(UUID id, int limit) {}
            @Override public void updateLimit(UUID id, int defaultLim, IntUnaryOperator op) {}
        });
        ClaimCostConfig costConfig = new ClaimCostConfig(
                true,
                ClaimCostConfig.PricingMode.FLAT,
                flatCostPerChunk,
                0.0,
                1.0
        );
        return new ClaimCostService(index, limitService, costConfig);
    }

    private static ClaimPaymentService fakePaymentService(List<Double> deposited) {
        EconomyService economy = new EconomyService() {
            @Override public boolean available() { return true; }
            @Override public boolean withdraw(UUID p, double amount) { return true; }
            @Override public boolean deposit(UUID p, double amount) { deposited.add(amount); return true; }
            @Override public String format(double amount) { return String.valueOf(amount); }
        };
        return new ClaimPaymentService(economy);
    }

    private static Claim playerClaim(String name, UUID ownerId, UUID worldId, int chunkX) {
        return new Claim(
                UUID.randomUUID(),
                name,
                OwnerType.PLAYER,
                ownerId,
                worldId,
                Set.of(new ClaimChunk(worldId, chunkX, 0)),
                Map.of(),
                Instant.parse("2026-06-07T00:00:00Z"),
                Instant.parse("2026-06-07T00:00:00Z")
        );
    }

    // -------------------------------------------------------------------------
    // FakeRepo
    // -------------------------------------------------------------------------
    private static final class FakeRepo implements ClaimRepository {
        private final List<Claim> claims = new ArrayList<>();
        private final List<UUID> deletedClaimIds = new ArrayList<>();

        @Override
        public void saveClaim(Claim claim) {
            claims.removeIf(c -> c.id().equals(claim.id()));
            claims.add(claim);
        }

        @Override
        public void deleteClaim(UUID claimId) {
            deletedClaimIds.add(claimId);
            claims.removeIf(c -> c.id().equals(claimId));
        }

        @Override
        public Optional<Claim> findClaimAt(UUID worldId, int chunkX, int chunkZ) {
            return claims.stream()
                    .filter(c -> c.claimChunks().contains(new ClaimChunk(worldId, chunkX, chunkZ)))
                    .findFirst();
        }

        @Override
        public Optional<Claim> findClaimById(UUID claimId) {
            return claims.stream()
                    .filter(c -> c.id().equals(claimId))
                    .findFirst();
        }

        @Override
        public List<Claim> findClaimsByOwner(OwnerType ownerType, UUID ownerUuid) {
            return claims.stream()
                    .filter(c -> c.owner() == ownerType)
                    .filter(c -> ownerUuid == null || ownerUuid.equals(c.ownerUuid()))
                    .toList();
        }

        @Override
        public List<Claim> findAllClaims() {
            return List.copyOf(claims);
        }
    }
}
