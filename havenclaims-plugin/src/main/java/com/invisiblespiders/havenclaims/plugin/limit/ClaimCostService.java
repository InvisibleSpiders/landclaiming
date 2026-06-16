package com.invisiblespiders.havenclaims.plugin.limit;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import java.util.Objects;
import java.util.UUID;

public final class ClaimCostService {
    private final ClaimIndex claimIndex;
    private final LimitService limitService;
    private ClaimCostConfig claimCostConfig;

    public ClaimCostService(ClaimIndex claimIndex, LimitService limitService, ClaimCostConfig claimCostConfig) {
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
        this.limitService = Objects.requireNonNull(limitService, "limitService");
        this.claimCostConfig = Objects.requireNonNull(claimCostConfig, "claimCostConfig");
    }

    public void reload(ClaimCostConfig newConfig) {
        this.claimCostConfig = Objects.requireNonNull(newConfig, "newConfig");
    }

    public boolean isPaidOverLimitEnabled() {
        return claimCostConfig.overLimitEnabled();
    }

    public int confirmTimeoutSeconds() {
        return claimCostConfig.confirmTimeoutSeconds();
    }

    public ClaimCostQuote quotePlayerClaim(UUID ownerId, ClaimRegion selectedRegion) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(selectedRegion, "selectedRegion");

        int allowedBlocks = limitService.getBlockLimit(ownerId);
        int existingBlocks = claimIndex.findAll().stream()
                .filter(c -> c.owner() == OwnerType.PLAYER && ownerId.equals(c.ownerUuid()))
                .mapToInt(c -> c.region().area())
                .sum();
        int selectedBlocks = selectedRegion.area();
        int proposedTotalBlocks = existingBlocks + selectedBlocks;
        int overageBlocks = limitService.overageBlocks(proposedTotalBlocks, allowedBlocks);
        return new ClaimCostQuote(allowedBlocks, existingBlocks, selectedBlocks,
                proposedTotalBlocks, overageBlocks, claimCostConfig.priceOverage(overageBlocks));
    }

    public double computeDeletionRefund(UUID ownerId, int blocksBeingRemoved) {
        Objects.requireNonNull(ownerId, "ownerId");
        int allowedBlocks = limitService.getBlockLimit(ownerId);
        int existingTotal = claimIndex.findAll().stream()
                .filter(c -> c.owner() == OwnerType.PLAYER && ownerId.equals(c.ownerUuid()))
                .mapToInt(c -> c.region().area())
                .sum();
        int afterDeletion = existingTotal - blocksBeingRemoved;
        double costBefore = claimCostConfig.priceOverage(Math.max(0, existingTotal - allowedBlocks));
        double costAfter  = claimCostConfig.priceOverage(Math.max(0, afterDeletion  - allowedBlocks));
        return Math.max(0.0, costBefore - costAfter);
    }
}
