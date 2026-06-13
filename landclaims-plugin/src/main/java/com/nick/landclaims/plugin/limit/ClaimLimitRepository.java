package com.nick.landclaims.plugin.limit;

import java.util.OptionalInt;
import java.util.UUID;

public interface ClaimLimitRepository {
    OptionalInt getLimit(UUID playerId);
    void setLimit(UUID playerId, int limit);
}
