package com.nick.landclaims.plugin.claim;

import java.util.UUID;

public record ClaimChunk(UUID worldId, int chunkX, int chunkZ) {
}
