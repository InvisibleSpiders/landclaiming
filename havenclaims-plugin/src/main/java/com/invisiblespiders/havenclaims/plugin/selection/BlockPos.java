package com.invisiblespiders.havenclaims.plugin.selection;

import java.util.Objects;
import java.util.UUID;

public record BlockPos(UUID worldId, int blockX, int blockZ) {
    public BlockPos {
        Objects.requireNonNull(worldId, "worldId");
    }
}
