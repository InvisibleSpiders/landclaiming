package com.invisiblespiders.havenclaims.plugin.limit;

import com.invisiblespiders.havenclaims.api.limit.HavenClaimsLimitService;
import java.util.Objects;
import java.util.UUID;

public final class LimitService implements HavenClaimsLimitService {
    private int defaultLimit;
    private final ClaimLimitRepository repository;

    public LimitService(int defaultLimit, ClaimLimitRepository repository) {
        this.defaultLimit = defaultLimit;
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public void reload(int newDefaultLimit) {
        this.defaultLimit = newDefaultLimit;
    }

    @Override
    public int getLimit(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return repository.getLimit(playerId).orElse(defaultLimit);
    }

    @Override
    public void setLimit(UUID playerId, int limit) {
        Objects.requireNonNull(playerId, "playerId");
        if (limit < 1) throw new IllegalArgumentException("limit must be >= 1");
        repository.setLimit(playerId, limit);
    }

    @Override
    public void addChunks(UUID playerId, int chunks) {
        Objects.requireNonNull(playerId, "playerId");
        if (chunks < 1) throw new IllegalArgumentException("chunks must be >= 1");
        repository.updateLimit(playerId, defaultLimit, current -> current + chunks);
    }

    @Override
    public void removeChunks(UUID playerId, int chunks) {
        Objects.requireNonNull(playerId, "playerId");
        if (chunks < 1) throw new IllegalArgumentException("chunks must be >= 1");
        repository.updateLimit(playerId, defaultLimit, current -> Math.max(1, current - chunks));
    }

    public int overageChunks(int proposedTotalChunks, int allowedChunks) {
        return Math.max(0, proposedTotalChunks - allowedChunks);
    }

    public static double flatOverLimitCost(int overageChunks, double costPerChunk) {
        return Math.max(0, overageChunks) * Math.max(0.0, costPerChunk);
    }

    public static double exponentialOverLimitCost(int overageChunks, double baseCost, double multiplier) {
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
}
