package com.invisiblespiders.havenclaims.plugin.limit;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class OverLimitConfirmService {
    private final int timeoutSeconds;
    private final Map<UUID, PendingOverLimitPurchase> pending = new HashMap<>();

    public OverLimitConfirmService(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public void store(UUID playerId, ClaimRegion region, String claimName, double cost) {
        store(playerId, region, claimName, cost, Instant.now().plusSeconds(timeoutSeconds));
    }

    /** Package-private overload for testing with explicit expiry. */
    void store(UUID playerId, ClaimRegion region, String claimName, double cost, Instant expiresAt) {
        Objects.requireNonNull(playerId, "playerId");
        pending.put(playerId, new PendingOverLimitPurchase(region, claimName, cost, expiresAt));
    }

    public Optional<PendingOverLimitPurchase> getPending(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PendingOverLimitPurchase purchase = pending.get(playerId);
        if (purchase == null || purchase.isExpired()) {
            pending.remove(playerId);
            return Optional.empty();
        }
        return Optional.of(purchase);
    }

    public Optional<PendingOverLimitPurchase> consume(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        PendingOverLimitPurchase purchase = pending.remove(playerId);
        if (purchase == null || purchase.isExpired()) return Optional.empty();
        return Optional.of(purchase);
    }

    public void clear(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        pending.remove(playerId);
    }
}
