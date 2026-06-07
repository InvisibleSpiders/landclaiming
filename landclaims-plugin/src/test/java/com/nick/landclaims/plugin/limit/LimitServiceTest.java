package com.nick.landclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LimitServiceTest {
    @Test
    void resolveLimitReturnsDefaultWhenNoPermissionMatches() {
        LimitService service = new LimitService(10, Map.of(
                "landclaims.limit.member", 25,
                "landclaims.limit.vip", 50
        ));

        assertThat(service.resolveLimit(Set.of("other.permission"))).isEqualTo(10);
    }

    @Test
    void resolveLimitReturnsHighestMatchingConfiguredLimit() {
        LimitService service = new LimitService(10, Map.of(
                "landclaims.limit.member", 25,
                "landclaims.limit.vip", 50,
                "landclaims.limit.staff", 100
        ));

        assertThat(service.resolveLimit(Set.of(
                "landclaims.limit.member",
                "landclaims.limit.staff"
        ))).isEqualTo(100);
    }

    @Test
    void overageChunksNeverReturnsNegativeNumbers() {
        LimitService service = new LimitService(10, Map.of());

        assertThat(service.overageChunks(14, 10)).isEqualTo(4);
        assertThat(service.overageChunks(8, 10)).isZero();
    }
}
