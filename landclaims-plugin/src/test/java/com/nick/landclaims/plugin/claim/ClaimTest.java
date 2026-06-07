package com.nick.landclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.api.claim.ClaimChunkView;
import com.nick.landclaims.api.claim.ClaimView;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimTest {
    @Test
    void exposesClaimViewDataFromPluginDomainModel() {
        UUID claimId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim claim = new Claim(
                claimId,
                "Spawn",
                OwnerType.PLAYER,
                ownerId,
                worldId,
                Set.of(new ClaimChunk(worldId, 4, -2)),
                Map.of("build", true),
                Instant.parse("2026-06-07T00:00:00Z"),
                Instant.parse("2026-06-07T00:05:00Z")
        );

        ClaimView view = claim;

        assertThat(view.id()).isEqualTo(claimId);
        assertThat(view.name()).isEqualTo("Spawn");
        assertThat(view.ownerType()).isEqualTo("PLAYER");
        assertThat(view.ownerUuid()).isEqualTo(ownerId);
        assertThat(view.worldId()).isEqualTo(worldId);
        assertThat(view.chunks()).containsExactly(new ClaimChunkView(worldId, 4, -2));
        assertThat(view.flags()).containsEntry("build", true);
    }
}
