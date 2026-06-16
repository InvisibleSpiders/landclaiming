package com.invisiblespiders.havenclaims.api.limit;

import java.util.UUID;

public interface HavenClaimsLimitService {
    int getBlockLimit(UUID playerId);
    void setBlockLimit(UUID playerId, int limit);
    void addBlocks(UUID playerId, int blocks);
    void removeBlocks(UUID playerId, int blocks);

    /** @deprecated Use {@link #getBlockLimit(UUID)} instead. */
    @Deprecated
    default int getLimit(UUID playerId) {
        return getBlockLimit(playerId);
    }

    /** @deprecated Use {@link #setBlockLimit(UUID, int)} instead. */
    @Deprecated
    default void setLimit(UUID playerId, int limit) {
        setBlockLimit(playerId, limit);
    }

    /** @deprecated Use {@link #addBlocks(UUID, int)} instead. */
    @Deprecated
    default void addChunks(UUID playerId, int chunks) {
        addBlocks(playerId, chunks);
    }

    /** @deprecated Use {@link #removeBlocks(UUID, int)} instead. */
    @Deprecated
    default void removeChunks(UUID playerId, int chunks) {
        removeBlocks(playerId, chunks);
    }
}
