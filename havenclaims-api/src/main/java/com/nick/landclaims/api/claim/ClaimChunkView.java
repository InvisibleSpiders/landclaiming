package com.nick.landclaims.api.claim;

import java.util.UUID;

public record ClaimChunkView(UUID worldId, int chunkX, int chunkZ) {
}
