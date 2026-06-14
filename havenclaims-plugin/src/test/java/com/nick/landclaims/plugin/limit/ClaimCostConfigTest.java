package com.nick.landclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ClaimCostConfigTest {
    @Test
    void readsFlatOverLimitPricingFromConfiguration() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("limits.over-limit.enabled", true);
        configuration.set("limits.over-limit.pricing-mode", "flat");
        configuration.set("limits.over-limit.flat-cost-per-chunk", 125.0);

        ClaimCostConfig costConfig = ClaimCostConfig.from(configuration);

        assertThat(costConfig.overLimitEnabled()).isTrue();
        assertThat(costConfig.priceOverage(4)).isEqualTo(500.0);
    }

    @Test
    void readsExponentialOverLimitPricingFromConfiguration() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("limits.over-limit.enabled", true);
        configuration.set("limits.over-limit.pricing-mode", "exponential");
        configuration.set("limits.over-limit.exponential-base-cost", 100.0);
        configuration.set("limits.over-limit.exponential-multiplier", 2.0);

        ClaimCostConfig costConfig = ClaimCostConfig.from(configuration);

        assertThat(costConfig.priceOverage(3)).isEqualTo(700.0);
    }

    @Test
    void disabledOverLimitPricingAlwaysReturnsZero() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("limits.over-limit.enabled", false);
        configuration.set("limits.over-limit.pricing-mode", "flat");
        configuration.set("limits.over-limit.flat-cost-per-chunk", 125.0);

        ClaimCostConfig costConfig = ClaimCostConfig.from(configuration);

        assertThat(costConfig.priceOverage(4)).isZero();
    }
}
