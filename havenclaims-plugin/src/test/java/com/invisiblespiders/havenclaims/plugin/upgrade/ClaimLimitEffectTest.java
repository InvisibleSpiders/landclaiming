package com.invisiblespiders.havenclaims.plugin.upgrade;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.plugin.limit.ClaimLimitRepository;
import com.invisiblespiders.havenclaims.plugin.limit.LimitService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeContext;
import dev.invisiblespiders.haven.api.upgrade.UpgradeScope;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ClaimLimitEffectTest {
    @Test
    void claimLimitEffectAddsAndRollsBackBlocks() {
        UUID playerId = UUID.randomUUID();
        LimitService limitService = new LimitService(10, new InMemoryClaimLimitRepository());
        ClaimLimitEffect effect = new ClaimLimitEffect(limitService, 1280);

        effect.apply(contextFor(playerId));

        assertThat(limitService.getBlockLimit(playerId)).isEqualTo(1290);

        effect.rollback(contextFor(playerId));

        assertThat(limitService.getBlockLimit(playerId)).isEqualTo(10);
    }

    private static UpgradeContext contextFor(UUID playerId) {
        return new UpgradeContext(null, playerId, "havenclaims:claim-limit", 1, UpgradeScope.PLAYER, Map.of());
    }

    private static final class InMemoryClaimLimitRepository implements ClaimLimitRepository {
        private UUID storedPlayerId;
        private final AtomicInteger storedLimit = new AtomicInteger(-1);

        @Override
        public OptionalInt getLimit(UUID playerId) {
            if (playerId.equals(storedPlayerId)) {
                return OptionalInt.of(storedLimit.get());
            }
            return OptionalInt.empty();
        }

        @Override
        public void setLimit(UUID playerId, int limit) {
            storedPlayerId = playerId;
            storedLimit.set(limit);
        }

        @Override
        public void updateLimit(UUID playerId, int defaultLimit, java.util.function.IntUnaryOperator operator) {
            int current = playerId.equals(storedPlayerId) ? storedLimit.get() : defaultLimit;
            setLimit(playerId, Math.max(1, operator.applyAsInt(current)));
        }
    }
}
