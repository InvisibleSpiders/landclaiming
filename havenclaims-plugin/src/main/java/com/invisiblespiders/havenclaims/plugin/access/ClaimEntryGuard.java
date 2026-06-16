package com.invisiblespiders.havenclaims.plugin.access;

import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class ClaimEntryGuard {
    private static final String ENTRY_BYPASS_PERMISSION = "havenclaims.bypass.entry-deny";

    private final ClaimIndex claimIndex;

    public ClaimEntryGuard(ClaimIndex claimIndex) {
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
    }

    public ClaimEntryDecision checkMove(
            UUID playerId,
            ClaimChunk fromChunk,
            ClaimChunk toChunk,
            Predicate<String> permissionCheck
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(fromChunk, "fromChunk");
        Objects.requireNonNull(toChunk, "toChunk");
        Objects.requireNonNull(permissionCheck, "permissionCheck");

        if (fromChunk.equals(toChunk) || permissionCheck.test(ENTRY_BYPASS_PERMISSION)) {
            return ClaimEntryDecision.allowed();
        }

        Optional<Claim> targetClaim = claimIndex.findAt(toChunk);
        if (targetClaim.isEmpty() || !targetClaim.orElseThrow().deniedPlayers().contains(playerId)) {
            return ClaimEntryDecision.allowed();
        }
        return ClaimEntryDecision.denied(fromChunk, "claim.deny.entry-denied");
    }

    public ClaimChunk nearestUnclaimedBorderChunk(ClaimChunk currentChunk, Claim claim) {
        Objects.requireNonNull(currentChunk, "currentChunk");
        Objects.requireNonNull(claim, "claim");

        int minX = claim.overlappingChunks().stream().mapToInt(ClaimChunk::chunkX).min().orElse(currentChunk.chunkX());
        int maxX = claim.overlappingChunks().stream().mapToInt(ClaimChunk::chunkX).max().orElse(currentChunk.chunkX());
        int minZ = claim.overlappingChunks().stream().mapToInt(ClaimChunk::chunkZ).min().orElse(currentChunk.chunkZ());
        int maxZ = claim.overlappingChunks().stream().mapToInt(ClaimChunk::chunkZ).max().orElse(currentChunk.chunkZ());

        for (int radius = 1; radius <= 64; radius++) {
            ClaimChunk fallback = nearestUnclaimedInRing(currentChunk, claim, minX, maxX, minZ, maxZ, radius);
            if (fallback != null) {
                return fallback;
            }
        }
        return new ClaimChunk(claim.worldId(), maxX + 65, currentChunk.chunkZ());
    }

    private ClaimChunk nearestUnclaimedInRing(
            ClaimChunk currentChunk,
            Claim claim,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int radius
    ) {
        ClaimChunk best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int x = minX - radius; x <= maxX + radius; x++) {
            for (int z = minZ - radius; z <= maxZ + radius; z++) {
                if (x != minX - radius && x != maxX + radius && z != minZ - radius && z != maxZ + radius) {
                    continue;
                }
                ClaimChunk candidate = new ClaimChunk(claim.worldId(), x, z);
                if (claim.overlappingChunks().contains(candidate) || claimIndex.findAt(candidate).isPresent()) {
                    continue;
                }
                int distance = Math.abs(candidate.chunkX() - currentChunk.chunkX())
                        + Math.abs(candidate.chunkZ() - currentChunk.chunkZ());
                if (distance < bestDistance) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }
}
