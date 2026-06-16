package com.invisiblespiders.havenclaims.plugin.limit;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import java.time.Instant;
import java.util.Objects;

public record PendingOverLimitPurchase(
        ClaimRegion region,
        String claimName,
        double cost,
        Instant expiresAt
) {
    public PendingOverLimitPurchase {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(claimName, "claimName");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
