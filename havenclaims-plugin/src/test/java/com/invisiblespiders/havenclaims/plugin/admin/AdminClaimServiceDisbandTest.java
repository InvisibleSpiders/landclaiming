package com.invisiblespiders.havenclaims.plugin.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import com.invisiblespiders.havenclaims.plugin.economy.ClaimPaymentService;
import com.invisiblespiders.havenclaims.plugin.economy.EconomyService;
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
import java.util.OptionalInt;
import java.util.function.IntUnaryOperator;
import java.util.Optional;
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
        // chunk (0,0) maps to [0,0] to [15,15]; chunk (1,0) maps to [16,0] to [31,15]
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
    // Single claim: chunk (0,0) → [0,0] to [15,15] = 256 blocks
    // limit = 200 blocks → overage = 56 → refund = 56 * 100.0 = 5600.0
    // adminService passes claim.overlappingChunks().size() (=1) to computeDeletionRefund
    // computeDeletionRefund: existingTotal (from region.area()=256), blocksBeingRemoved=1,
    //   afterDeletion=255, overageBefore=max(0,256-200)=56, overageAfter=max(0,255-200)=55
    //   cost difference = 56*100 - 55*100 = 100.0
    // NOTE: adminService uses claimChunks().size() as proxy — this is a known mismatch fixed in Task 14
    // For this test, we set limit to 0 so entire region is overage and test just verifies
    // that refund > 0 and deposit is called.
    // -------------------------------------------------------------------------
    @Test
    void disbandWithRefundCallsEconomyDeposit() {
        FakeRepo repo = new FakeRepo();
        ClaimIndex index = new ClaimIndex();
        AdminClaimService service = service(repo, index);

        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        // Single chunk claim: [0,0] to [15,15] = 256 blocks
        Claim bigClaim = playerClaim("BigClaim", ownerId, worldId, 0);
        repo.claims.add(bigClaim);
        index.add(bigClaim);

        List<Double> deposited = new ArrayList<>();
        // limit = 0: all 256 blocks are over-limit
        // refund = 256 * 100.0 = 25600.0
        ClaimCostService costService = costService(index, 0, 100.0);
        ClaimPaymentService paymentService = fakePaymentService(deposited);

        DisbandResult result = service.disbandPlayerClaims(ownerId, true, costService, paymentService);

        assertThat(deposited).containsExactly(25600.0);
        assertThat(result.totalRefunded()).isEqualTo(25600.0);
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
        // Single chunk claim: [0,0] to [15,15] = 256 blocks
        Claim bigClaim = playerClaim("BigClaim", ownerId, worldId, 0);
        repo.claims.add(bigClaim);
        index.add(bigClaim);

        List<Double> deposited = new ArrayList<>();
        ClaimCostService costService = costService(index, 0, 100.0);
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

    private static ClaimCostService costService(ClaimIndex index, int defaultLimit, double flatCostPerBlock) {
        LimitService limitService = new LimitService(defaultLimit, new ClaimLimitRepository() {
            @Override public OptionalInt getLimit(UUID id) { return OptionalInt.empty(); }
            @Override public void setLimit(UUID id, int limit) {}
            @Override public void updateLimit(UUID id, int defaultLim, IntUnaryOperator op) {}
        });
        ClaimCostConfig costConfig = new ClaimCostConfig(
                true,
                flatCostPerBlock,
                60
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
        // chunk (chunkX, 0): blocks [chunkX*16, 0] to [chunkX*16+15, 15]
        ClaimRegion region = new ClaimRegion(worldId, chunkX * 16, 0, chunkX * 16 + 15, 15);
        return new Claim(
                UUID.randomUUID(),
                name,
                OwnerType.PLAYER,
                ownerId,
                region,
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
                    .filter(c -> c.overlappingChunks().contains(new ClaimChunk(worldId, chunkX, chunkZ)))
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
