package com.invisiblespiders.havenclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.api.limit.HavenClaimsLimitService;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LimitServiceTest {
    private static final int DEFAULT = 10;

    private LimitService serviceWith(UUID player, int stored) {
        ClaimLimitRepository repo = new ClaimLimitRepository() {
            @Override public OptionalInt getLimit(UUID id) {
                return id.equals(player) ? OptionalInt.of(stored) : OptionalInt.empty();
            }
            @Override public void setLimit(UUID id, int limit) {}
            @Override public void updateLimit(UUID id, int defaultLimit, java.util.function.IntUnaryOperator op) {}
        };
        return new LimitService(DEFAULT, repo);
    }

    private LimitService emptyService() {
        return new LimitService(DEFAULT, new ClaimLimitRepository() {
            private int stored = -1;
            private UUID storedId = null;
            @Override public OptionalInt getLimit(UUID id) {
                return storedId != null && storedId.equals(id) ? OptionalInt.of(stored) : OptionalInt.empty();
            }
            @Override public void setLimit(UUID id, int limit) { storedId = id; stored = limit; }
            @Override public void updateLimit(UUID id, int defaultLimit, java.util.function.IntUnaryOperator op) {
                int current = storedId != null && storedId.equals(id) ? stored : defaultLimit;
                setLimit(id, Math.max(1, op.applyAsInt(current)));
            }
        });
    }

    @Test
    void getLimitReturnsDatabaseValueWhenPresent() {
        UUID player = UUID.randomUUID();
        assertThat(serviceWith(player, 25).getLimit(player)).isEqualTo(25);
    }

    @Test
    void getLimitFallsBackToDefaultWhenNoRecord() {
        assertThat(serviceWith(UUID.randomUUID(), 25).getLimit(UUID.randomUUID())).isEqualTo(DEFAULT);
    }

    @Test
    void setLimitWritesToRepository() {
        LimitService service = emptyService();
        UUID player = UUID.randomUUID();
        service.setLimit(player, 20);
        assertThat(service.getLimit(player)).isEqualTo(20);
    }

    @Test
    void addChunksIncreasesLimit() {
        LimitService service = emptyService();
        UUID player = UUID.randomUUID();
        service.setLimit(player, 10);
        service.addChunks(player, 5);
        assertThat(service.getLimit(player)).isEqualTo(15);
    }

    @Test
    void removeChunksDecreasesLimitWithFloorAtOne() {
        LimitService service = emptyService();
        UUID player = UUID.randomUUID();
        service.setLimit(player, 3);
        service.removeChunks(player, 10);
        assertThat(service.getLimit(player)).isEqualTo(1);
    }

    @Test
    void overageChunksNeverReturnsNegative() {
        LimitService service = emptyService();
        assertThat(service.overageChunks(14, 10)).isEqualTo(4);
        assertThat(service.overageChunks(8, 10)).isZero();
    }

    @Test
    void flatOverLimitCostChargesPerChunk() {
        assertThat(LimitService.flatOverLimitCost(3, 250.0)).isEqualTo(750.0);
    }

    @Test
    void exponentialOverLimitCostScalesEachChunk() {
        assertThat(LimitService.exponentialOverLimitCost(3, 250.0, 1.25)).isEqualTo(953.125);
        assertThat(LimitService.exponentialOverLimitCost(0, 250.0, 1.25)).isZero();
    }

    @Test
    void reloadChangesDefaultLimitWhenNoDbRow() {
        LimitService service = emptyService();
        UUID player = UUID.randomUUID();
        assertThat(service.getLimit(player)).isEqualTo(DEFAULT);

        service.reload(25);

        assertThat(service.getLimit(player)).isEqualTo(25);
    }
}
