package com.invisiblespiders.havenclaims.plugin.listener;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.api.protection.ClaimProtectionResult;
import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import com.invisiblespiders.havenclaims.plugin.flag.FlagRegistry;
import com.invisiblespiders.havenclaims.plugin.protection.ProtectionService;
import java.time.Instant;
import java.util.List;
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

    @Test
    void blocksPistonWhenMovedBlockDestinationWouldEnterProtectedClaim() {
        UUID worldId = UUID.randomUUID();
        ClaimChunk source = new ClaimChunk(worldId, 0, 0);
        ClaimChunk destination = new ClaimChunk(worldId, 1, 0);
        ProtectionListener listener = listener(Map.of(destination, claim(destination, Map.of("piston_protection", true))));

        boolean blocked = listener.pistonTouchesProtectedClaim(List.of(source), List.of(destination));

        assertThat(blocked).isTrue();
    }

    @Test
    void allowsPistonMovementIntoClaimWhenPistonProtectionIsDisabled() {
        UUID worldId = UUID.randomUUID();
        ClaimChunk source = new ClaimChunk(worldId, 0, 0);
        ClaimChunk destination = new ClaimChunk(worldId, 1, 0);
        ProtectionListener listener = listener(Map.of(destination, claim(destination, Map.of("piston_protection", false))));

        boolean blocked = listener.pistonTouchesProtectedClaim(List.of(source), List.of(destination));

        assertThat(blocked).isFalse();
    }

    @Test
    void blocksPistonHeadEnteringProtectedClaimEvenWithoutMovedBlocks() {
        UUID worldId = UUID.randomUUID();
        ClaimChunk pistonHeadDestination = new ClaimChunk(worldId, 1, 0);
        ProtectionListener listener = listener(Map.of(
                pistonHeadDestination,
                claim(pistonHeadDestination, Map.of("piston_protection", true))
        ));

        boolean blocked = listener.pistonTouchesProtectedClaim(List.of(), List.of(pistonHeadDestination));

        assertThat(blocked).isTrue();
    }

    @Test
    void blocksFluidFlowFromOutsideIntoProtectedClaim() {
        UUID worldId = UUID.randomUUID();
        ClaimChunk outside = new ClaimChunk(worldId, 0, 0);
        ClaimChunk inside = new ClaimChunk(worldId, 1, 0);
        ProtectionListener listener = listener(Map.of(inside, claim(inside, Map.of("fluid_flow", false))));

        boolean blocked = listener.isDeniedEnteringClaim(outside, inside, "fluid_flow");

        assertThat(blocked).isTrue();
    }

    @Test
    void allowsFluidFlowWithinSameClaimEvenWhenExternalFlowIsBlocked() {
        UUID worldId = UUID.randomUUID();
        ClaimChunk first = new ClaimChunk(worldId, 1, 0);
        ClaimChunk second = new ClaimChunk(worldId, 1, 1);
        Claim claim = claim(first, Map.of("fluid_flow", false), Set.of(first, second));
        ProtectionListener listener = listener(Map.of(first, claim, second, claim));

        boolean blocked = listener.isDeniedEnteringClaim(first, second, "fluid_flow");

        assertThat(blocked).isFalse();
    }

    private static ProtectionListener listener(Map<ClaimChunk, Claim> claims) {
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.load(claims.values());
        return new ProtectionListener(
                new ProtectionService(FlagRegistry.createDefault()),
                claimIndex
        );
    }

    private static Claim claim(ClaimChunk chunk, Map<String, Boolean> flags) {
        return claim(chunk, flags, Set.of(chunk));
    }

    private static Claim claim(ClaimChunk chunk, Map<String, Boolean> flags, Set<ClaimChunk> chunks) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new Claim(
                UUID.randomUUID(),
                "Spawn",
                OwnerType.PLAYER,
                UUID.randomUUID(),
                chunk.worldId(),
                chunks,
                flags,
                now,
                now
        );
    }
}
