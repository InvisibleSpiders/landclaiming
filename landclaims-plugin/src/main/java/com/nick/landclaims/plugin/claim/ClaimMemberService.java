package com.nick.landclaims.plugin.claim;

import com.nick.landclaims.plugin.storage.ClaimRepository;
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
        ClaimMemberResult validationResult = validateOwnerMutation(actorId, claim, memberId);
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
        ClaimMemberResult validationResult = validateOwnerMutation(actorId, claim, memberId);
        if (!validationResult.allowed()) {
            return validationResult;
        }

        Set<ClaimMember> members = new HashSet<>(claim.members());
        members.removeIf(member -> member.memberUuid().equals(memberId));
        saveUpdatedClaim(claim, members);
        return ClaimMemberResult.success();
    }

    private ClaimMemberResult validateOwnerMutation(UUID actorId, Claim claim, UUID memberId) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(memberId, "memberId");
        if (claim.owner() == OwnerType.ADMIN) {
            return ClaimMemberResult.denied("claim.member.admin-claim");
        }
        if (!actorId.equals(claim.ownerUuid())) {
            return ClaimMemberResult.denied("claim.member.not-owner");
        }
        if (actorId.equals(memberId)) {
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
                claim.worldId(),
                claim.claimChunks(),
                claim.flags(),
                members,
                claim.createdAt(),
                Instant.now()
        );
        claimRepository.saveClaim(updatedClaim);
        claimIndex.replace(updatedClaim);
    }
}
