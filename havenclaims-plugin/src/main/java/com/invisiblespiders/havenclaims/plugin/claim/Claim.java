package com.invisiblespiders.havenclaims.plugin.claim;

import com.invisiblespiders.havenclaims.api.claim.ClaimChunkView;
import com.invisiblespiders.havenclaims.api.claim.ClaimView;
import com.invisiblespiders.havenclaims.api.flag.FlagState;
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
        Map<String, FlagState> flags,
        Set<ClaimMember> members,
        Set<UUID> deniedPlayers,
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
        members = Set.copyOf(Objects.requireNonNull(members, "members"));
        deniedPlayers = Set.copyOf(Objects.requireNonNull(deniedPlayers, "deniedPlayers"));
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public Claim(
            UUID id,
            String name,
            OwnerType owner,
            UUID ownerUuid,
            UUID worldId,
            Set<ClaimChunk> claimChunks,
            Map<String, FlagState> flags,
            Set<ClaimMember> members,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, name, owner, ownerUuid, worldId, claimChunks, flags, members, Set.of(), createdAt, updatedAt);
    }

    public Claim(
            UUID id,
            String name,
            OwnerType owner,
            UUID ownerUuid,
            UUID worldId,
            Set<ClaimChunk> claimChunks,
            Map<String, FlagState> flags,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, name, owner, ownerUuid, worldId, claimChunks, flags, Set.of(), Set.of(), createdAt, updatedAt);
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
