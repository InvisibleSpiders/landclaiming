package com.invisiblespiders.havenclaims.plugin.ui;

import java.util.Objects;

public record ClaimCreatePreview(
        String claimName,
        int selectedBlocks,
        int proposedTotalBlocks,
        int allowedBlocks,
        int overageBlocks,
        String cost,
        ClaimMenuAction confirmAction,
        ClaimMenuAction cancelAction
) {
    public ClaimCreatePreview {
        claimName = Objects.requireNonNull(claimName, "claimName");
        cost = Objects.requireNonNull(cost, "cost");
        confirmAction = Objects.requireNonNull(confirmAction, "confirmAction");
        cancelAction = Objects.requireNonNull(cancelAction, "cancelAction");
    }
}
