package com.nick.landclaims.plugin.claim;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ClaimIndex {
    private final Map<ClaimChunk, Claim> byChunk = new HashMap<>();

    public void load(Collection<Claim> claims) {
        Objects.requireNonNull(claims, "claims");
        byChunk.clear();
        claims.forEach(this::add);
    }

    public void add(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        for (ClaimChunk chunk : claim.claimChunks()) {
            byChunk.put(chunk, claim);
        }
    }

    public void replace(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        byChunk.entrySet().removeIf(entry -> entry.getValue().id().equals(claim.id()));
        add(claim);
    }

    public void remove(UUID claimId) {
        Objects.requireNonNull(claimId, "claimId");
        byChunk.entrySet().removeIf(entry -> entry.getValue().id().equals(claimId));
    }

    public Optional<Claim> findAt(ClaimChunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        return Optional.ofNullable(byChunk.get(chunk));
    }

    public List<Claim> findAll() {
        return byChunk.values().stream()
                .distinct()
                .toList();
    }
}
