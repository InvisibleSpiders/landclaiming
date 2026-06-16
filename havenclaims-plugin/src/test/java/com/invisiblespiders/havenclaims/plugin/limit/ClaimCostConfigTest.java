package com.invisiblespiders.havenclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ClaimCostConfigTest {
    @Test
    void readsFlatOverLimitPricingFromConfiguration() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("limits.over-limit.enabled", true);
        configuration.set("limits.over-limit.flat-cost-per-block", 0.25);

        ClaimCostConfig costConfig = ClaimCostConfig.from(configuration);

        assertThat(costConfig.overLimitEnabled()).isTrue();
        assertThat(costConfig.priceOverage(4)).isEqualTo(1.0);
    }

    @Test
    void disabledOverLimitPricingAlwaysReturnsZero() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("limits.over-limit.enabled", false);
        configuration.set("limits.over-limit.flat-cost-per-block", 0.25);

        ClaimCostConfig costConfig = ClaimCostConfig.from(configuration);

        assertThat(costConfig.priceOverage(4)).isZero();
    }

    @Test
    void zeroOrNegativeOverageAlwaysReturnsZero() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("limits.over-limit.enabled", true);
        configuration.set("limits.over-limit.flat-cost-per-block", 0.25);

        ClaimCostConfig costConfig = ClaimCostConfig.from(configuration);

        assertThat(costConfig.priceOverage(0)).isZero();
    }
}
