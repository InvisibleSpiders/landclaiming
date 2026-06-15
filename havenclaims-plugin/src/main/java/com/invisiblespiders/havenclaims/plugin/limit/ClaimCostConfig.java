package com.invisiblespiders.havenclaims.plugin.limit;

import org.bukkit.configuration.file.FileConfiguration;

public record ClaimCostConfig(
        boolean overLimitEnabled,
        PricingMode pricingMode,
        double flatCostPerChunk,
        double exponentialBaseCost,
        double exponentialMultiplier
) {
    public static ClaimCostConfig from(FileConfiguration configuration) {
        return new ClaimCostConfig(
                configuration.getBoolean("limits.over-limit.enabled", true),
                PricingMode.from(configuration.getString("limits.over-limit.pricing-mode", "exponential")),
                configuration.getDouble("limits.over-limit.flat-cost-per-chunk", 250.0),
                configuration.getDouble("limits.over-limit.exponential-base-cost", 250.0),
                configuration.getDouble("limits.over-limit.exponential-multiplier", 1.25)
        );
    }

    public double priceOverage(int overageChunks) {
        if (!overLimitEnabled) {
            return 0.0;
        }
        return switch (pricingMode) {
            case FLAT -> LimitService.flatOverLimitCost(overageChunks, flatCostPerChunk);
            case EXPONENTIAL -> LimitService.exponentialOverLimitCost(
                    overageChunks,
                    exponentialBaseCost,
                    exponentialMultiplier
            );
        };
    }

    public enum PricingMode {
        FLAT,
        EXPONENTIAL;

        static PricingMode from(String value) {
            if ("flat".equalsIgnoreCase(value)) {
                return FLAT;
            }
            return EXPONENTIAL;
        }
    }
}
