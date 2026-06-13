package com.nick.landclaims.plugin.limit;

import java.util.OptionalInt;
import java.util.UUID;

public interface ClaimLimitRepository {
    OptionalInt getLimit(UUID playerId);
    void setLimit(UUID playerId, int limit);

    /**
     * Atomically reads the current limit (or uses defaultLimit if absent),
     * applies the given operator, and writes the result.
     * The new value is floored at 1.
     */
    void updateLimit(UUID playerId, int defaultLimit, java.util.function.IntUnaryOperator operator);
}
