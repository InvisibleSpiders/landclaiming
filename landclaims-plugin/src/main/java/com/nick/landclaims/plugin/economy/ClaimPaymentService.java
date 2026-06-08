package com.nick.landclaims.plugin.economy;

import com.nick.landclaims.plugin.limit.ClaimCostQuote;
import java.util.Objects;
import java.util.UUID;

public final class ClaimPaymentService {
    private final EconomyService economyService;

    public ClaimPaymentService(EconomyService economyService) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
    }

    public ClaimPaymentResult charge(UUID playerId, ClaimCostQuote quote) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(quote, "quote");
        if (quote.cost() <= 0.0) {
            return ClaimPaymentResult.success();
        }
        if (!economyService.available()) {
            return ClaimPaymentResult.denied("claims.cost.economy-unavailable");
        }
        if (!economyService.withdraw(playerId, quote.cost())) {
            return ClaimPaymentResult.denied("claims.cost.insufficient-funds");
        }
        return ClaimPaymentResult.success();
    }
}
