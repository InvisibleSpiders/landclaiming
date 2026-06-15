package com.invisiblespiders.havenclaims.plugin.ui;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ClaimInfoView(
        UUID claimId,
        String claimName,
        String ownerType,
        int chunkCount,
        int memberCount,
        int deniedCount,
        int flagCount,
        boolean viewerOwnsClaim,
        List<ClaimMenuAction> actions,
        ClaimMenuAction backAction
) {
    public ClaimInfoView {
        claimId = Objects.requireNonNull(claimId, "claimId");
        claimName = Objects.requireNonNull(claimName, "claimName");
        ownerType = Objects.requireNonNull(ownerType, "ownerType");
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        backAction = Objects.requireNonNull(backAction, "backAction");
    }
}
