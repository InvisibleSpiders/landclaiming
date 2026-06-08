package com.nick.landclaims.plugin.storage;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.OwnerType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository {
    void initialize();

    void saveClaim(Claim claim);

    void deleteClaim(UUID claimId);

    Optional<Claim> findClaimAt(UUID worldId, int chunkX, int chunkZ);

    Optional<Claim> findClaimById(UUID claimId);

    List<Claim> findClaimsByOwner(OwnerType ownerType, UUID ownerUuid);

    List<Claim> findAllClaims();
}
