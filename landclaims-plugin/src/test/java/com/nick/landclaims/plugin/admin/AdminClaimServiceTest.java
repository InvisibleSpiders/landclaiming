package com.nick.landclaims.plugin.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminClaimServiceTest {
    @Test
    void sortForAdminListReturnsNewListSortedByNameIgnoringCase() {
        AdminClaimService service = new AdminClaimService();
        Claim beta = claim("beta");
        Claim alphaUpper = claim("Alpha");
        Claim alphaLower = claim("alpha");
        List<Claim> claims = new ArrayList<>(List.of(beta, alphaUpper, alphaLower));

        List<Claim> sorted = service.sortForAdminList(claims);

        assertThat(sorted).containsExactly(alphaUpper, alphaLower, beta);
        assertThat(sorted).isNotSameAs(claims);
        assertThat(claims).containsExactly(beta, alphaUpper, alphaLower);
    }

    @Test
    void createsAdminClaimWithLockedDefaultsAndNoOwnerUuid() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        AdminClaimService service = service(repository, claimIndex);
        UUID worldId = UUID.randomUUID();
        Set<ClaimChunk> chunks = Set.of(new ClaimChunk(worldId, 0, 0), new ClaimChunk(worldId, 0, 1));

        AdminClaimResult result = service.createAdminClaim("Spawn", chunks);

        assertThat(result.allowed()).isTrue();
        assertThat(repository.claims).hasSize(1);
        Claim created = repository.claims.get(0);
        assertThat(created.name()).isEqualTo("Spawn");
        assertThat(created.owner()).isEqualTo(OwnerType.ADMIN);
        assertThat(created.ownerUuid()).isNull();
        assertThat(created.flags()).containsEntry("build", false).containsEntry("fluid_flow", false);
        assertThat(created.claimChunks()).containsExactlyInAnyOrderElementsOf(chunks);
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 1))).contains(created);
    }

    @Test
    void rejectsAdminClaimOverlappingExistingClaim() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        AdminClaimService service = service(repository, claimIndex);
        UUID worldId = UUID.randomUUID();
        Claim existing = claim("Home", worldId, Set.of(new ClaimChunk(worldId, 0, 0)), OwnerType.PLAYER);
        claimIndex.add(existing);

        AdminClaimResult result = service.createAdminClaim("Spawn", Set.of(new ClaimChunk(worldId, 0, 0)));

        assertThat(result.allowed()).isFalse();
        assertThat(result.messageKey()).isEqualTo("admin.claim.overlap");
        assertThat(repository.claims).isEmpty();
    }

    @Test
    void deletesOnlyAdminClaims() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        AdminClaimService service = service(repository, claimIndex);
        UUID worldId = UUID.randomUUID();
        Claim adminClaim = claim("Spawn", worldId, Set.of(new ClaimChunk(worldId, 0, 0)), OwnerType.ADMIN);
        Claim playerClaim = claim("Home", worldId, Set.of(new ClaimChunk(worldId, 1, 0)), OwnerType.PLAYER);
        repository.claims.add(adminClaim);
        repository.claims.add(playerClaim);
        claimIndex.add(adminClaim);
        claimIndex.add(playerClaim);

        AdminClaimResult playerResult = service.deleteAdminClaim(playerClaim.id());
        AdminClaimResult adminResult = service.deleteAdminClaim(adminClaim.id());

        assertThat(playerResult.allowed()).isFalse();
        assertThat(playerResult.messageKey()).isEqualTo("admin.claim.not-admin");
        assertThat(adminResult.allowed()).isTrue();
        assertThat(repository.deletedClaimIds).containsExactly(adminClaim.id());
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).isEmpty();
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 1, 0))).contains(playerClaim);
    }

    @Test
    void listsPlayerClaimsForOwnerSortedByName() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        AdminClaimService service = service(repository, claimIndex);
        UUID ownerId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim beta = playerClaim("beta", ownerId, worldId, 0);
        Claim alpha = playerClaim("Alpha", ownerId, worldId, 1);
        Claim other = playerClaim("Other", otherOwnerId, worldId, 2);
        Claim admin = claim("Spawn", worldId, Set.of(new ClaimChunk(worldId, 3, 0)), OwnerType.ADMIN);
        repository.claims.addAll(List.of(beta, alpha, other, admin));

        List<Claim> claims = service.listPlayerClaims(ownerId);

        assertThat(claims).containsExactly(alpha, beta);
    }

    @Test
    void deletesOnlyPlayerClaimsThroughUserClaimAdminFlow() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        AdminClaimService service = service(repository, claimIndex);
        UUID worldId = UUID.randomUUID();
        Claim adminClaim = claim("Spawn", worldId, Set.of(new ClaimChunk(worldId, 0, 0)), OwnerType.ADMIN);
        Claim playerClaim = playerClaim("Home", UUID.randomUUID(), worldId, 1);
        repository.claims.add(adminClaim);
        repository.claims.add(playerClaim);
        claimIndex.add(adminClaim);
        claimIndex.add(playerClaim);

        AdminClaimResult adminResult = service.deletePlayerClaim(adminClaim.id());
        AdminClaimResult playerResult = service.deletePlayerClaim(playerClaim.id());

        assertThat(adminResult.allowed()).isFalse();
        assertThat(adminResult.messageKey()).isEqualTo("admin.userclaims.not-player");
        assertThat(playerResult.allowed()).isTrue();
        assertThat(repository.deletedClaimIds).containsExactly(playerClaim.id());
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 1, 0))).isEmpty();
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).contains(adminClaim);
    }

    @Test
    void findPlayerClaimOnlyReturnsPlayerClaims() {
        FakeClaimRepository repository = new FakeClaimRepository();
        AdminClaimService service = service(repository, new ClaimIndex());
        UUID worldId = UUID.randomUUID();
        Claim adminClaim = claim("Spawn", worldId, Set.of(new ClaimChunk(worldId, 0, 0)), OwnerType.ADMIN);
        Claim playerClaim = playerClaim("Home", UUID.randomUUID(), worldId, 1);
        repository.claims.add(adminClaim);
        repository.claims.add(playerClaim);

        assertThat(service.findPlayerClaim(playerClaim.id())).contains(playerClaim);
        assertThat(service.findPlayerClaim(adminClaim.id())).isEmpty();
    }

    @Test
    void transfersPlayerClaimToNewOwner() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        AdminClaimService service = service(repository, claimIndex);
        UUID worldId = UUID.randomUUID();
        UUID originalOwnerId = UUID.randomUUID();
        UUID newOwnerId = UUID.randomUUID();
        Claim playerClaim = playerClaim("Home", originalOwnerId, worldId, 1);
        repository.claims.add(playerClaim);
        claimIndex.add(playerClaim);

        AdminClaimResult result = service.transferPlayerClaim(playerClaim.id(), newOwnerId);

        assertThat(result.allowed()).isTrue();
        Claim transferred = result.claim();
        assertThat(transferred.id()).isEqualTo(playerClaim.id());
        assertThat(transferred.ownerUuid()).isEqualTo(newOwnerId);
        assertThat(transferred.claimChunks()).isEqualTo(playerClaim.claimChunks());
        assertThat(repository.findClaimById(playerClaim.id())).contains(transferred);
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 1, 0))).contains(transferred);
    }

    @Test
    void transferRejectsAdminClaims() {
        FakeClaimRepository repository = new FakeClaimRepository();
        AdminClaimService service = service(repository, new ClaimIndex());
        UUID worldId = UUID.randomUUID();
        Claim adminClaim = claim("Spawn", worldId, Set.of(new ClaimChunk(worldId, 0, 0)), OwnerType.ADMIN);
        repository.claims.add(adminClaim);

        AdminClaimResult result = service.transferPlayerClaim(adminClaim.id(), UUID.randomUUID());

        assertThat(result.allowed()).isFalse();
        assertThat(result.messageKey()).isEqualTo("admin.userclaims.not-player");
    }

    private static Claim claim(String name) {
        UUID worldId = UUID.randomUUID();
        return claim(name, worldId, Set.of(), OwnerType.PLAYER);
    }

    private static Claim claim(String name, UUID worldId, Set<ClaimChunk> chunks, OwnerType ownerType) {
        return new Claim(
                UUID.randomUUID(),
                name,
                ownerType,
                ownerType == OwnerType.ADMIN ? null : UUID.randomUUID(),
                worldId,
                chunks,
                Map.of(),
                Instant.parse("2026-06-07T00:00:00Z"),
                Instant.parse("2026-06-07T00:00:00Z")
        );
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

    private static AdminClaimService service(FakeClaimRepository repository, ClaimIndex claimIndex) {
        return new AdminClaimService(repository, claimIndex, FlagRegistry.createDefault(), 32);
    }

    private static final class FakeClaimRepository implements ClaimRepository {
        private final List<Claim> claims = new ArrayList<>();
        private final List<UUID> deletedClaimIds = new ArrayList<>();

        @Override
        public void saveClaim(Claim claim) {
            claims.removeIf(existing -> existing.id().equals(claim.id()));
            claims.add(claim);
        }

        @Override
        public void deleteClaim(UUID claimId) {
            deletedClaimIds.add(claimId);
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
                    .filter(claim -> ownerUuid == null || ownerUuid.equals(claim.ownerUuid()))
                    .toList();
        }

        @Override
        public List<Claim> findAllClaims() {
            return List.copyOf(claims);
        }
    }
}
