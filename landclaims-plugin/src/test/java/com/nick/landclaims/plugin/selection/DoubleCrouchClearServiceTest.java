package com.nick.landclaims.plugin.selection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DoubleCrouchClearServiceTest {
    @Test
    void secondCrouchWithinWindowClearsSelection() {
        AtomicLong currentTick = new AtomicLong(100);
        DoubleCrouchClearService service = new DoubleCrouchClearService(80, currentTick::get);
        UUID playerId = UUID.randomUUID();

        assertThat(service.recordCrouch(playerId)).isFalse();

        currentTick.set(180);

        assertThat(service.recordCrouch(playerId)).isTrue();
    }

    @Test
    void secondCrouchOutsideWindowDoesNotClearSelection() {
        AtomicLong currentTick = new AtomicLong(100);
        DoubleCrouchClearService service = new DoubleCrouchClearService(80, currentTick::get);
        UUID playerId = UUID.randomUUID();

        assertThat(service.recordCrouch(playerId)).isFalse();

        currentTick.set(181);

        assertThat(service.recordCrouch(playerId)).isFalse();
    }
}
