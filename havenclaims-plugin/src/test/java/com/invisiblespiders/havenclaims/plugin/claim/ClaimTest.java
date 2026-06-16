package com.invisiblespiders.havenclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.invisiblespiders.havenclaims.api.claim.ClaimChunkView;
import com.invisiblespiders.havenclaims.api.claim.ClaimView;
import com.invisiblespiders.havenclaims.api.flag.FlagState;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimTest {
    private final UUID world = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final ClaimRegion region = new ClaimRegion(world, 0, 0, 15, 15);
    private final Instant now = Instant.now();

    @Test
    void worldIdDelegatesToRegion() {
        Claim claim = claim(region);
        assertThat(claim.worldId()).isEqualTo(world);
    }

    @Test
    void overlappingChunksComesFromRegion() {
        Claim claim = claim(region);
        assertThat(claim.overlappingChunks()).isEqualTo(region.overlappingChunks());
    }

    @Test
    void regionAccessorReturnsRegion() {
        Claim claim = claim(region);
        assertThat(claim.region()).isEqualTo(region);
    }

    @Test
    void exposesClaimViewDataFromPluginDomainModel() {
        UUID claimId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        // chunk (4,-2) covers blocks [64,-32] to [79,-17]
        ClaimRegion r = new ClaimRegion(worldId, 64, -32, 79, -17);
        Claim c = new Claim(
                claimId,
                "Spawn",
                OwnerType.PLAYER,
                ownerId,
                r,
                Map.of("build", FlagState.ALL),
                Instant.parse("2026-06-07T00:00:00Z"),
                Instant.parse("2026-06-07T00:05:00Z")
        );

        ClaimView view = c;

        assertThat(view.id()).isEqualTo(claimId);
        assertThat(view.name()).isEqualTo("Spawn");
        assertThat(view.ownerType()).isEqualTo("PLAYER");
        assertThat(view.ownerUuid()).isEqualTo(ownerId);
        assertThat(view.worldId()).isEqualTo(worldId);
        assertThat(view.chunks()).containsExactly(new ClaimChunkView(worldId, 4, -2));
        assertThat(view.flags()).containsEntry("build", FlagState.ALL);
        assertThat(view.region()).isEqualTo(r);
    }

    @Test
    void flagsViewCannotBeModified() {
        Claim c = claim(region);

        assertThatThrownBy(() -> c.flags().put("interact", FlagState.OFF))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void chunkViewsCannotBeModified() {
        Claim c = claim(region);

        assertThatThrownBy(() -> c.overlappingChunks().add(new ClaimChunk(world, 99, 99)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> c.chunks().add(new ClaimChunkView(world, 99, 99)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private Claim claim(ClaimRegion r) {
        return new Claim(UUID.randomUUID(), "Home", OwnerType.PLAYER, owner,
                r, Map.of(), now, now);
    }
}
