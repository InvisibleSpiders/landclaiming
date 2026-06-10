package com.nick.landclaims.plugin.storage;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.OwnerType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository {
    void saveClaim(Claim claim);

    void deleteClaim(UUID claimId);

    default void replaceClaims(Claim replacementClaim, List<UUID> deletedClaimIds) {
        Objects.requireNonNull(replacementClaim, "replacementClaim");
        Objects.requireNonNull(deletedClaimIds, "deletedClaimIds");
        for (UUID deletedClaimId : deletedClaimIds) {
            deleteClaim(deletedClaimId);
        }
        saveClaim(replacementClaim);
    }

    Optional<Claim> findClaimAt(UUID worldId, int chunkX, int chunkZ);

    Optional<Claim> findClaimById(UUID claimId);

    List<Claim> findClaimsByOwner(OwnerType ownerType, UUID ownerUuid);

    List<Claim> findAllClaims();
}
