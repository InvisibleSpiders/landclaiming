package com.invisiblespiders.havenclaims.plugin.claim;

import com.invisiblespiders.havenclaims.plugin.selection.BlockPos;
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

    public ClaimRegion blockRectangle(BlockPos p1, BlockPos p2) {
        Objects.requireNonNull(p1, "p1");
        Objects.requireNonNull(p2, "p2");
        if (!p1.worldId().equals(p2.worldId())) {
            throw new IllegalArgumentException("Both positions must be in the same world");
        }
        return new ClaimRegion(
                p1.worldId(),
                Math.min(p1.blockX(), p2.blockX()),
                Math.min(p1.blockZ(), p2.blockZ()),
                Math.max(p1.blockX(), p2.blockX()),
                Math.max(p1.blockZ(), p2.blockZ())
        );
    }

    public boolean isWithinBlockBuffer(ClaimRegion proposed, ClaimRegion existing, int bufferBlocks) {
        Objects.requireNonNull(proposed, "proposed");
        Objects.requireNonNull(existing, "existing");
        if (bufferBlocks < 0) throw new IllegalArgumentException("bufferBlocks must be non-negative");
        return minimumBlockGap(proposed, existing) < bufferBlocks;
    }

    static int minimumBlockGap(ClaimRegion a, ClaimRegion b) {
        if (!a.worldId().equals(b.worldId())) return Integer.MAX_VALUE;
        int gapX = Math.max(0, Math.max(a.minX(), b.minX()) - Math.min(a.maxX(), b.maxX()) - 1);
        int gapZ = Math.max(0, Math.max(a.minZ(), b.minZ()) - Math.min(a.maxZ(), b.maxZ()) - 1);
        return Math.max(gapX, gapZ);
    }
}
