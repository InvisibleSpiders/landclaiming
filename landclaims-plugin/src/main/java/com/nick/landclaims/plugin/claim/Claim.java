package com.nick.landclaims.plugin.claim;

import com.nick.landclaims.api.claim.ClaimChunkView;
import com.nick.landclaims.api.claim.ClaimView;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record Claim(
        UUID id,
        String name,
        OwnerType owner,
        UUID ownerUuid,
        UUID worldId,
        Set<ClaimChunk> claimChunks,
        Map<String, Boolean> flags,
        Instant createdAt,
        Instant updatedAt
) implements ClaimView {
    public Claim {
        id = Objects.requireNonNull(id, "id");
        name = Objects.requireNonNull(name, "name");
        owner = Objects.requireNonNull(owner, "owner");
        worldId = Objects.requireNonNull(worldId, "worldId");
        claimChunks = Set.copyOf(Objects.requireNonNull(claimChunks, "claimChunks"));
        flags = Map.copyOf(Objects.requireNonNull(flags, "flags"));
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override
    public String ownerType() {
        return owner.name();
    }

    @Override
    public Set<ClaimChunkView> chunks() {
        return claimChunks.stream()
                .map(chunk -> new ClaimChunkView(chunk.worldId(), chunk.chunkX(), chunk.chunkZ()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
