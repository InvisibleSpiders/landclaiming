package com.invisiblespiders.havenclaims.plugin.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.api.flag.FlagState;
import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimMember;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRole;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                .containsExactly(
                        "/claim flags " + claim.id(),
                        "/claim member list " + claim.id(),
                        "/claim denied " + claim.id(),
                        "/claim info " + claim.id(),
                        "/claim viewborder " + claim.id(),
                        "/claim delete " + claim.id());
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
                .containsExactly("Flags", "Members", "Denied Players", "Info", "View Border", "Delete Claim");
    }

    @Test
    void usesConfiguredActionLabels() {
        ClaimMenuService service = new ClaimMenuService(new MessageService(Map.of(
                "claim.menu.action-labels.flags", "Configure Flags",
                "claim.menu.action-labels.members", "Manage Members",
                "claim.menu.action-labels.denied", "Denied List",
                "claim.menu.action-labels.info", "View Info",
                "claim.menu.action-labels.viewborder", "Show Border",
                "claim.menu.action-labels.delete", "Abandon Claim"
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
                .containsExactly(
                        "Configure Flags",
                        "Manage Members",
                        "Denied List",
                        "View Info",
                        "Show Border",
                        "Abandon Claim");
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
                .containsExactly("Flags", "Members", "Denied Players", "Info", "View Border", "Delete Claim");
    }

    @Test
    void buildsDashboardForOwnedClaims() {
        ClaimMenuService service = new ClaimMenuService(messages());
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim home = playerClaim("Home", ownerId, worldId, 0);
        Claim mine = playerClaim("Mine", ownerId, worldId, 1);

        ClaimDashboard dashboard = service.buildDashboard(List.of(mine, home), Optional.of(home));

        assertThat(dashboard.claims()).extracting(ClaimDashboardRow::claimName)
                .containsExactly("Home", "Mine");
        assertThat(dashboard.claims()).extracting(ClaimDashboardRow::manageCommand)
                .containsExactly("/claim menu " + home.id(), "/claim menu " + mine.id());
        assertThat(dashboard.claims()).extracting(ClaimDashboardRow::currentClaim)
                .containsExactly(true, false);
        assertThat(dashboard.actions()).extracting(ClaimMenuAction::command)
                .containsExactly("/claim create", "/claim cost");
    }

    private static Claim playerClaim(String name, UUID ownerId, UUID worldId, int chunkX) {
        return new Claim(
                UUID.randomUUID(),
                name,
                OwnerType.PLAYER,
                ownerId,
                worldId,
                Set.of(new ClaimChunk(worldId, chunkX, 0)),
                Map.of(),
                Set.of(),
                Instant.parse("2026-06-08T00:00:00Z"),
                Instant.parse("2026-06-08T00:00:00Z")
        );
    }

    private static MessageService messages() {
        return new MessageService(Map.ofEntries(
                Map.entry("claim.menu.action-labels.flags", "Flags"),
                Map.entry("claim.menu.action-labels.members", "Members"),
                Map.entry("claim.menu.action-labels.denied", "Denied Players"),
                Map.entry("claim.menu.action-labels.info", "Info"),
                Map.entry("claim.menu.action-labels.viewborder", "View Border"),
                Map.entry("claim.menu.action-labels.delete", "Delete Claim"),
                Map.entry("claim.dashboard.title", "My Claims"),
                Map.entry("claim.dashboard.action-labels.create", "Create Claim"),
                Map.entry("claim.dashboard.action-labels.cost", "Claim Cost")
        ));
    }
}
