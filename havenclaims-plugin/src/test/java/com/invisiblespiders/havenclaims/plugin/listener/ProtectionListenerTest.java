package com.invisiblespiders.havenclaims.plugin.listener;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.api.flag.FlagState;
import com.invisiblespiders.havenclaims.api.protection.ClaimProtectionResult;
import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
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
                0, 0,
                UUID.randomUUID(),
                permission -> false,
                "break"
        );

        assertThat(result).isEmpty();
    }

    @Test
    void allowsWhenPlayerHasGlobalProtectionBypass() {
        ClaimChunk chunk = new ClaimChunk(UUID.randomUUID(), 1, 2);
        ProtectionListener listener = listener(Map.of(chunk, claim(chunk, Map.of("build", FlagState.VISITORS))));

        Optional<ClaimProtectionResult> result = listener.checkProtection(
                chunk,
                chunk.chunkX() * 16, chunk.chunkZ() * 16,
                UUID.randomUUID(),
                permission -> permission.equals("havenclaims.bypass.protection"),
                "build"
        );

        assertThat(result).contains(ClaimProtectionResult.ALLOW);
    }

    @Test
    void allowsWhenPlayerHasFlagSpecificProtectionBypass() {
        ClaimChunk chunk = new ClaimChunk(UUID.randomUUID(), 1, 2);
        ProtectionListener listener = listener(Map.of(chunk, claim(chunk, Map.of("interact", FlagState.VISITORS))));

        Optional<ClaimProtectionResult> result = listener.checkProtection(
                chunk,
                chunk.chunkX() * 16, chunk.chunkZ() * 16,
                UUID.randomUUID(),
                permission -> permission.equals("havenclaims.bypass.protection.interact"),
                "interact"
        );

        assertThat(result).contains(ClaimProtectionResult.ALLOW);
    }

    @Test
    void delegatesClaimFlagCheckWhenClaimExistsAndPlayerDoesNotBypass() {
        ClaimChunk chunk = new ClaimChunk(UUID.randomUUID(), 1, 2);
        ProtectionListener listener = listener(Map.of(chunk, claim(chunk, Map.of("break", FlagState.VISITORS))));

        Optional<ClaimProtectionResult> result = listener.checkProtection(
                chunk,
                chunk.chunkX() * 16, chunk.chunkZ() * 16,
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
        ProtectionListener listener = listener(Map.of(destination, claim(destination, Map.of("piston_protection", FlagState.ALL))));

        boolean blocked = listener.pistonTouchesProtectedClaim(List.of(source), List.of(destination));

        assertThat(blocked).isTrue();
    }

    @Test
    void allowsPistonMovementIntoClaimWhenPistonProtectionIsDisabled() {
        UUID worldId = UUID.randomUUID();
        ClaimChunk source = new ClaimChunk(worldId, 0, 0);
        ClaimChunk destination = new ClaimChunk(worldId, 1, 0);
        ProtectionListener listener = listener(Map.of(destination, claim(destination, Map.of("piston_protection", FlagState.OFF))));

        boolean blocked = listener.pistonTouchesProtectedClaim(List.of(source), List.of(destination));

        assertThat(blocked).isFalse();
    }

    @Test
    void blocksPistonHeadEnteringProtectedClaimEvenWithoutMovedBlocks() {
        UUID worldId = UUID.randomUUID();
        ClaimChunk pistonHeadDestination = new ClaimChunk(worldId, 1, 0);
        ProtectionListener listener = listener(Map.of(
                pistonHeadDestination,
                claim(pistonHeadDestination, Map.of("piston_protection", FlagState.ALL))
        ));

        boolean blocked = listener.pistonTouchesProtectedClaim(List.of(), List.of(pistonHeadDestination));

        assertThat(blocked).isTrue();
    }

    @Test
    void blocksFluidFlowFromOutsideIntoProtectedClaim() {
        UUID worldId = UUID.randomUUID();
        ClaimChunk outside = new ClaimChunk(worldId, 0, 0);
        ClaimChunk inside = new ClaimChunk(worldId, 1, 0);
        ProtectionListener listener = listener(Map.of(inside, claim(inside, Map.of("fluid_flow", FlagState.OFF))));

        boolean blocked = listener.isDeniedEnteringClaim(outside, inside, "fluid_flow");

        assertThat(blocked).isTrue();
    }

    @Test
    void allowsFluidFlowWithinSameClaimEvenWhenExternalFlowIsBlocked() {
        UUID worldId = UUID.randomUUID();
        ClaimChunk first = new ClaimChunk(worldId, 1, 0);
        ClaimChunk second = new ClaimChunk(worldId, 1, 1);
        Claim claim = claim(first, Map.of("fluid_flow", FlagState.OFF), Set.of(first, second));
        ProtectionListener listener = listener(Map.of(first, claim, second, claim));

        boolean blocked = listener.isDeniedEnteringClaim(first, second, "fluid_flow");

        assertThat(blocked).isFalse();
    }

    @Test
    void blockOutsideRegionInSameChunkIsNotProtected() {
        UUID worldId = UUID.randomUUID();
        // Claim covers only blocks 0-7 in X, chunk 0 covers 0-15
        ClaimRegion region = new ClaimRegion(worldId, 0, 0, 7, 15);
        Claim claim = claimWithRegion(region, Map.of("build", FlagState.VISITORS));
        ClaimChunk chunk = new ClaimChunk(worldId, 0, 0);
        ProtectionListener listener = listener(Map.of(chunk, claim));

        // Block at x=10 is in chunk 0 but outside the region (region only goes to x=7)
        Optional<ClaimProtectionResult> result = listener.checkProtection(
                chunk, 10, 5, UUID.randomUUID(), permission -> false, "build");

        assertThat(result).isEmpty();
    }

    @Test
    void blockOnExactBoundaryIsProtected() {
        UUID worldId = UUID.randomUUID();
        ClaimRegion region = new ClaimRegion(worldId, 0, 0, 7, 7);
        Claim claim = claimWithRegion(region, Map.of("build", FlagState.VISITORS));
        ClaimChunk chunk = new ClaimChunk(worldId, 0, 0);
        ProtectionListener listener = listener(Map.of(chunk, claim));

        // Block at x=7, z=7 is exactly on the boundary of the region
        Optional<ClaimProtectionResult> result = listener.checkProtection(
                chunk, 7, 7, UUID.randomUUID(), permission -> false, "build");

        assertThat(result).contains(ClaimProtectionResult.DENY_WITH_MESSAGE);
    }

    private static ProtectionListener listener(Map<ClaimChunk, Claim> claims) {
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.load(claims.values());
        return new ProtectionListener(
                new ProtectionService(FlagRegistry.createDefault()),
                claimIndex
        );
    }

    private static Claim claim(ClaimChunk chunk, Map<String, FlagState> flags) {
        return claim(chunk, flags, Set.of(chunk));
    }

    private static Claim claim(ClaimChunk chunk, Map<String, FlagState> flags, Set<ClaimChunk> chunks) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        UUID worldId = chunk.worldId();
        int minCX = chunks.stream().mapToInt(ClaimChunk::chunkX).min().orElse(chunk.chunkX());
        int minCZ = chunks.stream().mapToInt(ClaimChunk::chunkZ).min().orElse(chunk.chunkZ());
        int maxCX = chunks.stream().mapToInt(ClaimChunk::chunkX).max().orElse(chunk.chunkX());
        int maxCZ = chunks.stream().mapToInt(ClaimChunk::chunkZ).max().orElse(chunk.chunkZ());
        ClaimRegion region = new ClaimRegion(worldId, minCX * 16, minCZ * 16, maxCX * 16 + 15, maxCZ * 16 + 15);
        return claimWithRegion(region, flags);
    }

    private static Claim claimWithRegion(ClaimRegion region, Map<String, FlagState> flags) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new Claim(
                UUID.randomUUID(),
                "Spawn",
                OwnerType.PLAYER,
                UUID.randomUUID(),
                region,
                flags,
                Set.of(),
                Set.of(),
                now,
                now
        );
    }
}
