package com.nick.landclaims.plugin.economy;

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
}
