package com.nick.landclaims.plugin.listener;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.api.protection.ClaimProtectionResult;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import com.nick.landclaims.plugin.protection.ProtectionService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProtectionListenerTest {
    @Test
    void allowsWhenChunkHasNoClaim() {
        ProtectionListener listener = listener(Map.of());

        Optional<ClaimProtectionResult> result = listener.checkProtection(
                new ClaimChunk(UUID.randomUUID(), 0, 0),
                UUID.randomUUID(),
                permission -> false,
                "break"
        );

        assertThat(result).isEmpty();
    }

    @Test
    void allowsWhenPlayerHasGlobalProtectionBypass() {
        ClaimChunk chunk = new ClaimChunk(UUID.randomUUID(), 1, 2);
        ProtectionListener listener = listener(Map.of(chunk, claim(chunk, Map.of("build", false))));

        Optional<ClaimProtectionResult> result = listener.checkProtection(
                chunk,
                UUID.randomUUID(),
                permission -> permission.equals("landclaims.bypass.protection"),
                "build"
        );

        assertThat(result).contains(ClaimProtectionResult.ALLOW);
    }

    @Test
    void allowsWhenPlayerHasFlagSpecificProtectionBypass() {
        ClaimChunk chunk = new ClaimChunk(UUID.randomUUID(), 1, 2);
        ProtectionListener listener = listener(Map.of(chunk, claim(chunk, Map.of("interact", false))));

        Optional<ClaimProtectionResult> result = listener.checkProtection(
                chunk,
                UUID.randomUUID(),
                permission -> permission.equals("landclaims.bypass.protection.interact"),
                "interact"
        );

        assertThat(result).contains(ClaimProtectionResult.ALLOW);
    }

    @Test
    void delegatesClaimFlagCheckWhenClaimExistsAndPlayerDoesNotBypass() {
        ClaimChunk chunk = new ClaimChunk(UUID.randomUUID(), 1, 2);
        ProtectionListener listener = listener(Map.of(chunk, claim(chunk, Map.of("break", false))));

        Optional<ClaimProtectionResult> result = listener.checkProtection(
                chunk,
                UUID.randomUUID(),
                permission -> false,
                "break"
        );

        assertThat(result).contains(ClaimProtectionResult.DENY_WITH_MESSAGE);
    }

    private static ProtectionListener listener(Map<ClaimChunk, Claim> claims) {
        return new ProtectionListener(
                new ProtectionService(FlagRegistry.createDefault()),
                new HashMap<>(claims)
        );
    }

    private static Claim claim(ClaimChunk chunk, Map<String, Boolean> flags) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new Claim(
                UUID.randomUUID(),
                "Spawn",
                OwnerType.PLAYER,
                UUID.randomUUID(),
                chunk.worldId(),
                Set.of(chunk),
                flags,
                now,
                now
        );
    }
}
