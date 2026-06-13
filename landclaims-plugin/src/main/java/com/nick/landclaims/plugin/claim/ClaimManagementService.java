package com.nick.landclaims.plugin.claim;

import com.nick.landclaims.plugin.economy.ClaimPaymentService;
import com.nick.landclaims.plugin.limit.ClaimCostQuote;
import com.nick.landclaims.plugin.limit.ClaimCostService;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ClaimManagementService {
    private final ClaimRepository claimRepository;
    private final ClaimIndex claimIndex;

    public ClaimManagementService(ClaimRepository claimRepository, ClaimIndex claimIndex) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
    }

    public ClaimManagementResult renameClaim(UUID playerId, UUID claimId, String newName, int maxNameLength) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(newName, "newName");

        Optional<Claim> found = claimRepository.findClaimById(claimId);
        if (found.isEmpty()) {
            return ClaimManagementResult.denied("claim.manage.not-found");
        }
        Claim claim = found.get();
        if (claim.owner() != OwnerType.PLAYER || !playerId.equals(claim.ownerUuid())) {
            return ClaimManagementResult.denied("claim.manage.not-owner");
        }

        String trimmedName = newName.trim();
        if (trimmedName.isEmpty() || trimmedName.length() > maxNameLength) {
            return ClaimManagementResult.denied("claim.manage.invalid-name");
        }

        Claim renamed = new Claim(claim.id(), trimmedName, claim.owner(), claim.ownerUuid(),
                claim.worldId(), claim.claimChunks(), claim.flags(), claim.members(),
                claim.deniedPlayers(), claim.createdAt(), Instant.now());
        claimRepository.saveClaim(renamed);
        claimIndex.replace(renamed);
        return ClaimManagementResult.ok("claim.manage.renamed");
    }

    public ClaimManagementResult deleteClaim(UUID playerId, UUID claimId,
            ClaimCostService costService, ClaimPaymentService paymentService) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(costService, "costService");
        Objects.requireNonNull(paymentService, "paymentService");

        Optional<Claim> found = claimRepository.findClaimById(claimId);
        if (found.isEmpty()) {
            return ClaimManagementResult.denied("claim.manage.not-found");
        }
        Claim claim = found.get();
        if (claim.owner() != OwnerType.PLAYER || !playerId.equals(claim.ownerUuid())) {
            return ClaimManagementResult.denied("claim.manage.not-owner");
        }

        double refund = costService.computeDeletionRefund(playerId, claim.claimChunks().size());
        if (refund > 0.0) {
            paymentService.refund(playerId, new ClaimCostQuote(0, 0, 0, 0, 0, refund));
        }
        claimRepository.deleteClaim(claimId);
        claimIndex.remove(claimId);
        return ClaimManagementResult.ok("claim.manage.deleted");
    }
}
