package com.nick.landclaims.plugin.visual;

import com.nick.landclaims.plugin.claim.ClaimChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ChunkBorderPlanner {
    private static final int CHUNK_SIZE = 16;

    private ChunkBorderPlanner() {
    }

    public static ChunkBorderPlan plan(Set<ClaimChunk> chunks, double y, BorderColor color, int durationTicks) {
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(color, "color");

        List<BorderEdge> edges = new ArrayList<>();
        for (ClaimChunk chunk : chunks) {
            int minX = chunk.chunkX() * CHUNK_SIZE;
            int minZ = chunk.chunkZ() * CHUNK_SIZE;
            int maxX = minX + CHUNK_SIZE;
            int maxZ = minZ + CHUNK_SIZE;

            if (!chunks.contains(new ClaimChunk(chunk.worldId(), chunk.chunkX(), chunk.chunkZ() - 1))) {
                edges.add(new BorderEdge(chunk.worldId(), minX, minZ, maxX, minZ, y, color));
            }
            if (!chunks.contains(new ClaimChunk(chunk.worldId(), chunk.chunkX(), chunk.chunkZ() + 1))) {
                edges.add(new BorderEdge(chunk.worldId(), minX, maxZ, maxX, maxZ, y, color));
            }
            if (!chunks.contains(new ClaimChunk(chunk.worldId(), chunk.chunkX() - 1, chunk.chunkZ()))) {
                edges.add(new BorderEdge(chunk.worldId(), minX, minZ, minX, maxZ, y, color));
            }
            if (!chunks.contains(new ClaimChunk(chunk.worldId(), chunk.chunkX() + 1, chunk.chunkZ()))) {
                edges.add(new BorderEdge(chunk.worldId(), maxX, minZ, maxX, maxZ, y, color));
            }
        }

        edges.sort(Comparator
                .comparing(BorderEdge::worldId)
                .thenComparingInt(BorderEdge::x1)
                .thenComparingInt(BorderEdge::z1)
                .thenComparingInt(BorderEdge::x2)
                .thenComparingInt(BorderEdge::z2));
        return new ChunkBorderPlan(edges, durationTicks);
    }

    public static ChunkBorderPlan plan(
            Set<ClaimChunk> chunks,
            ChunkGroundHeightProvider heightProvider,
            BorderColor color,
            int durationTicks
    ) {
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(heightProvider, "heightProvider");
        Objects.requireNonNull(color, "color");

        List<BorderEdge> edges = new ArrayList<>();
        for (ClaimChunk chunk : chunks) {
            int minX = chunk.chunkX() * CHUNK_SIZE;
            int minZ = chunk.chunkZ() * CHUNK_SIZE;
            int maxX = minX + CHUNK_SIZE;
            int maxZ = minZ + CHUNK_SIZE;

            if (!chunks.contains(new ClaimChunk(chunk.worldId(), chunk.chunkX(), chunk.chunkZ() - 1))) {
                addHorizontalSegments(edges, chunk.worldId(), minX, maxX, minZ, minZ, heightProvider, color);
            }
            if (!chunks.contains(new ClaimChunk(chunk.worldId(), chunk.chunkX(), chunk.chunkZ() + 1))) {
                addHorizontalSegments(edges, chunk.worldId(), minX, maxX, maxZ, maxZ - 1, heightProvider, color);
            }
            if (!chunks.contains(new ClaimChunk(chunk.worldId(), chunk.chunkX() - 1, chunk.chunkZ()))) {
                addVerticalSegments(edges, chunk.worldId(), minX, minZ, maxZ, minX, heightProvider, color);
            }
            if (!chunks.contains(new ClaimChunk(chunk.worldId(), chunk.chunkX() + 1, chunk.chunkZ()))) {
                addVerticalSegments(edges, chunk.worldId(), maxX, minZ, maxZ, maxX - 1, heightProvider, color);
            }
        }

        edges.sort(Comparator
                .comparing(BorderEdge::worldId)
                .thenComparingInt(BorderEdge::x1)
                .thenComparingInt(BorderEdge::z1)
                .thenComparingInt(BorderEdge::x2)
                .thenComparingInt(BorderEdge::z2));
        return new ChunkBorderPlan(edges, durationTicks);
    }

    private static void addHorizontalSegments(
            List<BorderEdge> edges,
            UUID worldId,
            int minX,
            int maxX,
            int edgeZ,
            int sampleZ,
            ChunkGroundHeightProvider heightProvider,
            BorderColor color
    ) {
        for (int x = minX; x < maxX; x++) {
            edges.add(new BorderEdge(
                    worldId,
                    x,
                    edgeZ,
                    x + 1,
                    edgeZ,
                    heightProvider.borderY(worldId, x, sampleZ),
                    color));
        }
    }

    private static void addVerticalSegments(
            List<BorderEdge> edges,
            UUID worldId,
            int edgeX,
            int minZ,
            int maxZ,
            int sampleX,
            ChunkGroundHeightProvider heightProvider,
            BorderColor color
    ) {
        for (int z = minZ; z < maxZ; z++) {
            edges.add(new BorderEdge(
                    worldId,
                    edgeX,
                    z,
                    edgeX,
                    z + 1,
                    heightProvider.borderY(worldId, sampleX, z),
                    color));
        }
    }
}
