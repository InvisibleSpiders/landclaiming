package com.nick.landclaims.plugin.economy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class NoopEconomyServiceTest {
    @Test
    void noopEconomyIsUnavailableAndNeverWithdraws() {
        NoopEconomyService service = new NoopEconomyService();

        assertThat(service.available()).isFalse();
        assertThat(service.withdraw(UUID.randomUUID(), 10.0)).isFalse();
    }
}
