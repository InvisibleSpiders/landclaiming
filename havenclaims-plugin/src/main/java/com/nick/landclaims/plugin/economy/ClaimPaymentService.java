package com.nick.landclaims.plugin.economy;

import com.nick.landclaims.plugin.limit.ClaimCostQuote;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

public final class ClaimPaymentService {
    private static final Logger LOGGER = Logger.getLogger(ClaimPaymentService.class.getName());

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

    public void refund(UUID playerId, ClaimCostQuote quote) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(quote, "quote");
        if (quote.cost() <= 0.0) {
            return;
        }
        if (!economyService.available()) {
            LOGGER.warning("Economy unavailable during refund — player " + playerId + " was charged "
                    + quote.cost() + " but could not be refunded.");
            return;
        }
        if (!economyService.deposit(playerId, quote.cost())) {
            LOGGER.warning("Deposit failed during refund — player " + playerId + " was charged "
                    + quote.cost() + " but the deposit returned false.");
        }
    }

    public String format(double amount) {
        return economyService.format(amount);
    }
}
