package com.invisiblespiders.havenclaims.plugin.claim;

import com.invisiblespiders.havenclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ClaimMemberService {
    private final ClaimRepository claimRepository;
    private final ClaimIndex claimIndex;

    public ClaimMemberService(ClaimRepository claimRepository, ClaimIndex claimIndex) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
    }

    public ClaimMemberResult addMember(UUID actorId, Claim claim, UUID memberId, ClaimRole role) {
        Objects.requireNonNull(role, "role");
        ClaimMemberResult validationResult = validateMutation(actorId, claim, memberId, role);
        if (!validationResult.allowed()) {
            return validationResult;
        }

        Set<ClaimMember> members = new HashSet<>(claim.members());
        members.removeIf(member -> member.memberUuid().equals(memberId));
        members.add(new ClaimMember(memberId, role));
        saveUpdatedClaim(claim, members);
        return ClaimMemberResult.success();
    }

    public ClaimMemberResult removeMember(UUID actorId, Claim claim, UUID memberId) {
        ClaimMemberResult validationResult = validateMutation(actorId, claim, memberId, null);
        if (!validationResult.allowed()) {
            return validationResult;
        }

        Set<ClaimMember> members = new HashSet<>(claim.members());
        members.removeIf(member -> member.memberUuid().equals(memberId));
        saveUpdatedClaim(claim, members);
        return ClaimMemberResult.success();
    }

    private ClaimMemberResult validateMutation(UUID actorId, Claim claim, UUID memberId, ClaimRole targetRole) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(memberId, "memberId");
        if (claim.owner() == OwnerType.ADMIN) {
            return ClaimMemberResult.denied("claim.member.admin-claim");
        }

        boolean actorIsOwner = actorId.equals(claim.ownerUuid());
        if (!actorIsOwner) {
            boolean actorIsManager = claim.members().stream()
                    .anyMatch(m -> m.memberUuid().equals(actorId) && m.role() == ClaimRole.MANAGER);
            if (!actorIsManager) {
                return ClaimMemberResult.denied("claim.member.not-owner");
            }
            // Managers may only add/remove MEMBER-role members, not other MANAGERs.
            if (targetRole != null && targetRole != ClaimRole.MEMBER) {
                return ClaimMemberResult.denied("claim.member.manager-cannot-promote");
            }
            boolean targetIsManager = claim.members().stream()
                    .anyMatch(m -> m.memberUuid().equals(memberId) && m.role() == ClaimRole.MANAGER);
            if (targetIsManager) {
                return ClaimMemberResult.denied("claim.member.manager-cannot-remove-manager");
            }
        }

        if (claim.ownerUuid() != null && claim.ownerUuid().equals(memberId)) {
            return ClaimMemberResult.denied("claim.member.owner-is-not-member");
        }
        return ClaimMemberResult.success();
    }

    private void saveUpdatedClaim(Claim claim, Set<ClaimMember> members) {
        Claim updatedClaim = new Claim(
                claim.id(),
                claim.name(),
                claim.owner(),
                claim.ownerUuid(),
                claim.region(),
                claim.flags(),
                members,
                claim.deniedPlayers(),
                claim.createdAt(),
                Instant.now()
        );
        claimRepository.saveClaim(updatedClaim);
        claimIndex.replace(updatedClaim);
    }
}
