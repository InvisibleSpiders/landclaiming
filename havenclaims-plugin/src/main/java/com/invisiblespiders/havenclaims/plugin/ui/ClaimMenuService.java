package com.invisiblespiders.havenclaims.plugin.ui;

import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ClaimMenuService {
    public ClaimMenu buildMenu(Claim claim, UUID viewerId) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(viewerId, "viewerId");

        return new ClaimMenu(
                claim.name(),
                claim.owner().name(),
                claim.claimChunks().size(),
                claim.members().size(),
                claim.flags().size(),
                viewerId.equals(claim.ownerUuid()),
                claim.owner() == OwnerType.ADMIN,
                List.of(
                        new ClaimMenuAction("Flags", "/claim flags"),
                        new ClaimMenuAction("Members", "/claim member list"),
                        new ClaimMenuAction("Info", "/claim info")
                )
        );
    }
}
