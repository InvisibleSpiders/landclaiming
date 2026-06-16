package com.invisiblespiders.havenclaims.plugin.limit;

public record ClaimCostQuote(
        int allowedBlocks,
        int existingBlocks,
        int selectedBlocks,
        int proposedTotalBlocks,
        int overageBlocks,
        double cost
) {}
