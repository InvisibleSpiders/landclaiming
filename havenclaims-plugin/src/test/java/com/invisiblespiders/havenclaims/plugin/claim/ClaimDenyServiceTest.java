package com.invisiblespiders.havenclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.api.flag.FlagState;
import com.invisiblespiders.havenclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimDenyServiceTest {
    @Test
    void ownerCanDenyPlayerFromClaim() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimDenyService service = new ClaimDenyService(repository, claimIndex);
        UUID ownerId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim claim = claim(ownerId, worldId, Set.of(), Set.of());
        repository.claims.add(claim);
        claimIndex.add(claim);

        ClaimDenyResult result = service.denyPlayer(ownerId, claim, deniedId);

        assertThat(result.allowed()).isTrue();
        Claim updated = repository.claims.get(0);
        assertThat(updated.deniedPlayers()).containsExactly(deniedId);
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).contains(updated);
    }

    @Test
    void managerCanDenyRegularPlayerButNotOwnerOrManager() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimDenyService service = new ClaimDenyService(repository, new ClaimIndex());
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        Claim claim = claim(ownerId, UUID.randomUUID(), Set.of(new ClaimMember(managerId, ClaimRole.MANAGER)), Set.of());

        ClaimDenyResult ownerResult = service.denyPlayer(managerId, claim, ownerId);
        ClaimDenyResult managerResult = service.denyPlayer(managerId, claim, managerId);

        assertThat(ownerResult.allowed()).isFalse();
        assertThat(ownerResult.messageKey()).isEqualTo("claim.deny.owner-is-not-deniable");
        assertThat(managerResult.allowed()).isFalse();
        assertThat(managerResult.messageKey()).isEqualTo("claim.deny.manager-cannot-deny-manager");
        assertThat(repository.claims).isEmpty();
    }

    @Test
    void nonManagerCannotDenyPlayers() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimDenyService service = new ClaimDenyService(repository, new ClaimIndex());
        Claim claim = claim(UUID.randomUUID(), UUID.randomUUID(), Set.of(), Set.of());

        ClaimDenyResult result = service.denyPlayer(UUID.randomUUID(), claim, UUID.randomUUID());

        assertThat(result.allowed()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claim.deny.not-owner");
        assertThat(repository.claims).isEmpty();
    }

    @Test
    void deniedPlayerCanBeRemoved() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimDenyService service = new ClaimDenyService(repository, claimIndex);
        UUID ownerId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();
        Claim claim = claim(ownerId, UUID.randomUUID(), Set.of(), Set.of(deniedId));
        repository.claims.add(claim);
        claimIndex.add(claim);

        ClaimDenyResult result = service.allowPlayer(ownerId, claim, deniedId);

        assertThat(result.allowed()).isTrue();
        assertThat(repository.claims.get(0).deniedPlayers()).isEmpty();
    }

    @Test
    void adminClaimsCanUseDenyLists() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimDenyService service = new ClaimDenyService(repository, claimIndex);
        UUID adminId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();
        Claim claim = claim(OwnerType.ADMIN, null, UUID.randomUUID(), Set.of(), Set.of());
        repository.claims.add(claim);
        claimIndex.add(claim);

        ClaimDenyResult result = service.denyPlayer(adminId, claim, deniedId, permission -> permission.equals("havenclaims.admin.claim.edit"));

        assertThat(result.allowed()).isTrue();
        assertThat(repository.claims.get(0).deniedPlayers()).containsExactly(deniedId);
    }

    @Test
    void adminClaimsCannotDenyTheirOwnerUuid() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimDenyService service = new ClaimDenyService(repository, new ClaimIndex());
        UUID adminId = UUID.randomUUID();
        Claim claim = claim(OwnerType.ADMIN, adminId, UUID.randomUUID(), Set.of(), Set.of());

        ClaimDenyResult result = service.denyPlayer(
                adminId,
                claim,
                adminId,
                permission -> permission.equals("havenclaims.admin.claim.edit")
        );

        assertThat(result.allowed()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claim.deny.owner-is-not-deniable");
        assertThat(repository.claims).isEmpty();
    }

    private static Claim claim(UUID ownerId, UUID worldId, Set<ClaimMember> members, Set<UUID> deniedPlayers) {
        return claim(OwnerType.PLAYER, ownerId, worldId, members, deniedPlayers);
    }

    private static Claim claim(
            OwnerType ownerType,
            UUID ownerId,
            UUID worldId,
            Set<ClaimMember> members,
            Set<UUID> deniedPlayers
    ) {
        Instant now = Instant.parse("2026-06-09T00:00:00Z");
        return new Claim(
                UUID.randomUUID(),
                "Home",
                ownerType,
                ownerId,
                worldId,
                Set.of(new ClaimChunk(worldId, 0, 0)),
                Map.of("build", FlagState.OFF),
                members,
                deniedPlayers,
                now,
                now
        );
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
            return Optional.empty();
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
