package com.nick.landclaims.api.protection;

import java.util.UUID;

public record ClaimProtectionAction(
        String flagKey,
        UUID worldId,
        int chunkX,
        int chunkZ
) {
}
