package com.invisiblespiders.havenclaims.plugin.upgrade;

import com.invisiblespiders.havenclaims.plugin.limit.LimitService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeContext;
import dev.invisiblespiders.haven.api.upgrade.UpgradeEffect;
import java.util.Objects;

public final class ClaimLimitEffect implements UpgradeEffect {
    public static final String TYPE = "claim-limit";

    private final LimitService limitService;
    private final int blocks;

    public ClaimLimitEffect(LimitService limitService, int blocks) {
        this.limitService = Objects.requireNonNull(limitService, "limitService");
        if (blocks < 1) {
            throw new IllegalArgumentException("blocks must be >= 1");
        }
        this.blocks = blocks;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void apply(UpgradeContext context) {
        limitService.addBlocks(context.targetPlayerId(), blocks);
    }

    @Override
    public void rollback(UpgradeContext context) {
        limitService.removeBlocks(context.targetPlayerId(), blocks);
    }
}
