package com.invisiblespiders.havenclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.api.flag.FlagState;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimIndexTest {
    @Test
    void findsClaimByChunkAfterAdd() {
        UUID worldId = UUID.randomUUID();
        // chunk (4,-2) covers blocks [64,-32] to [79,-17]
        ClaimRegion region = new ClaimRegion(worldId, 64, -32, 79, -17);
        ClaimChunk chunk = new ClaimChunk(worldId, 4, -2);
        Claim claim = claim(region);
        ClaimIndex index = new ClaimIndex();

        index.add(claim);

        assertThat(index.findAt(chunk)).contains(claim);
        assertThat(index.findAll()).containsExactly(claim);
    }

    @Test
    void loadReplacesExistingClaims() {
        UUID oldWorldId = UUID.randomUUID();
        UUID newWorldId = UUID.randomUUID();
        // chunk (0,0) covers blocks [0,0] to [15,15]
        Claim oldClaim = claim(new ClaimRegion(oldWorldId, 0, 0, 15, 15));
        // chunk (1,1) covers blocks [16,16] to [31,31]
        Claim newClaim = claim(new ClaimRegion(newWorldId, 16, 16, 31, 31));
        ClaimIndex index = new ClaimIndex();

        index.add(oldClaim);
        index.load(java.util.Set.of(newClaim));

        assertThat(index.findAt(new ClaimChunk(oldWorldId, 0, 0))).isEmpty();
        assertThat(index.findAt(new ClaimChunk(newWorldId, 1, 1))).contains(newClaim);
        assertThat(index.findAll()).containsExactly(newClaim);
    }

    private static Claim claim(ClaimRegion region) {
        Instant now = Instant.parse("2026-06-07T00:00:00Z");
        return new Claim(
                UUID.randomUUID(),
                "Test",
                OwnerType.PLAYER,
                UUID.randomUUID(),
                region,
                Map.of("build", FlagState.OFF),
                now,
                now
        );
    }
}
