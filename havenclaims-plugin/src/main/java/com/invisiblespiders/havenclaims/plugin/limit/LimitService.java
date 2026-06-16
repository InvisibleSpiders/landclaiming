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
    public int getBlockLimit(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return repository.getLimit(playerId).orElse(defaultLimit);
    }

    @Override
    public void setBlockLimit(UUID playerId, int limit) {
        Objects.requireNonNull(playerId, "playerId");
        if (limit < 1) throw new IllegalArgumentException("limit must be >= 1");
        repository.setLimit(playerId, limit);
    }

    @Override
    public void addBlocks(UUID playerId, int blocks) {
        Objects.requireNonNull(playerId, "playerId");
        if (blocks < 1) throw new IllegalArgumentException("blocks must be >= 1");
        repository.updateLimit(playerId, defaultLimit, current -> current + blocks);
    }

    @Override
    public void removeBlocks(UUID playerId, int blocks) {
        Objects.requireNonNull(playerId, "playerId");
        if (blocks < 1) throw new IllegalArgumentException("blocks must be >= 1");
        repository.updateLimit(playerId, defaultLimit, current -> Math.max(1, current - blocks));
    }

    public int overageBlocks(int proposedTotalBlocks, int allowedBlocks) {
        return Math.max(0, proposedTotalBlocks - allowedBlocks);
    }

    public static double flatOverLimitCost(int overageBlocks, double costPerBlock) {
        return Math.max(0, overageBlocks) * Math.max(0.0, costPerBlock);
    }
}
