package com.invisiblespiders.havenclaims.plugin.accrual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.invisiblespiders.havenclaims.plugin.limit.LimitService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BlockAccrualServiceTest {
    private final LimitService limitService = mock(LimitService.class);
    private final AfkDetector afkDetector = mock(AfkDetector.class);

    @Test
    void grantsBlocksToActivePlayer() {
        UUID player = UUID.randomUUID();
        when(afkDetector.isAfk(player)).thenReturn(false);
        BlockAccrualService service = new BlockAccrualService(limitService, afkDetector, 10, 50000, "reduced", 0.5);
        service.accrueFor(player);
        verify(limitService).addBlocks(player, 10);
    }

    @Test
    void reducedAfkGrantsFlooredAmount() {
        UUID player = UUID.randomUUID();
        when(afkDetector.isAfk(player)).thenReturn(true);
        // 10 * 0.5 = 5.0, floor = 5
        BlockAccrualService service = new BlockAccrualService(limitService, afkDetector, 10, 50000, "reduced", 0.5);
        service.accrueFor(player);
        verify(limitService).addBlocks(player, 5);
    }

    @Test
    void zeroAfkGrantsNothing() {
        UUID player = UUID.randomUUID();
        when(afkDetector.isAfk(player)).thenReturn(true);
        BlockAccrualService service = new BlockAccrualService(limitService, afkDetector, 10, 50000, "zero", 0.0);
        service.accrueFor(player);
        verify(limitService, never()).addBlocks(any(), anyInt());
    }

    @Test
    void maxBlocksCapIsRespected() {
        UUID player = UUID.randomUUID();
        when(afkDetector.isAfk(player)).thenReturn(false);
        when(limitService.getBlockLimit(player)).thenReturn(49998);
        BlockAccrualService service = new BlockAccrualService(limitService, afkDetector, 10, 50000, "reduced", 0.5);
        service.accrueFor(player);
        // current=49998, grant=10, cap=50000 → only 2 more allowed
        verify(limitService).addBlocks(player, 2);
    }

    @Test
    void maxBlocksZeroMeansNoCap() {
        UUID player = UUID.randomUUID();
        when(afkDetector.isAfk(player)).thenReturn(false);
        BlockAccrualService service = new BlockAccrualService(limitService, afkDetector, 10, 0, "reduced", 0.5);
        service.accrueFor(player);
        verify(limitService).addBlocks(player, 10);
        verify(limitService, never()).getBlockLimit(any());
    }

    @Test
    void alreadyAtMaxGrantsNothing() {
        UUID player = UUID.randomUUID();
        when(afkDetector.isAfk(player)).thenReturn(false);
        when(limitService.getBlockLimit(player)).thenReturn(50000);
        BlockAccrualService service = new BlockAccrualService(limitService, afkDetector, 10, 50000, "reduced", 0.5);
        service.accrueFor(player);
        verify(limitService, never()).addBlocks(any(), anyInt());
    }
}
