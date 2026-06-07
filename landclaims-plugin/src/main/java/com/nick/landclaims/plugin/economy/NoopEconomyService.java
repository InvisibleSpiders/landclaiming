package com.nick.landclaims.plugin.economy;

import java.util.UUID;

public final class NoopEconomyService implements EconomyService {
    @Override
    public boolean available() {
        return false;
    }

    @Override
    public boolean withdraw(UUID playerId, double amount) {
        return false;
    }
}
