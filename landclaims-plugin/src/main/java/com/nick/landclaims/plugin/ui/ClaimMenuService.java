package com.nick.landclaims.plugin.ui;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.OwnerType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ClaimMenuService {
    public ClaimMenu buildMenu(Claim claim, UUID viewerId) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(viewerId, "viewerId");

        boolean viewerOwnsClaim = viewerId.equals(claim.ownerUuid());
        boolean adminClaim = claim.owner() == OwnerType.ADMIN;

        List<ClaimMenuAction> actions = new ArrayList<>();
        actions.add(new ClaimMenuAction("Flags", "/claim flags"));
        actions.add(new ClaimMenuAction("Members", "/claim member list"));
        actions.add(new ClaimMenuAction("Info", "/claim info"));
        if (viewerOwnsClaim && !adminClaim) {
            actions.add(new ClaimMenuAction("Rename", "/claim rename"));
            actions.add(new ClaimMenuAction("Delete", "/claim delete"));
        }

        return new ClaimMenu(
                claim.name(),
                claim.owner().name(),
                claim.claimChunks().size(),
                claim.members().size(),
                claim.flags().size(),
                viewerOwnsClaim,
                adminClaim,
                actions
        );
    }
}
