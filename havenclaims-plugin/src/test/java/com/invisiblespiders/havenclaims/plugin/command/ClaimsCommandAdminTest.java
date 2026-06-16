package com.invisiblespiders.havenclaims.plugin.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invisiblespiders.havenclaims.api.flag.FlagState;
import com.invisiblespiders.havenclaims.api.limit.HavenClaimsLimitService;
import com.invisiblespiders.havenclaims.plugin.admin.AdminClaimService;
import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimMember;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimMemberService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRole;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import com.invisiblespiders.havenclaims.plugin.flag.ClaimFlagService;
import com.invisiblespiders.havenclaims.plugin.flag.FlagRegistry;
import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import com.invisiblespiders.havenclaims.plugin.storage.ClaimRepository;
import com.invisiblespiders.havenclaims.plugin.tool.ClaimToolService;
import dev.invisiblespiders.haven.api.model.ReloadResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ClaimsCommandAdminTest {
    @Test
    void rootTabCompletionIncludesAdmin() {
        ClaimsCommand command = command(new AdminClaimService());

        assertThat(command.onTabComplete(mock(Player.class), mock(Command.class), "claim", new String[]{"a"}))
                .contains("admin");
    }

    @Test
    void adminTabCompletionIncludesManagementSubcommands() {
        ClaimsCommand command = command(new AdminClaimService());

        assertThat(command.onTabComplete(mock(Player.class), mock(Command.class), "claim", new String[]{"admin", ""}))
                .containsExactlyInAnyOrder("create", "list", "delete", "teleport", "userclaims", "limit", "reload", "browse");
    }

    @Test
    void adminUserclaimsTabCompletionIncludesManagementSubcommands() {
        ClaimsCommand command = command(new AdminClaimService());

        assertThat(command.onTabComplete(mock(Player.class), mock(Command.class), "claim", new String[]{"admin", "userclaims", ""}))
                .containsExactlyInAnyOrder("list", "view", "delete", "teleport", "transfer", "flag", "member");
    }

    @Test
    void adminUserclaimsFlagTabCompletionIncludesFlagSubcommands() {
        ClaimsCommand command = command(new AdminClaimService());

        assertThat(command.onTabComplete(mock(Player.class), mock(Command.class), "claim", new String[]{"admin", "userclaims", "flag", ""}))
                .containsExactlyInAnyOrder("list", "set", "cycle");
    }

    @Test
    void adminUserclaimsMemberTabCompletionIncludesMemberSubcommands() {
        ClaimsCommand command = command(new AdminClaimService());

        assertThat(command.onTabComplete(mock(Player.class), mock(Command.class), "claim", new String[]{"admin", "userclaims", "member", ""}))
                .containsExactlyInAnyOrder("list", "add", "remove");
    }

    @Test
    void adminListShowsSortedAdminClaimsWhenAllowed() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        UUID worldId = UUID.randomUUID();
        Claim beta = claim("Beta", worldId, 1);
        Claim alpha = claim("Alpha", worldId, 0);
        repository.claims.add(beta);
        repository.claims.add(alpha);
        AdminClaimService adminClaimService = new AdminClaimService(repository, claimIndex, FlagRegistry.createDefault(), 32);
        ClaimsCommand command = command(adminClaimService);
        Player player = mock(Player.class);
        when(player.hasPermission("havenclaims.admin.claim.list")).thenReturn(true);
        List<Component> messages = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            messages.add(invocation.getArgument(0));
            return null;
        }).when(player).sendMessage(any(Component.class));

        command.onCommand(player, mock(Command.class), "claim", new String[]{"admin", "list"});

        List<String> plainMessages = messages.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();
        assertThat(plainMessages).containsExactly(
                "Admin claims:",
                "- Alpha (1 chunks) " + alpha.id(),
                "- Beta (1 chunks) " + beta.id()
        );
    }

    @Test
    void adminUserclaimsListShowsPlayerClaimsWhenAllowed() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim home = playerClaim("Home", ownerId, worldId, 0);
        repository.claims.add(home);
        AdminClaimService adminClaimService = new AdminClaimService(repository, claimIndex, FlagRegistry.createDefault(), 32);
        ClaimsCommand command = command(adminClaimService);
        Player player = mock(Player.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);
        when(player.hasPermission("havenclaims.admin.userclaims.view")).thenReturn(true);
        when(player.getServer()).thenReturn(server);
        when(server.getOfflinePlayer(ownerId)).thenReturn(offlinePlayer);
        when(offlinePlayer.getUniqueId()).thenReturn(ownerId);
        when(offlinePlayer.getName()).thenReturn("Builder");
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{"admin", "userclaims", "list", ownerId.toString()});

        List<String> plainMessages = messages.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();
        assertThat(plainMessages).containsExactly(
                "Claims for Builder:",
                "- Home (1 chunks) " + home.id()
        );
    }

    @Test
    void adminUserclaimsDeleteRequiresDeletePermission() {
        ClaimsCommand command = command(new AdminClaimService());
        Player player = mock(Player.class);
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{"admin", "userclaims", "delete", UUID.randomUUID().toString()});

        assertThat(messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList())
                .containsExactly("You do not have permission to manage user claims.");
    }

    @Test
    void adminUserclaimsTransferRequiresTransferPermission() {
        ClaimsCommand command = command(new AdminClaimService());
        Player player = mock(Player.class);
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{
                "admin",
                "userclaims",
                "transfer",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString()
        });

        assertThat(messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList())
                .containsExactly("You do not have permission to manage user claims.");
    }

    @Test
    void adminUserclaimsTransferMovesClaimToOnlinePlayerWhenAllowed() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        UUID ownerId = UUID.randomUUID();
        UUID newOwnerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim home = playerClaim("Home", ownerId, worldId, 0);
        repository.claims.add(home);
        claimIndex.add(home);
        AdminClaimService adminClaimService = new AdminClaimService(repository, claimIndex, FlagRegistry.createDefault(), 32);
        ClaimsCommand command = command(adminClaimService);
        Player player = mock(Player.class);
        Player target = mock(Player.class);
        Server server = mock(Server.class);
        when(player.hasPermission("havenclaims.admin.userclaims.transfer")).thenReturn(true);
        when(player.getServer()).thenReturn(server);
        when(server.getPlayerExact("NewOwner")).thenReturn(target);
        when(target.getUniqueId()).thenReturn(newOwnerId);
        when(target.getName()).thenReturn("NewOwner");
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{
                "admin",
                "userclaims",
                "transfer",
                home.id().toString(),
                "NewOwner"
        });

        Claim transferred = repository.findClaimById(home.id()).orElseThrow();
        assertThat(transferred.ownerUuid()).isEqualTo(newOwnerId);
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).contains(transferred);
        assertThat(messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList())
                .containsExactly("Transferred Home to NewOwner.");
    }

    @Test
    void adminUserclaimsTransferRejectsUnknownPlayerNames() {
        ClaimsCommand command = command(new AdminClaimService());
        Player player = mock(Player.class);
        Server server = mock(Server.class);
        when(player.hasPermission("havenclaims.admin.userclaims.transfer")).thenReturn(true);
        when(player.getServer()).thenReturn(server);
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{
                "admin",
                "userclaims",
                "transfer",
                UUID.randomUUID().toString(),
                "NotOnline"
        });

        assertThat(messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList())
                .containsExactly("Player NotOnline is not online. Use a UUID to transfer offline players.");
    }

    @Test
    void adminUserclaimsFlagSetRequiresEditPermission() {
        ClaimsCommand command = command(new AdminClaimService());
        Player player = mock(Player.class);
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{
                "admin",
                "userclaims",
                "flag",
                "set",
                UUID.randomUUID().toString(),
                "build",
                "true"
        });

        assertThat(messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList())
                .containsExactly("You do not have permission to edit user claims.");
    }

    @Test
    void adminUserclaimsFlagSetUpdatesPlayerClaimWhenAllowed() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim home = playerClaim("Home", ownerId, worldId, 0);
        repository.claims.add(home);
        claimIndex.add(home);
        FlagRegistry flagRegistry = FlagRegistry.createDefault();
        AdminClaimService adminClaimService = new AdminClaimService(repository, claimIndex, flagRegistry, 32);
        ClaimFlagService claimFlagService = new ClaimFlagService(repository, claimIndex, flagRegistry);
        ClaimsCommand command = command(adminClaimService, claimFlagService);
        Player player = mock(Player.class);
        when(player.hasPermission("havenclaims.admin.userclaims.edit")).thenReturn(true);
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{
                "admin",
                "userclaims",
                "flag",
                "set",
                home.id().toString(),
                "build",
                "ALL"
        });

        Claim updated = repository.findClaimById(home.id()).orElseThrow();
        assertThat(updated.flags()).containsEntry("build", FlagState.ALL);
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).contains(updated);
        assertThat(messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList())
                .containsExactly("Set build to ALL for Home.");
    }

    @Test
    void adminUserclaimsFlagCycleUpdatesPlayerClaimWhenAllowed() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim home = playerClaim("Home", ownerId, worldId, 0, Map.of("build", FlagState.ALL));
        repository.claims.add(home);
        claimIndex.add(home);
        FlagRegistry flagRegistry = FlagRegistry.createDefault();
        AdminClaimService adminClaimService = new AdminClaimService(repository, claimIndex, flagRegistry, 32);
        ClaimFlagService claimFlagService = new ClaimFlagService(repository, claimIndex, flagRegistry);
        ClaimsCommand command = command(adminClaimService, claimFlagService);
        Player player = mock(Player.class);
        when(player.hasPermission("havenclaims.admin.userclaims.edit")).thenReturn(true);
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{
                "admin",
                "userclaims",
                "flag",
                "cycle",
                home.id().toString(),
                "build"
        });

        Claim updated = repository.findClaimById(home.id()).orElseThrow();
        assertThat(updated.flags()).containsEntry("build", FlagState.OFF);
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).contains(updated);
        assertThat(messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList())
                .containsExactly("Cycled build for Home.");
    }

    @Test
    void adminUserclaimsFlagListShowsClaimFlagsWhenAllowed() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim home = playerClaim("Home", ownerId, worldId, 0);
        repository.claims.add(home);
        claimIndex.add(home);
        FlagRegistry flagRegistry = FlagRegistry.createDefault();
        AdminClaimService adminClaimService = new AdminClaimService(repository, claimIndex, flagRegistry, 32);
        ClaimFlagService claimFlagService = new ClaimFlagService(repository, claimIndex, flagRegistry);
        ClaimsCommand command = command(adminClaimService, claimFlagService);
        Player player = mock(Player.class);
        when(player.hasPermission("havenclaims.admin.userclaims.edit")).thenReturn(true);
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{
                "admin",
                "userclaims",
                "flag",
                "list",
                home.id().toString()
        });

        List<String> plainMessages = messages.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();
        assertThat(plainMessages).first().isEqualTo("Flags for Home:");
        assertThat(plainMessages).anyMatch(message -> message.contains("build") && message.contains("VISITORS"));
    }

    @Test
    void adminUserclaimsMemberAddRequiresEditPermission() {
        ClaimsCommand command = command(new AdminClaimService());
        Player player = mock(Player.class);
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{
                "admin",
                "userclaims",
                "member",
                "add",
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "member"
        });

        assertThat(messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList())
                .containsExactly("You do not have permission to edit user claims.");
    }

    @Test
    void adminUserclaimsMemberAddUpdatesPlayerClaimWhenAllowed() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim home = playerClaim("Home", ownerId, worldId, 0);
        repository.claims.add(home);
        claimIndex.add(home);
        FlagRegistry flagRegistry = FlagRegistry.createDefault();
        AdminClaimService adminClaimService = new AdminClaimService(repository, claimIndex, flagRegistry, 32);
        ClaimMemberService claimMemberService = new ClaimMemberService(repository, claimIndex);
        ClaimsCommand command = command(adminClaimService, null, claimMemberService);
        Player player = mock(Player.class);
        when(player.hasPermission("havenclaims.admin.userclaims.edit")).thenReturn(true);
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{
                "admin",
                "userclaims",
                "member",
                "add",
                home.id().toString(),
                memberId.toString(),
                "manager"
        });

        Claim updated = repository.findClaimById(home.id()).orElseThrow();
        assertThat(updated.members()).containsExactly(new ClaimMember(memberId, ClaimRole.MANAGER));
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).contains(updated);
        assertThat(messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList())
                .containsExactly("Added " + memberId + " as manager to Home.");
    }

    @Test
    void adminUserclaimsMemberRemoveUpdatesPlayerClaimWhenAllowed() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim home = playerClaim("Home", ownerId, worldId, 0, Map.of(), Set.of(new ClaimMember(memberId, ClaimRole.MEMBER)));
        repository.claims.add(home);
        claimIndex.add(home);
        FlagRegistry flagRegistry = FlagRegistry.createDefault();
        AdminClaimService adminClaimService = new AdminClaimService(repository, claimIndex, flagRegistry, 32);
        ClaimMemberService claimMemberService = new ClaimMemberService(repository, claimIndex);
        ClaimsCommand command = command(adminClaimService, null, claimMemberService);
        Player player = mock(Player.class);
        when(player.hasPermission("havenclaims.admin.userclaims.edit")).thenReturn(true);
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{
                "admin",
                "userclaims",
                "member",
                "remove",
                home.id().toString(),
                memberId.toString()
        });

        Claim updated = repository.findClaimById(home.id()).orElseThrow();
        assertThat(updated.members()).isEmpty();
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).contains(updated);
        assertThat(messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList())
                .containsExactly("Removed " + memberId + " from Home.");
    }

    @Test
    void adminUserclaimsMemberListShowsClaimMembersWhenAllowed() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim home = playerClaim("Home", ownerId, worldId, 0, Map.of(), Set.of(new ClaimMember(memberId, ClaimRole.MANAGER)));
        repository.claims.add(home);
        claimIndex.add(home);
        FlagRegistry flagRegistry = FlagRegistry.createDefault();
        AdminClaimService adminClaimService = new AdminClaimService(repository, claimIndex, flagRegistry, 32);
        ClaimMemberService claimMemberService = new ClaimMemberService(repository, claimIndex);
        ClaimsCommand command = command(adminClaimService, null, claimMemberService);
        Player player = mock(Player.class);
        Server server = mock(Server.class);
        OfflinePlayer member = mock(OfflinePlayer.class);
        when(player.hasPermission("havenclaims.admin.userclaims.edit")).thenReturn(true);
        when(player.getServer()).thenReturn(server);
        when(server.getOfflinePlayer(memberId)).thenReturn(member);
        when(member.getUniqueId()).thenReturn(memberId);
        when(member.getName()).thenReturn("Helper");
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{
                "admin",
                "userclaims",
                "member",
                "list",
                home.id().toString()
        });

        assertThat(messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList())
                .containsExactly(
                        "Members for Home:",
                        "- Helper (manager)"
                );
    }

    @Test
    void adminReloadCallsReloadActionAndSendsSuccessMessage() {
        boolean[] called = {false};
        Supplier<ReloadResult> reloadAction = () -> {
            called[0] = true;
            return ReloadResult.ok("reloaded");
        };
        ClaimsCommand command = commandWithReloadAction(reloadAction);
        Player admin = mockAdmin();
        List<Component> messages = captureMessages(admin);

        command.onCommand(admin, mockCommand(), "claim", new String[]{"admin", "reload"});

        assertThat(called[0]).isTrue();
        assertThat(messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList())
                .hasSize(1)
                .first()
                .asString()
                .contains("Config reloaded.")
                .contains("reloaded");
    }

    @Test
    void adminReloadSendsFailureMessageWhenReloadFails() {
        Supplier<ReloadResult> reloadAction = () -> ReloadResult.fail("Config file is invalid");
        ClaimsCommand command = commandWithReloadAction(reloadAction);
        Player admin = mockAdmin();
        List<Component> messages = captureMessages(admin);

        command.onCommand(admin, mockCommand(), "claim", new String[]{"admin", "reload"});

        assertThat(messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList())
                .hasSize(1)
                .first()
                .asString()
                .contains("Reload failed:")
                .contains("Config file is invalid");
    }

    @Test
    void adminBrowseRequiresDeletePermission() {
        ClaimsCommand command = command(new AdminClaimService());
        Player player = mock(Player.class);
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{"admin", "browse", UUID.randomUUID().toString()});

        assertThat(messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList())
                .containsExactly("You do not have permission to manage user claims.");
    }

    @Test
    void adminBrowseSendsUnavailableWhenServiceNull() {
        ClaimsCommand command = command(new AdminClaimService());
        Player player = mock(Player.class);
        when(player.hasPermission("havenclaims.admin.userclaims.delete")).thenReturn(true);
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{"admin", "browse", UUID.randomUUID().toString()});

        assertThat(messages.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList())
                .containsExactly("Admin claim management is not available yet.");
    }

    private static Player mockAdmin() {
        Player admin = mock(Player.class);
        when(admin.hasPermission("havenclaims.admin.reload")).thenReturn(true);
        return admin;
    }

    private static Command mockCommand() {
        return mock(Command.class);
    }

    private static ClaimsCommand commandWithReloadAction(Supplier<ReloadResult> reloadAction) {
        return new ClaimsCommand(
                mock(ClaimToolService.class),
                null,
                null,
                null,
                null,
                null,
                null,
                new MessageService(Map.ofEntries(
                        Map.entry("admin.reload.success", "<green>Config reloaded. <detail>"),
                        Map.entry("admin.reload.failed", "<red>Reload failed: <detail>"),
                        Map.entry("admin.no-permission", "<red>No permission.")
                )),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new AdminClaimService(),
                mock(HavenClaimsLimitService.class),
                reloadAction,
                null
        );
    }

    private static ClaimsCommand command(AdminClaimService adminClaimService) {
        return command(adminClaimService, null, null);
    }

    private static ClaimsCommand command(AdminClaimService adminClaimService, ClaimFlagService claimFlagService) {
        return command(adminClaimService, claimFlagService, null);
    }

    private static ClaimsCommand command(
            AdminClaimService adminClaimService,
            ClaimFlagService claimFlagService,
            ClaimMemberService claimMemberService
    ) {
        return new ClaimsCommand(
                mock(ClaimToolService.class),
                null,
                null,
                null,
                null,
                null,
                null,
                new MessageService(Map.ofEntries(
                        Map.entry("admin.claim.list-header", "<gold>Admin claims:"),
                        Map.entry("admin.claim.list-empty", "<yellow>No admin claims exist."),
                        Map.entry("admin.claim.list-entry", "<gray>- <yellow><claim_name></yellow> (<chunk_count> chunks) <claim_id>"),
                        Map.entry("admin.claim.no-permission", "<red>You do not have permission to manage admin claims."),
                        Map.entry("admin.userclaims.list-header", "<gold>Claims for <yellow><player></yellow>:"),
                        Map.entry("admin.userclaims.list-entry", "<gray>- <yellow><claim_name></yellow> (<chunk_count> chunks) <claim_id>"),
                        Map.entry("admin.userclaims.no-permission", "<red>You do not have permission to manage user claims."),
                        Map.entry("admin.userclaims.transfer-player-not-found", "<red>Player <player> is not online. Use a UUID to transfer offline players."),
                        Map.entry("admin.userclaims.transferred", "<green>Transferred <claim_name> to <player>."),
                        Map.entry("admin.userclaims.edit-no-permission", "<red>You do not have permission to edit user claims."),
                        Map.entry("admin.userclaims.flag-list-header", "<gold>Flags for <claim_name>:"),
                        Map.entry("admin.userclaims.flag-list-entry", "<gray>- <flag>: <state>"),
                        Map.entry("admin.userclaims.flag-set", "<green>Set <flag> to <state> for <claim_name>."),
                        Map.entry("admin.userclaims.flag-cycled", "<green>Cycled <flag> for <claim_name>."),
                        Map.entry("admin.userclaims.member-list-header", "<gold>Members for <claim_name>:"),
                        Map.entry("admin.userclaims.member-list-entry", "<gray>- <player> (<role>)"),
                        Map.entry("admin.userclaims.member-added", "<green>Added <player> as <role> to <claim_name>."),
                        Map.entry("admin.userclaims.member-removed", "<green>Removed <player> from <claim_name>."),
                        Map.entry("admin.claim.unavailable", "<red>Admin claim management is not available yet."),
                        Map.entry("admin.userclaims.browse.usage", "<red>Usage: /claim admin browse <player|uuid>")
                )),
                claimMemberService,
                null,
                claimFlagService,
                null,
                null,
                null,
                null,
                null,
                null,
                adminClaimService,
                mock(HavenClaimsLimitService.class),
                null,
                null
        );
    }

    private static List<Component> captureMessages(Player player) {
        List<Component> messages = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            messages.add(invocation.getArgument(0));
            return null;
        }).when(player).sendMessage(any(Component.class));
        return messages;
    }

    private static Claim claim(String name, UUID worldId, int chunkX) {
        Instant now = Instant.parse("2026-06-10T00:00:00Z");
        return new Claim(
                UUID.randomUUID(),
                name,
                OwnerType.ADMIN,
                null,
                new ClaimRegion(worldId, chunkX * 16, 0, chunkX * 16 + 15, 15),
                Map.of(),
                now,
                now
        );
    }

    private static Claim playerClaim(String name, UUID ownerId, UUID worldId, int chunkX) {
        return playerClaim(name, ownerId, worldId, chunkX, Map.of());
    }

    private static Claim playerClaim(String name, UUID ownerId, UUID worldId, int chunkX, Map<String, FlagState> flags) {
        return playerClaim(name, ownerId, worldId, chunkX, flags, Set.of());
    }

    private static Claim playerClaim(
            String name,
            UUID ownerId,
            UUID worldId,
            int chunkX,
            Map<String, FlagState> flags,
            Set<ClaimMember> members
    ) {
        Instant now = Instant.parse("2026-06-10T00:00:00Z");
        return new Claim(
                UUID.randomUUID(),
                name,
                OwnerType.PLAYER,
                ownerId,
                new ClaimRegion(worldId, chunkX * 16, 0, chunkX * 16 + 15, 15),
                flags,
                members,
                now,
                now
        );
    }

    private static final class FakeClaimRepository implements ClaimRepository {
        private final List<Claim> claims = new ArrayList<>();

        @Override
        public void saveClaim(Claim claim) {
            claims.removeIf(existing -> existing.id().equals(claim.id()));
            claims.add(claim);
        }

        @Override
        public void deleteClaim(UUID claimId) {
            claims.removeIf(claim -> claim.id().equals(claimId));
        }

        @Override
        public Optional<Claim> findClaimAt(UUID worldId, int chunkX, int chunkZ) {
            return Optional.empty();
        }

        @Override
        public Optional<Claim> findClaimById(UUID claimId) {
            return claims.stream()
                    .filter(claim -> claim.id().equals(claimId))
                    .findFirst();
        }

        @Override
        public List<Claim> findClaimsByOwner(OwnerType ownerType, UUID ownerUuid) {
            return claims.stream()
                    .filter(claim -> claim.owner() == ownerType)
                    .filter(claim -> ownerUuid == null || ownerUuid.equals(claim.ownerUuid()))
                    .toList();
        }

        @Override
        public List<Claim> findAllClaims() {
            return List.copyOf(claims);
        }
    }
}
