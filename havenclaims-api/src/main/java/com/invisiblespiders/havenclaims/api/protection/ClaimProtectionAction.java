package com.invisiblespiders.havenclaims.api.protection;

import java.util.UUID;

public record ClaimProtectionAction(
        String flagKey,
        UUID worldId,
        int chunkX,
        int chunkZ
) {
}
