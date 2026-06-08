package com.nick.landclaims.plugin.economy;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.plugin.limit.ClaimCostQuote;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimPaymentServiceTest {
    @Test
    void allowsFreeClaimsWithoutEconomyWithdrawal() {
        RecordingEconomyService economyService = new RecordingEconomyService(true, false);
        ClaimPaymentService paymentService = new ClaimPaymentService(economyService);

        ClaimPaymentResult result = paymentService.charge(UUID.randomUUID(), quote(0.0));

        assertThat(result.allowed()).isTrue();
        assertThat(economyService.withdrawCalls).isZero();
    }

    @Test
    void deniesPaidClaimsWhenEconomyIsUnavailable() {
        ClaimPaymentService paymentService = new ClaimPaymentService(new RecordingEconomyService(false, false));

        ClaimPaymentResult result = paymentService.charge(UUID.randomUUID(), quote(100.0));

        assertThat(result.allowed()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claims.cost.economy-unavailable");
    }

    @Test
    void deniesPaidClaimsWhenWithdrawalFails() {
        ClaimPaymentService paymentService = new ClaimPaymentService(new RecordingEconomyService(true, false));

        ClaimPaymentResult result = paymentService.charge(UUID.randomUUID(), quote(100.0));

        assertThat(result.allowed()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claims.cost.insufficient-funds");
    }

    @Test
    void withdrawsPaidClaimCostWhenEconomyIsAvailable() {
        RecordingEconomyService economyService = new RecordingEconomyService(true, true);
        ClaimPaymentService paymentService = new ClaimPaymentService(economyService);
        UUID playerId = UUID.randomUUID();

        ClaimPaymentResult result = paymentService.charge(playerId, quote(100.0));

        assertThat(result.allowed()).isTrue();
        assertThat(economyService.withdrawCalls).isEqualTo(1);
        assertThat(economyService.lastPlayerId).isEqualTo(playerId);
        assertThat(economyService.lastAmount).isEqualTo(100.0);
    }

    private static ClaimCostQuote quote(double cost) {
        return new ClaimCostQuote(10, 8, 3, 11, 1, cost);
    }

    private static final class RecordingEconomyService implements EconomyService {
        private final boolean available;
        private final boolean withdrawResult;
        private int withdrawCalls;
        private UUID lastPlayerId;
        private double lastAmount;

        private RecordingEconomyService(boolean available, boolean withdrawResult) {
            this.available = available;
            this.withdrawResult = withdrawResult;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public boolean withdraw(UUID playerId, double amount) {
            withdrawCalls++;
            lastPlayerId = playerId;
            lastAmount = amount;
            return withdrawResult;
        }
    }
}
