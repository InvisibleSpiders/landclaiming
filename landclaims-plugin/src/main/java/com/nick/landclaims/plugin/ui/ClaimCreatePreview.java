package com.nick.landclaims.plugin.ui;

import java.util.Objects;

public record ClaimCreatePreview(
        String claimName,
        int selectedChunks,
        int proposedTotalChunks,
        int allowedChunks,
        int overageChunks,
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
