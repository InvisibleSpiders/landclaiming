package com.nick.landclaims.plugin.visual;

import java.util.UUID;

@FunctionalInterface
public interface ChunkGroundHeightProvider {
    double borderY(UUID worldId, int blockX, int blockZ);
}
