package com.invisiblespiders.havenclaims.plugin.limit;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ClaimCostService {
    private final ClaimIndex claimIndex;
    private final LimitService limitService;
    private ClaimCostConfig claimCostConfig;

    public ClaimCostService(
            ClaimIndex claimIndex,
            LimitService limitService,
            ClaimCostConfig claimCostConfig
    ) {
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

    public double computeDeletionRefund(UUID ownerId, int chunksBeingRemoved) {
        Objects.requireNonNull(ownerId, "ownerId");
        int allowedChunks = limitService.getLimit(ownerId);
        int existingTotal = claimIndex.findAll().stream()
                .filter(c -> c.owner() == OwnerType.PLAYER && ownerId.equals(c.ownerUuid()))
                .mapToInt(c -> c.claimChunks().size())
                .sum();
        int afterDeletion = existingTotal - chunksBeingRemoved;
        double costBefore = claimCostConfig.priceOverage(Math.max(0, existingTotal - allowedChunks));
        double costAfter  = claimCostConfig.priceOverage(Math.max(0, afterDeletion  - allowedChunks));
        return Math.max(0.0, costBefore - costAfter);
    }

    public ClaimCostQuote quotePlayerClaim(UUID ownerId, Set<ClaimChunk> selectedChunks) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(selectedChunks, "selectedChunks");

        int allowedChunks = limitService.getLimit(ownerId);
        int existingChunks = claimIndex.findAll().stream()
                .filter(claim -> claim.owner() == OwnerType.PLAYER && ownerId.equals(claim.ownerUuid()))
                .mapToInt(claim -> claim.claimChunks().size())
                .sum();
        int proposedTotalChunks = existingChunks + selectedChunks.size();
        int overageChunks = limitService.overageChunks(proposedTotalChunks, allowedChunks);
        return new ClaimCostQuote(
                allowedChunks,
                existingChunks,
                selectedChunks.size(),
                proposedTotalChunks,
                overageChunks,
                claimCostConfig.priceOverage(overageChunks)
        );
    }
}
