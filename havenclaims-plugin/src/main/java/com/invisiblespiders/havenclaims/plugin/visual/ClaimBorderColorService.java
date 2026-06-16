package com.invisiblespiders.havenclaims.plugin.visual;

import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimCreationService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimValidationResult;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimCostQuote;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimCostService;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ClaimBorderColorService {
    private static final String PREVIEW_CLAIM_NAME = "Preview";

    private final ClaimCreationService claimCreationService;
    private final ClaimIndex claimIndex;
    private final ClaimCostService claimCostService;

    public ClaimBorderColorService(
            ClaimCreationService claimCreationService,
            ClaimIndex claimIndex,
            ClaimCostService claimCostService
    ) {
        this.claimCreationService = Objects.requireNonNull(claimCreationService, "claimCreationService");
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
        this.claimCostService = claimCostService;
    }

    public BorderColor colorForPlayerSelection(
            UUID ownerId,
            String claimName,
            Set<ClaimChunk> chunks,
            Set<String> permissions
    ) {
        return colorForPlayerSelection(ownerId, Optional.of(claimName), chunks, permissions);
    }

    public BorderColor colorForPlayerSelection(
            UUID ownerId,
            Optional<String> claimName,
            Set<ClaimChunk> chunks,
            Set<String> permissions
    ) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(claimName, "claimName");
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(permissions, "permissions");

        String validationName = claimName.filter(name -> !name.isBlank()).orElse(PREVIEW_CLAIM_NAME);
        ClaimRegion region = chunksAsRegion(chunks);
        ClaimValidationResult validationResult = claimCreationService.validatePlayerClaim(
                ownerId,
                validationName,
                region,
                permissions.contains("havenclaims.bypass.claim-buffer")
        );
        if (!validationResult.isAllowed()) {
            return BorderColor.RED;
        }

        if (claimName.filter(name -> !name.isBlank())
                .map(name -> !claimCreationService.findMergeTargets(ownerId, name, region).isEmpty())
                .orElseGet(() -> bordersOwnerClaim(ownerId, chunks))) {
            return BorderColor.YELLOW;
        }

        if (claimCostService != null && !permissions.contains("havenclaims.bypass.claim-limit")) {
            ClaimCostQuote quote = claimCostService.quotePlayerClaim(ownerId, region);
            if (quote.cost() > 0.0) {
                return BorderColor.AQUA;
            }
        }

        return BorderColor.GREEN;
    }

    private boolean bordersOwnerClaim(UUID ownerId, Set<ClaimChunk> chunks) {
        return claimIndex.findAll().stream()
                .filter(claim -> claim.owner() == OwnerType.PLAYER)
                .filter(claim -> ownerId.equals(claim.ownerUuid()))
                .anyMatch(claim -> chunks.stream().anyMatch(chunk -> bordersClaim(chunk, claim)));
    }

    private static ClaimRegion chunksAsRegion(Set<ClaimChunk> chunks) {
        if (chunks.isEmpty()) throw new IllegalArgumentException("chunks cannot be empty");
        UUID worldId = chunks.iterator().next().worldId();
        int minX = chunks.stream().mapToInt(c -> c.chunkX() * 16).min().getAsInt();
        int minZ = chunks.stream().mapToInt(c -> c.chunkZ() * 16).min().getAsInt();
        int maxX = chunks.stream().mapToInt(c -> c.chunkX() * 16 + 15).max().getAsInt();
        int maxZ = chunks.stream().mapToInt(c -> c.chunkZ() * 16 + 15).max().getAsInt();
        return new ClaimRegion(worldId, minX, minZ, maxX, maxZ);
    }

    private boolean bordersClaim(ClaimChunk proposedChunk, Claim claim) {
        return claim.overlappingChunks().stream().anyMatch(existingChunk ->
                proposedChunk.worldId().equals(existingChunk.worldId())
                        && Math.abs(proposedChunk.chunkX() - existingChunk.chunkX())
                        + Math.abs(proposedChunk.chunkZ() - existingChunk.chunkZ()) == 1
        );
    }
}
