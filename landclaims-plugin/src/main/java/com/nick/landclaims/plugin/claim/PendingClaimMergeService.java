package com.nick.landclaims.plugin.claim;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PendingClaimMergeService {
    private final Map<UUID, PendingClaimMerge> pendingMerges = new HashMap<>();

    public void put(UUID playerId, String claimName, Set<ClaimChunk> chunks) {
        Objects.requireNonNull(playerId, "playerId");
        pendingMerges.put(playerId, new PendingClaimMerge(claimName, chunks));
    }

    public Optional<PendingClaimMerge> get(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(pendingMerges.get(playerId));
    }

    public Optional<PendingClaimMerge> consume(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(pendingMerges.remove(playerId));
    }

    public void clear(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        pendingMerges.remove(playerId);
    }
}
