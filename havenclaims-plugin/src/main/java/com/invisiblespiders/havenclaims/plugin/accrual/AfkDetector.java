package com.invisiblespiders.havenclaims.plugin.accrual;

import java.util.UUID;

public interface AfkDetector {
    boolean isAfk(UUID playerId);
}
