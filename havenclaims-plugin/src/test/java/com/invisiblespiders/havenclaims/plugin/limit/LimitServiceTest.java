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
    void getBlockLimitReturnsDatabaseValueWhenPresent() {
        UUID player = UUID.randomUUID();
        assertThat(serviceWith(player, 25).getBlockLimit(player)).isEqualTo(25);
    }

    @Test
    void getBlockLimitFallsBackToDefaultWhenNoRecord() {
        assertThat(serviceWith(UUID.randomUUID(), 25).getBlockLimit(UUID.randomUUID())).isEqualTo(DEFAULT);
    }

    @Test
    void setBlockLimitWritesToRepository() {
        LimitService service = emptyService();
        UUID player = UUID.randomUUID();
        service.setBlockLimit(player, 20);
        assertThat(service.getBlockLimit(player)).isEqualTo(20);
    }

    @Test
    void addBlocksIncreasesLimit() {
        LimitService service = emptyService();
        UUID player = UUID.randomUUID();
        service.setBlockLimit(player, 10);
        service.addBlocks(player, 5);
        assertThat(service.getBlockLimit(player)).isEqualTo(15);
    }

    @Test
    void removeBlocksDecreasesLimitWithFloorAtOne() {
        LimitService service = emptyService();
        UUID player = UUID.randomUUID();
        service.setBlockLimit(player, 3);
        service.removeBlocks(player, 10);
        assertThat(service.getBlockLimit(player)).isEqualTo(1);
    }

    @Test
    void overageBlocksNeverReturnsNegative() {
        LimitService service = emptyService();
        assertThat(service.overageBlocks(14, 10)).isEqualTo(4);
        assertThat(service.overageBlocks(8, 10)).isZero();
    }

    @Test
    void flatOverLimitCostChargesPerBlock() {
        assertThat(LimitService.flatOverLimitCost(3, 250.0)).isEqualTo(750.0);
    }

    @Test
    void reloadChangesDefaultLimitWhenNoDbRow() {
        LimitService service = emptyService();
        UUID player = UUID.randomUUID();
        assertThat(service.getBlockLimit(player)).isEqualTo(DEFAULT);

        service.reload(25);

        assertThat(service.getBlockLimit(player)).isEqualTo(25);
    }
}
