package com.invisiblespiders.havenclaims.api.limit;

import java.util.UUID;

public interface HavenClaimsLimitService {
    int getLimit(UUID playerId);
    void setLimit(UUID playerId, int limit);
    void addChunks(UUID playerId, int chunks);
    void removeChunks(UUID playerId, int chunks);
}
