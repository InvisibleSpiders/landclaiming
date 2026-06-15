package com.invisiblespiders.havenclaims.plugin.claimmode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ClaimModeConfigTest {
    @Test
    void readsConfiguredClaimModeValues() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("claim-mode.enabled", true);
        configuration.set("claim-mode.history-per-player", 0);
        configuration.set("claim-mode.blocked-commands", List.of("/Storage", "pay balance", "minecraft:give", " PAY ", " "));
        configuration.set("claim-mode.allowed-commands", List.of("/ClaimMode toggle", "CM", "claim", "cm"));

        ClaimModeConfig config = ClaimModeConfig.from(configuration);

        assertThat(config.enabled()).isTrue();
        assertThat(config.historyPerPlayer()).isEqualTo(1);
        assertThat(config.blockedCommands()).containsExactly("storage", "pay", "give");
        assertThat(config.allowedCommands()).containsExactly("claimmode", "cm", "claim");
    }

    @Test
    void keepsExplicitEmptyCommandLists() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("claim-mode.blocked-commands", List.of());
        configuration.set("claim-mode.allowed-commands", List.of());

        ClaimModeConfig config = ClaimModeConfig.from(configuration);

        assertThat(config.blockedCommands()).isEmpty();
        assertThat(config.allowedCommands()).isEmpty();
    }

    @Test
    void suppliesSafeDefaults() {
        ClaimModeConfig config = ClaimModeConfig.from(new YamlConfiguration());

        assertThat(config.enabled()).isTrue();
        assertThat(config.historyPerPlayer()).isEqualTo(5);
        assertThat(config.blockedCommands()).contains("storage", "vault", "shop", "auction", "ah", "trade", "pay", "sell", "buy", "kit", "mail");
        assertThat(config.allowedCommands()).contains("claimmode", "cm", "claim");
    }
}
