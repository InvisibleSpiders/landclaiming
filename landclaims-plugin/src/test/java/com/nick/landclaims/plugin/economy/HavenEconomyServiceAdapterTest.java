package com.nick.landclaims.plugin.economy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HavenEconomyServiceAdapterTest {
    @Test
    void delegatesMoneyAvailabilityAndWithdrawalsToHavenCoreEconomy() {
        HavenEconomyService havenEconomyService = mock(HavenEconomyService.class);
        HavenEconomyServiceAdapter adapter = new HavenEconomyServiceAdapter(havenEconomyService);
        UUID playerId = UUID.randomUUID();
        when(havenEconomyService.isMoneyAvailable()).thenReturn(true);
        when(havenEconomyService.withdraw(playerId, 125.0)).thenReturn(true);

        assertThat(adapter.available()).isTrue();
        assertThat(adapter.withdraw(playerId, 125.0)).isTrue();

        verify(havenEconomyService).withdraw(playerId, 125.0);
    }

    @Test
    void refusesWithdrawalsWhenMoneyEconomyIsUnavailable() {
        HavenEconomyService havenEconomyService = mock(HavenEconomyService.class);
        HavenEconomyServiceAdapter adapter = new HavenEconomyServiceAdapter(havenEconomyService);
        UUID playerId = UUID.randomUUID();
        when(havenEconomyService.isMoneyAvailable()).thenReturn(false);

        assertThat(adapter.withdraw(playerId, 125.0)).isFalse();
    }

    @Test
    void delegatesDepositsToHavenCoreEconomy() {
        HavenEconomyService havenEconomyService = mock(HavenEconomyService.class);
        HavenEconomyServiceAdapter adapter = new HavenEconomyServiceAdapter(havenEconomyService);
        UUID playerId = UUID.randomUUID();
        when(havenEconomyService.isMoneyAvailable()).thenReturn(true);

        assertThat(adapter.deposit(playerId, 50.0)).isTrue();

        verify(havenEconomyService).deposit(playerId, 50.0);
    }

    @Test
    void refusesDepositsWhenEconomyIsUnavailable() {
        HavenEconomyService havenEconomyService = mock(HavenEconomyService.class);
        HavenEconomyServiceAdapter adapter = new HavenEconomyServiceAdapter(havenEconomyService);
        when(havenEconomyService.isMoneyAvailable()).thenReturn(false);

        assertThat(adapter.deposit(UUID.randomUUID(), 50.0)).isFalse();
    }

    @Test
    void delegatesMoneyFormattingToHavenCoreEconomy() {
        HavenEconomyService havenEconomyService = mock(HavenEconomyService.class);
        HavenEconomyServiceAdapter adapter = new HavenEconomyServiceAdapter(havenEconomyService);
        when(havenEconomyService.format(125.5)).thenReturn("$125.50");

        assertThat(adapter.format(125.5)).isEqualTo("$125.50");
    }
}
