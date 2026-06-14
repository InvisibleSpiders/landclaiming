package com.nick.landclaims.plugin.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.api.flag.FlagState;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimMember;
import com.nick.landclaims.plugin.claim.ClaimRole;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.message.MessageService;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimMenuServiceTest {
    @Test
    void buildsMenuSummaryForPlayerClaim() {
        ClaimMenuService service = new ClaimMenuService(messages());
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
                Map.of("build", FlagState.ALL, "container_access", FlagState.OFF),
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
        ClaimMenuService service = new ClaimMenuService(messages());
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

    @Test
    void usesConfiguredActionLabels() {
        ClaimMenuService service = new ClaimMenuService(new MessageService(Map.of(
                "claim.menu.action-labels.flags", "Configure Flags",
                "claim.menu.action-labels.members", "Manage Members",
                "claim.menu.action-labels.info", "View Info"
        )));
        UUID worldId = UUID.randomUUID();
        Claim claim = new Claim(
                UUID.randomUUID(),
                "Spawn",
                OwnerType.PLAYER,
                UUID.randomUUID(),
                worldId,
                Set.of(new ClaimChunk(worldId, 0, 0)),
                Map.of(),
                Set.of(),
                Instant.parse("2026-06-08T00:00:00Z"),
                Instant.parse("2026-06-08T00:00:00Z")
        );

        ClaimMenu menu = service.buildMenu(claim, UUID.randomUUID());

        assertThat(menu.actions()).extracting(ClaimMenuAction::label)
                .containsExactly("Configure Flags", "Manage Members", "View Info");
    }

    @Test
    void usesDefaultActionLabelsWhenMessagesAreMissing() {
        ClaimMenuService service = new ClaimMenuService(new MessageService(Map.of()));
        UUID worldId = UUID.randomUUID();
        Claim claim = new Claim(
                UUID.randomUUID(),
                "Spawn",
                OwnerType.PLAYER,
                UUID.randomUUID(),
                worldId,
                Set.of(new ClaimChunk(worldId, 0, 0)),
                Map.of(),
                Set.of(),
                Instant.parse("2026-06-08T00:00:00Z"),
                Instant.parse("2026-06-08T00:00:00Z")
        );

        ClaimMenu menu = service.buildMenu(claim, UUID.randomUUID());

        assertThat(menu.actions()).extracting(ClaimMenuAction::label)
                .containsExactly("Flags", "Members", "Info");
    }

    private static MessageService messages() {
        return new MessageService(Map.of(
                "claim.menu.action-labels.flags", "Flags",
                "claim.menu.action-labels.members", "Members",
                "claim.menu.action-labels.info", "Info"
        ));
    }
}
