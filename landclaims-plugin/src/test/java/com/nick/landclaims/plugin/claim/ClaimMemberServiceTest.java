package com.nick.landclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimMemberServiceTest {
    @Test
    void ownerCanAddMemberToClaim() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimMemberService service = new ClaimMemberService(repository, claimIndex);
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim claim = claim(ownerId, worldId, Set.of());
        repository.claims.add(claim);
        claimIndex.add(claim);

        ClaimMemberResult result = service.addMember(ownerId, claim, memberId, ClaimRole.MEMBER);

        assertThat(result.allowed()).isTrue();
        Claim updated = repository.claims.get(0);
        assertThat(updated.members()).containsExactly(new ClaimMember(memberId, ClaimRole.MEMBER));
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).contains(updated);
    }

    @Test
    void ownerCanPromoteExistingMemberToManager() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimMemberService service = new ClaimMemberService(repository, claimIndex);
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim claim = claim(ownerId, worldId, Set.of(new ClaimMember(memberId, ClaimRole.MEMBER)));
        repository.claims.add(claim);
        claimIndex.add(claim);

        ClaimMemberResult result = service.addMember(ownerId, claim, memberId, ClaimRole.MANAGER);

        assertThat(result.allowed()).isTrue();
        assertThat(repository.claims.get(0).members()).containsExactly(new ClaimMember(memberId, ClaimRole.MANAGER));
    }

    @Test
    void nonOwnerCannotManageMembers() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimMemberService service = new ClaimMemberService(repository, claimIndex);
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Claim claim = claim(ownerId, UUID.randomUUID(), Set.of());

        ClaimMemberResult result = service.addMember(UUID.randomUUID(), claim, memberId, ClaimRole.MEMBER);

        assertThat(result.allowed()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claim.member.not-owner");
        assertThat(repository.claims).isEmpty();
    }

    @Test
    void ownerCannotAddThemselvesAsMember() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimMemberService service = new ClaimMemberService(repository, new ClaimIndex());
        UUID ownerId = UUID.randomUUID();
        Claim claim = claim(ownerId, UUID.randomUUID(), Set.of());

        ClaimMemberResult result = service.addMember(ownerId, claim, ownerId, ClaimRole.MEMBER);

        assertThat(result.allowed()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claim.member.owner-is-not-member");
    }

    @Test
    void managerCannotAddOwnerAsMember() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimMemberService service = new ClaimMemberService(repository, new ClaimIndex());
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        Claim claim = claim(ownerId, UUID.randomUUID(), Set.of(new ClaimMember(managerId, ClaimRole.MANAGER)));

        ClaimMemberResult result = service.addMember(managerId, claim, ownerId, ClaimRole.MEMBER);

        assertThat(result.allowed()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claim.member.owner-is-not-member");
        assertThat(repository.claims).isEmpty();
    }

    @Test
    void managerCanAddMemberToClaim() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimMemberService service = new ClaimMemberService(repository, claimIndex);
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID newMemberId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim claim = claim(ownerId, worldId, Set.of(new ClaimMember(managerId, ClaimRole.MANAGER)));
        repository.claims.add(claim);
        claimIndex.add(claim);

        ClaimMemberResult result = service.addMember(managerId, claim, newMemberId, ClaimRole.MEMBER);

        assertThat(result.allowed()).isTrue();
        assertThat(repository.claims.get(0).members()).contains(new ClaimMember(newMemberId, ClaimRole.MEMBER));
    }

    @Test
    void managerCannotAddAnotherManagerToClaim() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimMemberService service = new ClaimMemberService(repository, new ClaimIndex());
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        Claim claim = claim(ownerId, UUID.randomUUID(), Set.of(new ClaimMember(managerId, ClaimRole.MANAGER)));

        ClaimMemberResult result = service.addMember(managerId, claim, UUID.randomUUID(), ClaimRole.MANAGER);

        assertThat(result.allowed()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claim.member.manager-cannot-promote");
        assertThat(repository.claims).isEmpty();
    }

    @Test
    void managerCanRemoveMemberFromClaim() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimMemberService service = new ClaimMemberService(repository, claimIndex);
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Claim claim = claim(ownerId, UUID.randomUUID(), Set.of(
                new ClaimMember(managerId, ClaimRole.MANAGER),
                new ClaimMember(memberId, ClaimRole.MEMBER)
        ));
        repository.claims.add(claim);
        claimIndex.add(claim);

        ClaimMemberResult result = service.removeMember(managerId, claim, memberId);

        assertThat(result.allowed()).isTrue();
        assertThat(repository.claims.get(0).members())
                .containsExactly(new ClaimMember(managerId, ClaimRole.MANAGER));
    }

    @Test
    void managerCannotRemoveAnotherManagerFromClaim() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimMemberService service = new ClaimMemberService(repository, new ClaimIndex());
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID otherManagerId = UUID.randomUUID();
        Claim claim = claim(ownerId, UUID.randomUUID(), Set.of(
                new ClaimMember(managerId, ClaimRole.MANAGER),
                new ClaimMember(otherManagerId, ClaimRole.MANAGER)
        ));

        ClaimMemberResult result = service.removeMember(managerId, claim, otherManagerId);

        assertThat(result.allowed()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claim.member.manager-cannot-remove-manager");
        assertThat(repository.claims).isEmpty();
    }

    @Test
    void ownerCanRemoveMemberFromClaim() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimMemberService service = new ClaimMemberService(repository, claimIndex);
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Claim claim = claim(ownerId, UUID.randomUUID(), Set.of(new ClaimMember(memberId, ClaimRole.MEMBER)));
        repository.claims.add(claim);
        claimIndex.add(claim);

        ClaimMemberResult result = service.removeMember(ownerId, claim, memberId);

        assertThat(result.allowed()).isTrue();
        assertThat(repository.claims.get(0).members()).isEmpty();
    }

    private static Claim claim(UUID ownerId, UUID worldId, Set<ClaimMember> members) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new Claim(
                UUID.randomUUID(),
                "Home",
                OwnerType.PLAYER,
                ownerId,
                worldId,
                Set.of(new ClaimChunk(worldId, 0, 0)),
                Map.of("build", false),
                members,
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
