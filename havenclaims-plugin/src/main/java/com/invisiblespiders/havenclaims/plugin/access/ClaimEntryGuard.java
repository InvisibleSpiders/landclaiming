package com.invisiblespiders.havenclaims.plugin.access;

import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public final class ClaimEntryGuard {
    private static final String ENTRY_BYPASS_PERMISSION = "landclaims.bypass.entry-deny";

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
}
