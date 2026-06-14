package com.nick.landclaims.api.claim;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface ClaimView {
    UUID id();

    String name();

    String ownerType();

    UUID ownerUuid();

    UUID worldId();

    Set<ClaimChunkView> chunks();

    Map<String, Boolean> flags();
}
