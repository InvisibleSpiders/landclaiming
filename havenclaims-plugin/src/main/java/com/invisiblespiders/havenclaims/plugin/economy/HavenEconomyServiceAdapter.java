package com.invisiblespiders.havenclaims.plugin.economy;

import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import java.util.Objects;
import java.util.UUID;

public final class HavenEconomyServiceAdapter implements EconomyService {
    private final HavenEconomyService havenEconomyService;

    public HavenEconomyServiceAdapter(HavenEconomyService havenEconomyService) {
        this.havenEconomyService = Objects.requireNonNull(havenEconomyService, "havenEconomyService");
    }

    @Override
    public boolean available() {
        return havenEconomyService.isMoneyAvailable();
    }

    @Override
    public boolean withdraw(UUID playerId, double amount) {
        Objects.requireNonNull(playerId, "playerId");
        if (!available()) {
            return false;
        }
        return havenEconomyService.withdraw(playerId, amount);
    }

    @Override
    public boolean deposit(UUID playerId, double amount) {
        Objects.requireNonNull(playerId, "playerId");
        if (!available()) {
            return false;
        }
        try {
            havenEconomyService.deposit(playerId, amount);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public String format(double amount) {
        return havenEconomyService.format(amount);
    }
}
