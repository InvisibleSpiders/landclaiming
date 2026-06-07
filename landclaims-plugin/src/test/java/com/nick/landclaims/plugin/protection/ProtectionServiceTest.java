package com.nick.landclaims.plugin.protection;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.api.protection.ClaimProtectionResult;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProtectionServiceTest {
    @Test
    void ownerAlwaysAllowedEvenWhenFlagIsFalse() {
        UUID ownerUuid = UUID.randomUUID();
        Claim claim = claim(ownerUuid, Map.of("build", false));
        ProtectionService service = new ProtectionService(FlagRegistry.createDefault());

        ClaimProtectionResult result = service.checkClaimFlag(claim, ownerUuid, "build");

        assertThat(result).isEqualTo(ClaimProtectionResult.ALLOW);
    }

    @Test
    void strangerDeniedWhenClaimFlagIsFalse() {
        Claim claim = claim(UUID.randomUUID(), Map.of("build", false));
        ProtectionService service = new ProtectionService(FlagRegistry.createDefault());

        ClaimProtectionResult result = service.checkClaimFlag(claim, UUID.randomUUID(), "build");

        assertThat(result).isEqualTo(ClaimProtectionResult.DENY_WITH_MESSAGE);
    }

    @Test
    void strangerAllowedWhenClaimFlagIsTrue() {
        Claim claim = claim(UUID.randomUUID(), Map.of("build", true));
        ProtectionService service = new ProtectionService(FlagRegistry.createDefault());

        ClaimProtectionResult result = service.checkClaimFlag(claim, UUID.randomUUID(), "build");

        assertThat(result).isEqualTo(ClaimProtectionResult.ALLOW);
    }

    @Test
    void missingClaimFlagFallsBackToRegistryDefault() {
        Claim claim = claim(UUID.randomUUID(), Map.of());
        ProtectionService service = new ProtectionService(FlagRegistry.createDefault());

        assertThat(service.checkClaimFlag(claim, UUID.randomUUID(), "piston_protection"))
                .isEqualTo(ClaimProtectionResult.ALLOW);
        assertThat(service.checkClaimFlag(claim, UUID.randomUUID(), "build"))
                .isEqualTo(ClaimProtectionResult.DENY_WITH_MESSAGE);
    }

    private static Claim claim(UUID ownerUuid, Map<String, Boolean> flags) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new Claim(
                UUID.randomUUID(),
                "Spawn",
                OwnerType.PLAYER,
                ownerUuid,
                UUID.randomUUID(),
                Set.of(),
                flags,
                now,
                now
        );
    }
}
