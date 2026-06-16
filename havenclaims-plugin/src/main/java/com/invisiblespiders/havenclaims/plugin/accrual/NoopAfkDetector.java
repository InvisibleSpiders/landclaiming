package com.invisiblespiders.havenclaims.plugin.accrual;

import java.util.UUID;

public final class NoopAfkDetector implements AfkDetector {
    @Override
    public boolean isAfk(UUID playerId) {
        return false;
    }
}
