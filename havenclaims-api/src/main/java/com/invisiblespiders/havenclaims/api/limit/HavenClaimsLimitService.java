package com.invisiblespiders.havenclaims.api.limit;

import java.util.UUID;

public interface HavenClaimsLimitService {
    int getBlockLimit(UUID playerId);
    void setBlockLimit(UUID playerId, int limit);
    void addBlocks(UUID playerId, int blocks);
    void removeBlocks(UUID playerId, int blocks);
}
