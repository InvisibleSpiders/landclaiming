package com.invisiblespiders.havenclaims.plugin.limit;

public record ClaimCostQuote(
        int allowedChunks,
        int existingChunks,
        int selectedChunks,
        int proposedTotalChunks,
        int overageChunks,
        double cost
) {
}
