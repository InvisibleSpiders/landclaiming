package com.invisiblespiders.havenclaims.plugin.limit;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class LimitService {
    private final int defaultLimit;
    private final Map<String, Integer> permissionLimits;

    public LimitService(int defaultLimit, Map<String, Integer> permissionLimits) {
        this.defaultLimit = defaultLimit;
        this.permissionLimits = Map.copyOf(Objects.requireNonNull(permissionLimits, "permissionLimits"));
    }

    public int resolveLimit(Set<String> permissions) {
        Objects.requireNonNull(permissions, "permissions");

        int resolved = defaultLimit;
        for (String permission : permissions) {
            Integer configuredLimit = permissionLimits.get(permission);
            if (configuredLimit != null && configuredLimit > resolved) {
                resolved = configuredLimit;
            }
        }
        return resolved;
    }

    public int overageChunks(int proposedTotalChunks, int allowedChunks) {
        return Math.max(0, proposedTotalChunks - allowedChunks);
    }

    public static double flatOverLimitCost(int overageChunks, double costPerChunk) {
        return Math.max(0, overageChunks) * Math.max(0.0, costPerChunk);
    }

    public static double exponentialOverLimitCost(int overageChunks, double baseCost, double multiplier) {
        int normalizedOverageChunks = Math.max(0, overageChunks);
        double normalizedBaseCost = Math.max(0.0, baseCost);
        double normalizedMultiplier = Math.max(0.0, multiplier);
        double total = 0.0;
        for (int chunkNumber = 0; chunkNumber < normalizedOverageChunks; chunkNumber++) {
            total += normalizedBaseCost * Math.pow(normalizedMultiplier, chunkNumber);
        }
        return total;
    }
}
