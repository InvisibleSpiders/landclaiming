package com.nick.landclaims.plugin.limit;

import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ClaimCostService {
    private final ClaimRepository claimRepository;
    private final LimitService limitService;
    private final ClaimCostConfig claimCostConfig;

    public ClaimCostService(
            ClaimRepository claimRepository,
            LimitService limitService,
            ClaimCostConfig claimCostConfig
    ) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
        this.limitService = Objects.requireNonNull(limitService, "limitService");
        this.claimCostConfig = Objects.requireNonNull(claimCostConfig, "claimCostConfig");
    }

    public ClaimCostQuote quotePlayerClaim(UUID ownerId, Set<String> permissions, Set<ClaimChunk> selectedChunks) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(selectedChunks, "selectedChunks");

        int allowedChunks = limitService.resolveLimit(permissions);
        int existingChunks = claimRepository.findClaimsByOwner(OwnerType.PLAYER, ownerId).stream()
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
