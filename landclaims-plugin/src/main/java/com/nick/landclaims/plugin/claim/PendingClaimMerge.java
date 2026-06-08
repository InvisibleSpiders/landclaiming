package com.nick.landclaims.plugin.claim;

import java.util.Objects;
import java.util.Set;

public record PendingClaimMerge(String claimName, Set<ClaimChunk> chunks) {
    public PendingClaimMerge {
        claimName = Objects.requireNonNull(claimName, "claimName");
        chunks = Set.copyOf(Objects.requireNonNull(chunks, "chunks"));
    }
}
