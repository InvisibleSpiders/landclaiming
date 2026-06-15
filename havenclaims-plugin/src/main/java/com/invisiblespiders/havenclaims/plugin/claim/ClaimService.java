package com.invisiblespiders.havenclaims.plugin.claim;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class ClaimService {
    public Set<ClaimChunk> expandRectangle(UUID worldId, int firstX, int firstZ, int secondX, int secondZ) {
        Objects.requireNonNull(worldId, "worldId");

        int minX = Math.min(firstX, secondX);
        int maxX = Math.max(firstX, secondX);
        int minZ = Math.min(firstZ, secondZ);
        int maxZ = Math.max(firstZ, secondZ);

        Set<ClaimChunk> chunks = new HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(new ClaimChunk(worldId, x, z));
            }
        }
        return Set.copyOf(chunks);
    }

    public boolean isWithinChunkBuffer(ClaimChunk proposed, ClaimChunk existing, int bufferDistance) {
        Objects.requireNonNull(proposed, "proposed");
        Objects.requireNonNull(existing, "existing");

        if (bufferDistance < 0) {
            throw new IllegalArgumentException("bufferDistance must be non-negative");
        }

        if (!proposed.worldId().equals(existing.worldId())) {
            return false;
        }

        int deltaX = Math.abs(proposed.chunkX() - existing.chunkX());
        int deltaZ = Math.abs(proposed.chunkZ() - existing.chunkZ());
        return Math.max(deltaX, deltaZ) <= bufferDistance;
    }
}
