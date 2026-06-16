package com.invisiblespiders.havenclaims.plugin.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.api.flag.FlagState;
import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimEntryGuardTest {
    @Test
    void deniesMovementIntoClaimWhenPlayerIsDenied() {
        UUID worldId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();
        ClaimChunk outside = new ClaimChunk(worldId, 0, 0);
        ClaimChunk inside = new ClaimChunk(worldId, 1, 0);
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.add(claim(inside, Set.of(deniedId)));
        ClaimEntryGuard guard = new ClaimEntryGuard(claimIndex);

        ClaimEntryDecision decision = guard.checkMove(deniedId, outside, inside, permission -> false);

        assertThat(decision.denied()).isTrue();
        assertThat(decision.fallbackChunk()).contains(outside);
        assertThat(decision.messageKey()).isEqualTo("claim.deny.entry-denied");
    }

    @Test
    void allowsDeniedPlayerToMoveOutsideClaim() {
        UUID worldId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();
        ClaimChunk inside = new ClaimChunk(worldId, 1, 0);
        ClaimChunk outside = new ClaimChunk(worldId, 0, 0);
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.add(claim(inside, Set.of(deniedId)));
        ClaimEntryGuard guard = new ClaimEntryGuard(claimIndex);

        ClaimEntryDecision decision = guard.checkMove(deniedId, inside, outside, permission -> false);

        assertThat(decision.denied()).isFalse();
    }

    @Test
    void bypassPermissionAllowsEntry() {
        UUID worldId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();
        ClaimChunk outside = new ClaimChunk(worldId, 0, 0);
        ClaimChunk inside = new ClaimChunk(worldId, 1, 0);
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.add(claim(inside, Set.of(deniedId)));
        ClaimEntryGuard guard = new ClaimEntryGuard(claimIndex);

        ClaimEntryDecision decision = guard.checkMove(
                deniedId,
                outside,
                inside,
                permission -> permission.equals("havenclaims.bypass.entry-deny")
        );

        assertThat(decision.denied()).isFalse();
    }

    @Test
    void nearestUnclaimedBorderChunkSkipsOtherClaims() {
        UUID worldId = UUID.randomUUID();
        ClaimChunk deniedChunk = new ClaimChunk(worldId, 0, 0);
        ClaimChunk alreadyClaimedBorder = new ClaimChunk(worldId, 1, 0);
        ClaimIndex claimIndex = new ClaimIndex();
        Claim deniedClaim = claim(deniedChunk, Set.of(UUID.randomUUID()));
        claimIndex.add(deniedClaim);
        claimIndex.add(claim(alreadyClaimedBorder, Set.of()));
        ClaimEntryGuard guard = new ClaimEntryGuard(claimIndex);

        ClaimChunk fallback = guard.nearestUnclaimedBorderChunk(deniedChunk, deniedClaim);

        assertThat(fallback).isNotEqualTo(alreadyClaimedBorder);
        assertThat(claimIndex.findAt(fallback)).isEmpty();
    }

    @Test
    void nearestUnclaimedBorderChunkExpandsPastClaimedImmediateNeighbors() {
        UUID worldId = UUID.randomUUID();
        ClaimChunk deniedChunk = new ClaimChunk(worldId, 0, 0);
        ClaimIndex claimIndex = new ClaimIndex();
        Claim deniedClaim = claim(deniedChunk, Set.of(UUID.randomUUID()));
        claimIndex.add(deniedClaim);
        claimIndex.add(claim(new ClaimChunk(worldId, 1, 0), Set.of()));
        claimIndex.add(claim(new ClaimChunk(worldId, -1, 0), Set.of()));
        claimIndex.add(claim(new ClaimChunk(worldId, 0, 1), Set.of()));
        claimIndex.add(claim(new ClaimChunk(worldId, 0, -1), Set.of()));
        ClaimEntryGuard guard = new ClaimEntryGuard(claimIndex);

        ClaimChunk fallback = guard.nearestUnclaimedBorderChunk(deniedChunk, deniedClaim);

        assertThat(claimIndex.findAt(fallback)).isEmpty();
        assertThat(fallback).isNotIn(
                new ClaimChunk(worldId, 1, 0),
                new ClaimChunk(worldId, -1, 0),
                new ClaimChunk(worldId, 0, 1),
                new ClaimChunk(worldId, 0, -1)
        );
    }

    private static Claim claim(ClaimChunk chunk, Set<UUID> deniedPlayers) {
        Instant now = Instant.parse("2026-06-09T00:00:00Z");
        // Convert single chunk to its block region
        ClaimRegion region = new ClaimRegion(
                chunk.worldId(),
                chunk.chunkX() * 16, chunk.chunkZ() * 16,
                chunk.chunkX() * 16 + 15, chunk.chunkZ() * 16 + 15
        );
        return new Claim(
                UUID.randomUUID(),
                "Home",
                OwnerType.PLAYER,
                UUID.randomUUID(),
                region,
                Map.of("build", FlagState.OFF),
                Set.of(),
                deniedPlayers,
                now,
                now
        );
    }
}
