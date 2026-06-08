package com.nick.landclaims.plugin.selection;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class DoubleCrouchClearService {
    private final int windowTicks;
    private final LongSupplier currentTickSupplier;
    private final Map<UUID, Long> lastCrouchTicks = new HashMap<>();

    public DoubleCrouchClearService(int windowTicks, LongSupplier currentTickSupplier) {
        if (windowTicks < 1) {
            throw new IllegalArgumentException("windowTicks must be at least 1");
        }
        this.windowTicks = windowTicks;
        this.currentTickSupplier = Objects.requireNonNull(currentTickSupplier, "currentTickSupplier");
    }

    public boolean recordCrouch(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        long currentTick = currentTickSupplier.getAsLong();
        Long previousTick = lastCrouchTicks.put(playerId, currentTick);
        return previousTick != null && currentTick - previousTick <= windowTicks;
    }
}
