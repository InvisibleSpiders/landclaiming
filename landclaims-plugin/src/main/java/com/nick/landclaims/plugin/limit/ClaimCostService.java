package com.nick.landclaims.plugin.limit;

import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.OwnerType;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ClaimCostService {
    private final ClaimIndex claimIndex;
    private final LimitService limitService;
    private final ClaimCostConfig claimCostConfig;

    public ClaimCostService(
            ClaimIndex claimIndex,
            LimitService limitService,
            ClaimCostConfig claimCostConfig
    ) {
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
        this.limitService = Objects.requireNonNull(limitService, "limitService");
        this.claimCostConfig = Objects.requireNonNull(claimCostConfig, "claimCostConfig");
    }

    public ClaimCostQuote quotePlayerClaim(UUID ownerId, Set<String> permissions, Set<ClaimChunk> selectedChunks) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(permissions, "permissions");
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
