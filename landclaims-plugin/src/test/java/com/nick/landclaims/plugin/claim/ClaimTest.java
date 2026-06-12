package com.nick.landclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nick.landclaims.api.claim.ClaimChunkView;
import com.nick.landclaims.api.claim.ClaimView;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
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

    @Test
    void defensiveCopiesMutableConstructorInputs() {
        UUID worldId = UUID.randomUUID();
        ClaimChunk originalChunk = new ClaimChunk(worldId, 4, -2);
        ClaimChunk addedChunk = new ClaimChunk(worldId, 5, -2);
        Set<ClaimChunk> claimChunks = new HashSet<>();
        claimChunks.add(originalChunk);
        Map<String, Boolean> flags = new HashMap<>();
        flags.put("build", true);

        Claim claim = new Claim(
                UUID.randomUUID(),
                "Spawn",
                OwnerType.PLAYER,
                UUID.randomUUID(),
                worldId,
                claimChunks,
                flags,
                Instant.parse("2026-06-07T00:00:00Z"),
                Instant.parse("2026-06-07T00:05:00Z")
        );

        claimChunks.add(addedChunk);
        flags.put("interact", false);

        assertThat(claim.claimChunks()).containsExactly(originalChunk);
        assertThat(claim.chunks()).containsExactly(new ClaimChunkView(worldId, 4, -2));
        assertThat(claim.flags()).containsExactly(Map.entry("build", true));
    }

    @Test
    void flagsViewCannotBeModified() {
        Claim claim = new Claim(
                UUID.randomUUID(),
                "Spawn",
                OwnerType.PLAYER,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Set.of(),
                Map.of("build", true),
                Instant.parse("2026-06-07T00:00:00Z"),
                Instant.parse("2026-06-07T00:05:00Z")
        );

        assertThatThrownBy(() -> claim.flags().put("interact", false))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void chunkViewsCannotBeModified() {
        UUID worldId = UUID.randomUUID();
        Claim claim = new Claim(
                UUID.randomUUID(),
                "Spawn",
                OwnerType.PLAYER,
                UUID.randomUUID(),
                worldId,
                Set.of(new ClaimChunk(worldId, 4, -2)),
                Map.of(),
                Instant.parse("2026-06-07T00:00:00Z"),
                Instant.parse("2026-06-07T00:05:00Z")
        );

        assertThatThrownBy(() -> claim.claimChunks().add(new ClaimChunk(worldId, 5, -2)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> claim.chunks().add(new ClaimChunkView(worldId, 5, -2)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
