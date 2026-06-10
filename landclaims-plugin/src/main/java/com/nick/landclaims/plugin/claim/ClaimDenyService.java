package com.nick.landclaims.plugin.claim;

import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class ClaimDenyService {
    private static final String ADMIN_EDIT_PERMISSION = "landclaims.admin.claim.edit";

    private final ClaimRepository claimRepository;
    private final ClaimIndex claimIndex;

    public ClaimDenyService(ClaimRepository claimRepository, ClaimIndex claimIndex) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
    }

    public ClaimDenyResult denyPlayer(UUID actorId, Claim claim, UUID deniedPlayerId) {
        return denyPlayer(actorId, claim, deniedPlayerId, permission -> false);
    }

    public ClaimDenyResult denyPlayer(
            UUID actorId,
            Claim claim,
            UUID deniedPlayerId,
            Predicate<String> permissionCheck
    ) {
        ClaimDenyResult validationResult = validateMutation(actorId, claim, deniedPlayerId, permissionCheck);
        if (!validationResult.allowed()) {
            return validationResult;
        }

        Set<UUID> deniedPlayers = new HashSet<>(claim.deniedPlayers());
        deniedPlayers.add(deniedPlayerId);
        saveUpdatedClaim(claim, deniedPlayers);
        return ClaimDenyResult.success();
    }

    public ClaimDenyResult allowPlayer(UUID actorId, Claim claim, UUID deniedPlayerId) {
        return allowPlayer(actorId, claim, deniedPlayerId, permission -> false);
    }

    public ClaimDenyResult allowPlayer(
            UUID actorId,
            Claim claim,
            UUID deniedPlayerId,
            Predicate<String> permissionCheck
    ) {
        ClaimDenyResult validationResult = validateMutation(actorId, claim, deniedPlayerId, permissionCheck);
        if (!validationResult.allowed()) {
            return validationResult;
        }

        Set<UUID> deniedPlayers = new HashSet<>(claim.deniedPlayers());
        deniedPlayers.remove(deniedPlayerId);
        saveUpdatedClaim(claim, deniedPlayers);
        return ClaimDenyResult.success();
    }

    private ClaimDenyResult validateMutation(
            UUID actorId,
            Claim claim,
            UUID deniedPlayerId,
            Predicate<String> permissionCheck
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(deniedPlayerId, "deniedPlayerId");
        Objects.requireNonNull(permissionCheck, "permissionCheck");

        if (claim.owner() == OwnerType.ADMIN) {
            if (!permissionCheck.test(ADMIN_EDIT_PERMISSION)) {
                return ClaimDenyResult.denied("claim.deny.admin-claim");
            }
            if (claim.ownerUuid() != null && claim.ownerUuid().equals(deniedPlayerId)) {
                return ClaimDenyResult.denied("claim.deny.owner-is-not-deniable");
            }
            return ClaimDenyResult.success();
        }

        if (claim.ownerUuid() != null && claim.ownerUuid().equals(deniedPlayerId)) {
            return ClaimDenyResult.denied("claim.deny.owner-is-not-deniable");
        }

        boolean actorIsOwner = actorId.equals(claim.ownerUuid());
        if (actorIsOwner) {
            return ClaimDenyResult.success();
        }

        boolean actorIsManager = hasManagerRole(claim, actorId);
        if (!actorIsManager) {
            return ClaimDenyResult.denied("claim.deny.not-owner");
        }
        if (hasManagerRole(claim, deniedPlayerId)) {
            return ClaimDenyResult.denied("claim.deny.manager-cannot-deny-manager");
        }
        return ClaimDenyResult.success();
    }

    private boolean hasManagerRole(Claim claim, UUID playerId) {
        return claim.members().stream()
                .anyMatch(member -> member.memberUuid().equals(playerId) && member.role() == ClaimRole.MANAGER);
    }

    private void saveUpdatedClaim(Claim claim, Set<UUID> deniedPlayers) {
        Claim updatedClaim = new Claim(
                claim.id(),
                claim.name(),
                claim.owner(),
                claim.ownerUuid(),
                claim.worldId(),
                claim.claimChunks(),
                claim.flags(),
                claim.members(),
                deniedPlayers,
                claim.createdAt(),
                Instant.now()
        );
        claimRepository.saveClaim(updatedClaim);
        claimIndex.replace(updatedClaim);
    }
}
