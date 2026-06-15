package com.invisiblespiders.havenclaims.plugin.claimmode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClaimModeCommandGuardTest {
    @Test
    void blocksConfiguredCommandsAndAllowsClaimModeCommands() {
        ClaimModeCommandGuard guard = new ClaimModeCommandGuard(
                new ClaimModeConfig(true, 5, List.of("storage", "pay"), List.of("claimmode", "cm", "claim"))
        );

        assertThat(guard.isBlocked("/storage open")).isTrue();
        assertThat(guard.isBlocked("/minecraft:pay Alice 10")).isTrue();
        assertThat(guard.isBlocked("StOrAgE")).isTrue();
        assertThat(guard.isBlocked("/claimmode off")).isFalse();
        assertThat(guard.isBlocked("/cm off")).isFalse();
        assertThat(guard.isBlocked("/claim mode")).isFalse();
        assertThat(guard.isBlocked("/minecraft:claim mode")).isFalse();
    }

    @Test
    void reloadReplacesConfiguredCommands() {
        ClaimModeCommandGuard guard = new ClaimModeCommandGuard(
                new ClaimModeConfig(true, 5, List.of("storage"), List.of("claimmode", "cm", "claim"))
        );

        guard.reload(new ClaimModeConfig(true, 5, List.of("pay"), List.of("claimmode", "cm", "claim")));

        assertThat(guard.isBlocked("/storage")).isFalse();
        assertThat(guard.isBlocked("/pay Alice 10")).isTrue();
    }
}
