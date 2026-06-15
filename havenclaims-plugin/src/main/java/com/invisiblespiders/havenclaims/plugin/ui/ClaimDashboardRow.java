package com.invisiblespiders.havenclaims.plugin.ui;

import java.util.Objects;
import java.util.UUID;

public record ClaimDashboardRow(
        UUID claimId,
        String claimName,
        int chunkCount,
        boolean currentClaim,
        String manageCommand
) {
    public ClaimDashboardRow {
        claimId = Objects.requireNonNull(claimId, "claimId");
        claimName = Objects.requireNonNull(claimName, "claimName");
        manageCommand = Objects.requireNonNull(manageCommand, "manageCommand");
    }
}
