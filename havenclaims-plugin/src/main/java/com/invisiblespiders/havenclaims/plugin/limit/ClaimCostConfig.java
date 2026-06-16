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
            case EXPONENTIAL -> exponentialOverLimitCost(overageChunks, exponentialBaseCost, exponentialMultiplier);
        };
    }

    private static double exponentialOverLimitCost(int overageChunks, double baseCost, double multiplier) {
        int n = Math.max(0, overageChunks);
        double base = Math.max(0.0, baseCost);
        double mult = Math.max(0.0, multiplier);
        double total = 0.0;
        for (int i = 0; i < n; i++) {
            total += base * Math.pow(mult, i);
            if (total >= Double.MAX_VALUE) {
                return Double.MAX_VALUE;
            }
        }
        return total;
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
