package com.invisiblespiders.havenclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimIndexTest {
    @Test
    void findsClaimByChunkAfterAdd() {
        UUID worldId = UUID.randomUUID();
        ClaimChunk chunk = new ClaimChunk(worldId, 4, -2);
        Claim claim = claim(worldId, Set.of(chunk));
        ClaimIndex index = new ClaimIndex();

        index.add(claim);

        assertThat(index.findAt(chunk)).contains(claim);
        assertThat(index.findAll()).containsExactly(claim);
    }

    @Test
    void loadReplacesExistingClaims() {
        UUID oldWorldId = UUID.randomUUID();
        UUID newWorldId = UUID.randomUUID();
        Claim oldClaim = claim(oldWorldId, Set.of(new ClaimChunk(oldWorldId, 0, 0)));
        Claim newClaim = claim(newWorldId, Set.of(new ClaimChunk(newWorldId, 1, 1)));
        ClaimIndex index = new ClaimIndex();

        index.add(oldClaim);
        index.load(Set.of(newClaim));

        assertThat(index.findAt(new ClaimChunk(oldWorldId, 0, 0))).isEmpty();
        assertThat(index.findAt(new ClaimChunk(newWorldId, 1, 1))).contains(newClaim);
        assertThat(index.findAll()).containsExactly(newClaim);
    }

    private static Claim claim(UUID worldId, Set<ClaimChunk> chunks) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new Claim(
                UUID.randomUUID(),
                "Test",
                OwnerType.PLAYER,
                UUID.randomUUID(),
                worldId,
                chunks,
                Map.of("build", false),
                now,
                now
        );
    }
}
