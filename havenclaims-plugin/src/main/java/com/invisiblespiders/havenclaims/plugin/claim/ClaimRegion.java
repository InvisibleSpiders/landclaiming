package com.invisiblespiders.havenclaims.plugin.claim;

import com.invisiblespiders.havenclaims.api.claim.ClaimRegionView;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ClaimRegion(UUID worldId, int minX, int minZ, int maxX, int maxZ)
        implements ClaimRegionView {
    public ClaimRegion {
        Objects.requireNonNull(worldId, "worldId");
        if (minX > maxX) throw new IllegalArgumentException("minX must be <= maxX");
        if (minZ > maxZ) throw new IllegalArgumentException("minZ must be <= maxZ");
    }

    @Override
    public int area() {
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    public boolean containsBlock(int blockX, int blockZ) {
        return blockX >= minX && blockX <= maxX && blockZ >= minZ && blockZ <= maxZ;
    }

    public Set<ClaimChunk> overlappingChunks() {
        int minChunkX = Math.floorDiv(minX, 16);
        int minChunkZ = Math.floorDiv(minZ, 16);
        int maxChunkX = Math.floorDiv(maxX, 16);
        int maxChunkZ = Math.floorDiv(maxZ, 16);
        Set<ClaimChunk> chunks = new HashSet<>();
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                chunks.add(new ClaimChunk(worldId, cx, cz));
            }
        }
        return Set.copyOf(chunks);
    }
}
