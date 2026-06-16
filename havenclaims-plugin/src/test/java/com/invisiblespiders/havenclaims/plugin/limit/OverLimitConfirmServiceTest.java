package com.invisiblespiders.havenclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OverLimitConfirmServiceTest {
    private final UUID player = UUID.randomUUID();
    private final ClaimRegion region = new ClaimRegion(UUID.randomUUID(), 0, 0, 15, 15);

    @Test
    void storePendingAndRetrieve() {
        OverLimitConfirmService service = new OverLimitConfirmService(60);
        service.store(player, region, "MyBase", 15.00);
        assertThat(service.getPending(player)).isPresent();
    }

    @Test
    void expiredPurchaseReturnsEmpty() {
        OverLimitConfirmService service = new OverLimitConfirmService(0);
        // Store with explicit past expiry
        service.store(player, region, "MyBase", 15.00, Instant.now().minusSeconds(1));
        assertThat(service.getPending(player)).isEmpty();
    }

    @Test
    void consumeRemovesPending() {
        OverLimitConfirmService service = new OverLimitConfirmService(60);
        service.store(player, region, "MyBase", 15.00);
        assertThat(service.consume(player)).isPresent();
        assertThat(service.getPending(player)).isEmpty();
    }

    @Test
    void clearRemovesPending() {
        OverLimitConfirmService service = new OverLimitConfirmService(60);
        service.store(player, region, "MyBase", 15.00);
        service.clear(player);
        assertThat(service.getPending(player)).isEmpty();
    }

    @Test
    void consumeExpiredReturnsEmpty() {
        OverLimitConfirmService service = new OverLimitConfirmService(0);
        service.store(player, region, "MyBase", 15.00, Instant.now().minusSeconds(1));
        assertThat(service.consume(player)).isEmpty();
    }

    @Test
    void getPendingFieldsAreCorrect() {
        OverLimitConfirmService service = new OverLimitConfirmService(60);
        service.store(player, region, "TestClaim", 42.50);
        PendingOverLimitPurchase purchase = service.getPending(player).orElseThrow();
        assertThat(purchase.region()).isEqualTo(region);
        assertThat(purchase.claimName()).isEqualTo("TestClaim");
        assertThat(purchase.cost()).isEqualTo(42.50);
        assertThat(purchase.isExpired()).isFalse();
    }
}
