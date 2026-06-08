package com.nick.landclaims.plugin.ui;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.OwnerType;
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
                        new ClaimMenuAction("Flags", "/claims flag list"),
                        new ClaimMenuAction("Members", "/claims member list"),
                        new ClaimMenuAction("Info", "/claims info")
                )
        );
    }
}
