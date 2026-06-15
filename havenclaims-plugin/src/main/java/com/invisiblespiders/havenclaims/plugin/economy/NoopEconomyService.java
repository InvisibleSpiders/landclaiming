package com.invisiblespiders.havenclaims.plugin.economy;

import java.util.Locale;
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

    @Override
    public boolean deposit(UUID playerId, double amount) {
        return false;
    }

    @Override
    public String format(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }
}
