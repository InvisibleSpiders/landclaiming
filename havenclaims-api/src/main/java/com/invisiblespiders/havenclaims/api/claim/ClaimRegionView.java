package com.invisiblespiders.havenclaims.api.claim;

import java.util.UUID;

public interface ClaimRegionView {
    UUID worldId();
    int minX();
    int minZ();
    int maxX();
    int maxZ();
    int area();
}
