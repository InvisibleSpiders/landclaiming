package com.invisiblespiders.havenclaims.plugin.limit;

import org.bukkit.configuration.file.FileConfiguration;

public record ClaimCostConfig(
        boolean overLimitEnabled,
        double flatCostPerBlock,
        int confirmTimeoutSeconds
) {
    public static ClaimCostConfig from(FileConfiguration configuration) {
        return new ClaimCostConfig(
                configuration.getBoolean("limits.over-limit.enabled", true),
                configuration.getDouble("limits.over-limit.flat-cost-per-block", 0.10),
                configuration.getInt("limits.over-limit.confirm-timeout-seconds", 60)
        );
    }

    public double priceOverage(int overageBlocks) {
        if (!overLimitEnabled || overageBlocks <= 0) return 0.0;
        return overageBlocks * flatCostPerBlock;
    }
}
