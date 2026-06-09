package com.nick.landclaims.plugin.economy;

import java.util.UUID;

public interface EconomyService {
    boolean available();

    boolean withdraw(UUID playerId, double amount);

    boolean deposit(UUID playerId, double amount);

    String format(double amount);
}
