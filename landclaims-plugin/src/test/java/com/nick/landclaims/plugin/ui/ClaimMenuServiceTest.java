package com.nick.landclaims.plugin.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimMember;
import com.nick.landclaims.plugin.claim.ClaimRole;
import com.nick.landclaims.plugin.claim.OwnerType;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimMenuServiceTest {
    @Test
    void buildsMenuSummaryForPlayerClaim() {
        ClaimMenuService service = new ClaimMenuService();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim claim = new Claim(
                UUID.randomUUID(),
                "Haven Home",
                OwnerType.PLAYER,
                ownerId,
                worldId,
                Set.of(
                        new ClaimChunk(worldId, 0, 0),
                        new ClaimChunk(worldId, 1, 0)
                ),
                Map.of("build", true, "container_access", false),
                Set.of(new ClaimMember(UUID.randomUUID(), ClaimRole.MANAGER)),
                Instant.parse("2026-06-08T00:00:00Z"),
                Instant.parse("2026-06-08T00:00:00Z")
        );

        ClaimMenu menu = service.buildMenu(claim, ownerId);

        assertThat(menu.title()).isEqualTo("Haven Home");
        assertThat(menu.ownerType()).isEqualTo("PLAYER");
        assertThat(menu.chunkCount()).isEqualTo(2);
        assertThat(menu.memberCount()).isEqualTo(1);
        assertThat(menu.flagCount()).isEqualTo(2);
        assertThat(menu.viewerOwnsClaim()).isTrue();
        assertThat(menu.adminClaim()).isFalse();
        assertThat(menu.actions()).extracting(ClaimMenuAction::command)
                .containsExactly("/claim flags", "/claim member list", "/claim info");
    }

    @Test
    void marksAdminClaimsAndNonOwnerViewers() {
        ClaimMenuService service = new ClaimMenuService();
        UUID worldId = UUID.randomUUID();
        Claim claim = new Claim(
                UUID.randomUUID(),
                "Spawn",
                OwnerType.ADMIN,
                UUID.randomUUID(),
                worldId,
                Set.of(new ClaimChunk(worldId, 0, 0)),
                Map.of(),
                Set.of(),
                Instant.parse("2026-06-08T00:00:00Z"),
                Instant.parse("2026-06-08T00:00:00Z")
        );

        ClaimMenu menu = service.buildMenu(claim, UUID.randomUUID());

        assertThat(menu.adminClaim()).isTrue();
        assertThat(menu.viewerOwnsClaim()).isFalse();
        assertThat(menu.actions()).extracting(ClaimMenuAction::label)
                .containsExactly("Flags", "Members", "Info");
    }
}
