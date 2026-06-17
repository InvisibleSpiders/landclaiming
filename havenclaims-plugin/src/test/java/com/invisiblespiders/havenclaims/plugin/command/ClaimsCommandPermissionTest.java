package com.invisiblespiders.havenclaims.plugin.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invisiblespiders.havenclaims.api.limit.HavenClaimsLimitService;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeAction;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeCommand;
import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimCreationService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimDenyService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimMember;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimMemberService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRole;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimService;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import com.invisiblespiders.havenclaims.plugin.economy.ClaimPaymentService;
import com.invisiblespiders.havenclaims.plugin.economy.NoopEconomyService;
import com.invisiblespiders.havenclaims.plugin.flag.FlagRegistry;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimCostConfig;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimCostService;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimLimitRepository;
import com.invisiblespiders.havenclaims.plugin.limit.LimitService;
import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import com.invisiblespiders.havenclaims.plugin.selection.BlockPos;
import com.invisiblespiders.havenclaims.plugin.selection.SelectionService;
import com.invisiblespiders.havenclaims.plugin.storage.ClaimRepository;
import com.invisiblespiders.havenclaims.plugin.tool.ClaimToolService;
import com.invisiblespiders.havenclaims.plugin.ui.ClaimMenuService;
import com.invisiblespiders.havenclaims.plugin.ui.DialogService;
import com.invisiblespiders.havenclaims.plugin.visual.BorderColor;
import com.invisiblespiders.havenclaims.plugin.visual.ChunkBorderVisualService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

class ClaimsCommandPermissionTest {
    @Test
    void claimModeSubcommandDelegatesToClaimModeCommand() {
        ClaimModeCommand claimModeCommand = mock(ClaimModeCommand.class);
        ClaimsCommand command = new ClaimsCommand(mock(ClaimToolService.class));
        command.setClaimModeCommand(claimModeCommand);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        command.onCommand(player, mock(Command.class), "claim", new String[]{"mode", "on"});

        verify(claimModeCommand).execute(eq(player), eq(ClaimModeAction.ON));
    }

    @Test
    void rootSuggestionsDoNotIncludeRetiredToolCommand() {
        ClaimsCommand command = new ClaimsCommand(mock(ClaimToolService.class));

        List<String> suggestions = command.onTabComplete(
                mock(Player.class), mock(Command.class), "claim", new String[]{""});

        assertThat(suggestions).doesNotContain("tool");
        assertThat(suggestions).contains("mode");
    }

    @Test
    void createRequiresClaimPermissionBeforeCheckingSelectionServices() {
        ClaimsCommand command = new ClaimsCommand(
                mock(ClaimToolService.class),
                null,
                null,
                null,
                null,
                null,
                null,
                messages(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                mock(HavenClaimsLimitService.class),
                null,
                null,
                null
        );
        Player player = mock(Player.class);
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{"create", "Home"});

        assertThat(plain(messages)).containsExactly("You do not have permission to create claims.");
    }

    @Test
    void disabledPaidOverLimitDeniesOverLimitClaimWithoutChargingOrSaving() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimCreationService creationService = new ClaimCreationService(
                repository,
                claimIndex,
                new ClaimService(),
                FlagRegistry.createDefault(),
                3,
                3,
                32
        );
        SelectionService selectionService = new SelectionService(new ClaimService());
        selectionService.select(ownerId, new BlockPos(worldId, 0, 0));
        selectionService.select(ownerId, new BlockPos(worldId, 31, 15));
        LimitService limitService = new LimitService(1, emptyLimitRepository());
        ClaimCostService costService = new ClaimCostService(
                claimIndex,
                limitService,
                new ClaimCostConfig(false, 100.0, 60)
        );
        ClaimToolService toolService = mock(ClaimToolService.class);
        ItemStack tool = mock(ItemStack.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.hasPermission("havenclaims.claim")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(tool);
        when(toolService.isClaimTool(tool)).thenReturn(true);
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = new ClaimsCommand(
                toolService,
                selectionService,
                creationService,
                claimIndex,
                costService,
                new ClaimPaymentService(new NoopEconomyService()),
                null,
                messages(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                limitService,
                null,
                null,
                null
        );

        command.onCommand(player, mock(Command.class), "claim", new String[]{"create", "Home"});

        assertThat(repository.savedClaims).isEmpty();
        assertThat(plain(messages)).containsExactly("This claim exceeds your claim limit.");
    }

    @Test
    void costPreviewShowsUnavailableWhenPaidOverLimitIsDisabled() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        ClaimIndex claimIndex = new ClaimIndex();
        SelectionService selectionService = new SelectionService(new ClaimService());
        selectionService.select(ownerId, new BlockPos(worldId, 0, 0));
        selectionService.select(ownerId, new BlockPos(worldId, 31, 15));
        LimitService limitService = new LimitService(1, emptyLimitRepository());
        ClaimCostService costService = new ClaimCostService(
                claimIndex,
                limitService,
                new ClaimCostConfig(false, 100.0, 60)
        );
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.hasPermission("havenclaims.claim")).thenReturn(true);
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = new ClaimsCommand(
                mock(ClaimToolService.class),
                selectionService,
                mock(ClaimCreationService.class),
                claimIndex,
                costService,
                new ClaimPaymentService(new NoopEconomyService()),
                null,
                messages(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                limitService,
                null,
                null,
                null
        );

        command.onCommand(player, mock(Command.class), "claim", new String[]{"cost"});

        assertThat(plain(messages)).containsExactly(
                "Selection: 512 blocks",
                "Current total after claim: 512 / 1 blocks",
                "Over limit: 511 blocks",
                "Cost: not available"
        );
    }

    @Test
    void createWithoutNameUsesNextDefaultClaimName() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        Claim existing = playerClaim("Claim 1", ownerId, worldId, 5);
        repository.claims.add(existing);
        claimIndex.add(existing);
        ClaimCreationService creationService = new ClaimCreationService(
                repository,
                claimIndex,
                new ClaimService(),
                FlagRegistry.createDefault(),
                3,
                3,
                32
        );
        SelectionService selectionService = new SelectionService(new ClaimService());
        selectionService.select(ownerId, new BlockPos(worldId, 0, 0));
        selectionService.select(ownerId, new BlockPos(worldId, 15, 15));
        ClaimToolService toolService = mock(ClaimToolService.class);
        ItemStack tool = mock(ItemStack.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.hasPermission("havenclaims.claim")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(tool);
        when(toolService.isClaimTool(tool)).thenReturn(true);
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = new ClaimsCommand(
                toolService,
                selectionService,
                creationService,
                claimIndex,
                null,
                null,
                null,
                messages(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                mock(HavenClaimsLimitService.class),
                null,
                null,
                null
        );

        command.onCommand(player, mock(Command.class), "claim", new String[]{"create"});

        assertThat(repository.savedClaims).hasSize(1);
        assertThat(repository.savedClaims.get(0).name()).isEqualTo("Claim 2");
        assertThat(plain(messages)).containsExactly("Claim Claim 2 created.");
    }

    @Test
    void createDoesNotRequireHeldPermanentClaimToolOrCharges() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimCreationService creationService = new ClaimCreationService(
                repository,
                claimIndex,
                new ClaimService(),
                FlagRegistry.createDefault(),
                3,
                3,
                32
        );
        SelectionService selectionService = new SelectionService(new ClaimService());
        selectionService.select(ownerId, new BlockPos(worldId, 0, 0));
        selectionService.select(ownerId, new BlockPos(worldId, 15, 15));
        ClaimToolService toolService = mock(ClaimToolService.class);
        ItemStack normalItem = mock(ItemStack.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.hasPermission("havenclaims.claim")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(normalItem);
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = new ClaimsCommand(
                toolService,
                selectionService,
                creationService,
                claimIndex,
                null,
                null,
                null,
                messages(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                mock(HavenClaimsLimitService.class),
                null,
                null,
                null
        );

        command.onCommand(player, mock(Command.class), "claim", new String[]{"create", "Home"});

        assertThat(repository.savedClaims).hasSize(1);
        assertThat(plain(messages)).containsExactly("Claim Home created.");
        verify(toolService, never()).isClaimTool(any());
    }

    @Test
    void createWithDialogPreferenceOpensConfirmationWithoutSaving() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimCreationService creationService = new ClaimCreationService(
                repository,
                claimIndex,
                new ClaimService(),
                FlagRegistry.createDefault(),
                3,
                3,
                32
        );
        SelectionService selectionService = new SelectionService(new ClaimService());
        selectionService.select(ownerId, new BlockPos(worldId, 0, 0));
        selectionService.select(ownerId, new BlockPos(worldId, 15, 15));
        ClaimToolService toolService = mock(ClaimToolService.class);
        ItemStack tool = mock(ItemStack.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.hasPermission("havenclaims.claim")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(tool);
        when(toolService.isClaimTool(tool)).thenReturn(true);
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = new ClaimsCommand(
                toolService,
                selectionService,
                creationService,
                claimIndex,
                new ClaimCostService(
                        claimIndex,
                        new LimitService(1000, emptyLimitRepository()),
                        new ClaimCostConfig(false, 100.0, 60)
                ),
                new ClaimPaymentService(new NoopEconomyService()),
                null,
                messages(),
                null,
                null,
                null,
                null,
                null,
                new DialogService(true),
                null,
                null,
                null,
                null,
                mock(HavenClaimsLimitService.class),
                null,
                null,
                null
        );

        command.onCommand(player, mock(Command.class), "claim", new String[]{"create"});

        assertThat(repository.savedClaims).isEmpty();
        assertThat(plain(messages)).containsExactly(
                "Create claim Claim 1?",
                "Selection: 256 blocks",
                "Total after claim: 256 / 1000 blocks",
                "Over limit: 0 blocks",
                "Cost: free",
                "Actions:",
                "- Create Claim (/claim createconfirm Claim 1)",
                "- Cancel (/claim cancel)"
        );
    }

    @Test
    void createPreviewDoesNotRequireHeldPermanentClaimToolOrCharges() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimCreationService creationService = new ClaimCreationService(
                repository,
                claimIndex,
                new ClaimService(),
                FlagRegistry.createDefault(),
                3,
                3,
                32
        );
        SelectionService selectionService = new SelectionService(new ClaimService());
        selectionService.select(ownerId, new BlockPos(worldId, 0, 0));
        selectionService.select(ownerId, new BlockPos(worldId, 15, 15));
        ClaimToolService toolService = mock(ClaimToolService.class);
        ItemStack normalItem = mock(ItemStack.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.hasPermission("havenclaims.claim")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(normalItem);
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = new ClaimsCommand(
                toolService,
                selectionService,
                creationService,
                claimIndex,
                new ClaimCostService(
                        claimIndex,
                        new LimitService(1000, emptyLimitRepository()),
                        new ClaimCostConfig(false, 100.0, 60)
                ),
                new ClaimPaymentService(new NoopEconomyService()),
                null,
                messages(),
                null,
                null,
                null,
                null,
                null,
                new DialogService(true),
                null,
                null,
                null,
                null,
                mock(HavenClaimsLimitService.class),
                null,
                null,
                null
        );

        command.onCommand(player, mock(Command.class), "claim", new String[]{"create", "Home"});

        assertThat(repository.savedClaims).isEmpty();
        assertThat(plain(messages)).containsExactly(
                "Create claim Home?",
                "Selection: 256 blocks",
                "Total after claim: 256 / 1000 blocks",
                "Over limit: 0 blocks",
                "Cost: free",
                "Actions:",
                "- Create Claim (/claim createconfirm Home)",
                "- Cancel (/claim cancel)"
        );
        verify(toolService, never()).isClaimTool(any());
    }

    @Test
    void createConfirmWithoutNameCreatesNextDefaultClaimName() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimCreationService creationService = new ClaimCreationService(
                repository,
                claimIndex,
                new ClaimService(),
                FlagRegistry.createDefault(),
                3,
                3,
                32
        );
        SelectionService selectionService = new SelectionService(new ClaimService());
        selectionService.select(ownerId, new BlockPos(worldId, 0, 0));
        selectionService.select(ownerId, new BlockPos(worldId, 15, 15));
        ClaimToolService toolService = mock(ClaimToolService.class);
        ItemStack tool = mock(ItemStack.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.hasPermission("havenclaims.claim")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(tool);
        when(toolService.isClaimTool(tool)).thenReturn(true);
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = new ClaimsCommand(
                toolService,
                selectionService,
                creationService,
                claimIndex,
                null,
                null,
                null,
                messages(),
                null,
                null,
                null,
                null,
                null,
                new DialogService(true),
                null,
                null,
                null,
                null,
                mock(HavenClaimsLimitService.class),
                null,
                null,
                null
        );

        command.onCommand(player, mock(Command.class), "claim", new String[]{"createconfirm"});

        assertThat(repository.savedClaims).hasSize(1);
        assertThat(repository.savedClaims.get(0).name()).isEqualTo("Claim 1");
        assertThat(plain(messages)).containsExactly("Claim Claim 1 created.");
    }

    @Test
    void rootCommandOpensOwnedClaimDashboardAnywhere() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        ClaimIndex claimIndex = new ClaimIndex();
        Claim claim = playerClaim("Home", ownerId, worldId, 0);
        claimIndex.add(claim);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.hasPermission("havenclaims.gui")).thenReturn(true);
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = new ClaimsCommand(
                mock(ClaimToolService.class),
                null,
                null,
                claimIndex,
                null,
                null,
                null,
                dashboardMessages(),
                null,
                null,
                null,
                null,
                new ClaimMenuService(dashboardMessages()),
                new DialogService(false),
                null,
                null,
                null,
                null,
                mock(HavenClaimsLimitService.class),
                null,
                null,
                null
        );

        command.onCommand(player, mock(Command.class), "claim", new String[]{});

        assertThat(plain(messages)).containsExactly(
                "My Claims",
                "- Home (256 blocks) /claim menu " + claim.id(),
                "Actions:",
                "- Create Claim (/claim create)",
                "- Claim Cost (/claim cost)"
        );
    }

    @Test
    void memberListWithClaimIdDoesNotRequireStandingInClaim() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        Claim claim = new Claim(
                UUID.randomUUID(),
                "Home",
                OwnerType.PLAYER,
                ownerId,
                new ClaimRegion(worldId, 0, 0, 15, 15),
                Map.of(),
                Set.of(new ClaimMember(memberId, ClaimRole.MANAGER)),
                java.time.Instant.now(),
                java.time.Instant.now()
        );
        repository.claims.add(claim);
        claimIndex.add(claim);
        Player player = mock(Player.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        org.bukkit.OfflinePlayer member = mock(org.bukkit.OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.getServer()).thenReturn(server);
        when(server.getOfflinePlayer(memberId)).thenReturn(member);
        when(member.getUniqueId()).thenReturn(memberId);
        when(member.getName()).thenReturn("Helper");
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = new ClaimsCommand(
                mock(ClaimToolService.class),
                null,
                null,
                claimIndex,
                null,
                null,
                null,
                detailMessages(),
                new ClaimMemberService(repository, claimIndex),
                null,
                null,
                null,
                null,
                new DialogService(false),
                null,
                null,
                null,
                null,
                mock(HavenClaimsLimitService.class),
                null,
                null,
                null
        );

        command.onCommand(player, mock(Command.class), "claim", new String[]{"member", "list", claim.id().toString()});

        assertThat(plain(messages)).containsExactly(
                "Members for Home",
                "- Helper (manager)",
                "Actions:",
                "- Add Member (/claim member add " + claim.id() + " <player> [member|manager])",
                "- Remove Helper (/claim member remove " + claim.id() + " " + memberId + ")",
                "- Back (/claim menu " + claim.id() + ")"
        );
    }

    @Test
    void memberRemoveWithClaimIdDoesNotRequireStandingInClaim() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        Claim claim = new Claim(
                UUID.randomUUID(),
                "Home",
                OwnerType.PLAYER,
                ownerId,
                new ClaimRegion(worldId, 0, 0, 15, 15),
                Map.of(),
                Set.of(new ClaimMember(memberId, ClaimRole.MEMBER)),
                java.time.Instant.now(),
                java.time.Instant.now()
        );
        repository.claims.add(claim);
        claimIndex.add(claim);
        Player player = mock(Player.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        org.bukkit.OfflinePlayer member = mock(org.bukkit.OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.getServer()).thenReturn(server);
        when(server.getOfflinePlayer(memberId)).thenReturn(member);
        when(member.getName()).thenReturn("Helper");
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = new ClaimsCommand(
                mock(ClaimToolService.class),
                null,
                null,
                claimIndex,
                null,
                null,
                null,
                detailMessages(),
                new ClaimMemberService(repository, claimIndex),
                null,
                null,
                null,
                null,
                new DialogService(false),
                null,
                null,
                null,
                null,
                mock(HavenClaimsLimitService.class),
                null,
                null,
                null
        );

        command.onCommand(player, mock(Command.class), "claim",
                new String[]{"member", "remove", claim.id().toString(), memberId.toString()});

        assertThat(repository.savedClaims).hasSize(1);
        assertThat(repository.savedClaims.get(0).members()).isEmpty();
        assertThat(plain(messages)).containsExactly("Removed Helper from this claim.");
    }

    @Test
    void infoWithClaimIdUsesClaimDetailDialogFallbackAnywhere() {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        ClaimIndex claimIndex = new ClaimIndex();
        Claim claim = new Claim(
                UUID.randomUUID(),
                "Home",
                OwnerType.PLAYER,
                ownerId,
                new ClaimRegion(worldId, 0, 0, 31, 15),
                Map.of("build", com.invisiblespiders.havenclaims.api.flag.FlagState.ALL),
                Set.of(new ClaimMember(memberId, ClaimRole.MEMBER)),
                Set.of(deniedId),
                java.time.Instant.now(),
                java.time.Instant.now()
        );
        claimIndex.add(claim);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = new ClaimsCommand(
                mock(ClaimToolService.class),
                null,
                null,
                claimIndex,
                null,
                null,
                null,
                detailMessages(),
                null,
                null,
                null,
                null,
                null,
                new DialogService(false),
                null,
                null,
                null,
                null,
                mock(HavenClaimsLimitService.class),
                null,
                null,
                null
        );

        command.onCommand(player, mock(Command.class), "claim", new String[]{"info", claim.id().toString()});

        assertThat(plain(messages)).containsExactly(
                "Claim: Home",
                "Owner type: PLAYER",
                "Blocks: 512",
                "Members: 1",
                "Denied players: 1",
                "Configured flags: 1",
                "You are owner: true",
                "Actions:",
                "- Flags (/claim flags " + claim.id() + ")",
                "- Members (/claim member list " + claim.id() + ")",
                "- Denied Players (/claim denied " + claim.id() + ")",
                "- Back (/claim menu " + claim.id() + ")"
        );
    }

    @Test
    void deniedPlayersWithClaimIdDoesNotRequireStandingInClaim() {
        UUID ownerId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        Claim claim = new Claim(
                UUID.randomUUID(),
                "Home",
                OwnerType.PLAYER,
                ownerId,
                new ClaimRegion(worldId, 0, 0, 15, 15),
                Map.of(),
                Set.of(),
                Set.of(deniedId),
                java.time.Instant.now(),
                java.time.Instant.now()
        );
        repository.claims.add(claim);
        claimIndex.add(claim);
        Player player = mock(Player.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        org.bukkit.OfflinePlayer denied = mock(org.bukkit.OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.hasPermission("havenclaims.deny.manage")).thenReturn(true);
        when(player.getServer()).thenReturn(server);
        when(server.getOfflinePlayer(deniedId)).thenReturn(denied);
        when(denied.getUniqueId()).thenReturn(deniedId);
        when(denied.getName()).thenReturn("Visitor");
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = new ClaimsCommand(
                mock(ClaimToolService.class),
                null,
                null,
                claimIndex,
                null,
                null,
                null,
                detailMessages(),
                null,
                new ClaimDenyService(repository, claimIndex),
                null,
                null,
                null,
                new DialogService(false),
                null,
                null,
                null,
                null,
                mock(HavenClaimsLimitService.class),
                null,
                null,
                null
        );

        command.onCommand(player, mock(Command.class), "claim", new String[]{"denied", claim.id().toString()});

        assertThat(plain(messages)).containsExactly(
                "Denied players for Home",
                "- Visitor",
                "Actions:",
                "- Deny Player (/claim deny " + claim.id() + " <player|uuid>)",
                "- Allow Visitor (/claim undeny " + claim.id() + " " + deniedId + ")",
                "- Back (/claim menu " + claim.id() + ")"
        );
    }

    @Test
    void undenyWithClaimIdDoesNotRequireStandingInClaim() {
        UUID ownerId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        Claim claim = new Claim(
                UUID.randomUUID(),
                "Home",
                OwnerType.PLAYER,
                ownerId,
                new ClaimRegion(worldId, 0, 0, 15, 15),
                Map.of(),
                Set.of(),
                Set.of(deniedId),
                java.time.Instant.now(),
                java.time.Instant.now()
        );
        repository.claims.add(claim);
        claimIndex.add(claim);
        Player player = mock(Player.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        org.bukkit.OfflinePlayer denied = mock(org.bukkit.OfflinePlayer.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.hasPermission("havenclaims.deny.manage")).thenReturn(true);
        when(player.getServer()).thenReturn(server);
        when(server.getOfflinePlayer(deniedId)).thenReturn(denied);
        when(denied.getName()).thenReturn("Visitor");
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = new ClaimsCommand(
                mock(ClaimToolService.class),
                null,
                null,
                claimIndex,
                null,
                null,
                null,
                detailMessages(),
                null,
                new ClaimDenyService(repository, claimIndex),
                null,
                null,
                null,
                new DialogService(false),
                null,
                null,
                null,
                null,
                mock(HavenClaimsLimitService.class),
                null,
                null,
                null
        );

        command.onCommand(player, mock(Command.class), "claim",
                new String[]{"undeny", claim.id().toString(), deniedId.toString()});

        assertThat(repository.savedClaims).hasSize(1);
        assertThat(repository.savedClaims.get(0).deniedPlayers()).isEmpty();
        assertThat(plain(messages)).containsExactly("Allowed Visitor back into this claim.");
    }

    @Test
    void ownerCanDeleteCurrentPlayerClaim() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        Claim claim = playerClaim("Home", ownerId, worldId, 0);
        repository.claims.add(claim);
        claimIndex.add(claim);
        Player player = playerAt(ownerId, worldId, 0, 0);
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = commandForClaimDeletion(repository, claimIndex);

        command.onCommand(player, mock(Command.class), "claim", new String[]{"delete"});

        assertThat(repository.deletedClaims).containsExactly(claim.id());
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).isEmpty();
        assertThat(plain(messages)).containsExactly("Deleted claim Home.");
    }

    @Test
    void nonOwnerCannotDeleteCurrentPlayerClaim() {
        UUID ownerId = UUID.randomUUID();
        UUID visitorId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        Claim claim = playerClaim("Home", ownerId, worldId, 0);
        repository.claims.add(claim);
        claimIndex.add(claim);
        Player player = playerAt(visitorId, worldId, 0, 0);
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = commandForClaimDeletion(repository, claimIndex);

        command.onCommand(player, mock(Command.class), "claim", new String[]{"abandon"});

        assertThat(repository.deletedClaims).isEmpty();
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).contains(claim);
        assertThat(plain(messages)).containsExactly("Only the claim owner can delete this claim.");
    }

    @Test
    void deleteWithClaimIdRequiresConfirmationAndDoesNotNeedStandingInClaim() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        Claim claim = playerClaim("Home", ownerId, worldId, 0);
        repository.claims.add(claim);
        claimIndex.add(claim);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = commandForClaimDeletion(repository, claimIndex);

        command.onCommand(player, mock(Command.class), "claim", new String[]{"delete", claim.id().toString()});

        assertThat(repository.deletedClaims).isEmpty();
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).contains(claim);
        assertThat(plain(messages)).containsExactly(
                "Delete claim Home?",
                "Actions:",
                "- Confirm Delete (/claim deleteconfirm " + claim.id() + ")",
                "- Back (/claim menu " + claim.id() + ")"
        );
    }

    @Test
    void deleteConfirmWithClaimIdDeletesOwnedClaimAnywhere() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        Claim claim = playerClaim("Home", ownerId, worldId, 0);
        repository.claims.add(claim);
        claimIndex.add(claim);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = commandForClaimDeletion(repository, claimIndex);

        command.onCommand(player, mock(Command.class), "claim", new String[]{"deleteconfirm", claim.id().toString()});

        assertThat(repository.deletedClaims).containsExactly(claim.id());
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).isEmpty();
        assertThat(plain(messages)).containsExactly("Deleted claim Home.");
    }

    @Test
    void viewBorderWithClaimIdShowsOwnedClaimBorderAnywhere() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        ClaimIndex claimIndex = new ClaimIndex();
        Claim claim = playerClaim("Home", ownerId, worldId, 0);
        claimIndex.add(claim);
        ChunkBorderVisualService visualService = mock(ChunkBorderVisualService.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        List<Component> messages = captureMessages(player);
        ClaimsCommand command = new ClaimsCommand(
                mock(ClaimToolService.class),
                null,
                null,
                claimIndex,
                null,
                null,
                null,
                messages(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                visualService,
                null,
                null,
                mock(HavenClaimsLimitService.class),
                null,
                null,
                null
        );

        command.onCommand(player, mock(Command.class), "claim", new String[]{"viewborder", claim.id().toString()});

        verify(visualService).showSelection(player, claim.region(), BorderColor.GOLD);
        assertThat(plain(messages)).containsExactly("Showing this claim border.");
    }

    private static MessageService messages() {
        return new MessageService(Map.ofEntries(
                Map.entry("command.claim.no-permission", "<red>You do not have permission to create claims."),
                Map.entry("claim.over-limit-disabled", "<red>This claim exceeds your claim limit."),
                Map.entry("claim.cost-preview.selection", "<gray>Selection: <yellow><selected_blocks></yellow> blocks"),
                Map.entry("claim.cost-preview.current-total", "<gray>Current total after claim: <yellow><proposed_total_blocks></yellow> / <yellow><allowed_blocks></yellow> blocks"),
                Map.entry("claim.cost-preview.over-limit", "<gray>Over limit: <yellow><overage_blocks></yellow> blocks"),
                Map.entry("claim.cost-preview.cost", "<gray>Cost: <green><cost></green>"),
                Map.entry("claim.cost-preview.free", "free"),
                Map.entry("claim.cost-preview.unavailable", "not available"),
                Map.entry("claim.deleted", "<green>Deleted claim <white><claim_name></white>."),
                Map.entry("claim.delete.not-owner", "<red>Only the claim owner can delete this claim."),
                Map.entry("claim.delete.confirm-title", "<gold>Delete claim <yellow><claim_name></yellow>?"),
                Map.entry("claim.delete.actions-header", "<gold>Actions:"),
                Map.entry("claim.delete.action", "<gray>- <yellow><label></yellow> (<command>)"),
                Map.entry("claim.visual.border-claim", "<green>Showing this claim border."),
                Map.entry("claim.info.unclaimed", "<yellow>You are not standing in a claim."),
                Map.entry("claim.selection-required", "<red>Select chunks first."),
                Map.entry("claim.created", "<green>Claim <claim_name> created."),
                Map.entry("claim.create-preview.title", "<gold>Create claim <yellow><claim_name></yellow>?"),
                Map.entry("claim.create-preview.selection", "<gray>Selection: <yellow><selected_blocks></yellow> blocks"),
                Map.entry("claim.create-preview.current-total", "<gray>Total after claim: <yellow><proposed_total_blocks></yellow> / <yellow><allowed_blocks></yellow> blocks"),
                Map.entry("claim.create-preview.over-limit", "<gray>Over limit: <yellow><overage_blocks></yellow> blocks"),
                Map.entry("claim.create-preview.cost", "<gray>Cost: <green><cost></green>"),
                Map.entry("claim.create-preview.actions-header", "<gold>Actions:"),
                Map.entry("claim.create-preview.action", "<gray>- <yellow><label></yellow> (<command>)"),
                Map.entry("claim.create-denied", "<red><reason>"),
                Map.entry("claim.charged", "<green>Charged <cost>.")
        ));
    }

    private static MessageService dashboardMessages() {
        return new MessageService(Map.ofEntries(
                Map.entry("claim.menu.no-permission", "<red>You do not have permission to open claim menus."),
                Map.entry("claim.dashboard.title", "<gold>My Claims"),
                Map.entry("claim.dashboard.empty", "<yellow>You do not have any claims yet."),
                Map.entry("claim.dashboard.claim", "<gray>- <yellow><claim_name></yellow> (<block_count> blocks) <command>"),
                Map.entry("claim.dashboard.actions-header", "<gold>Actions:"),
                Map.entry("claim.dashboard.action", "<gray>- <yellow><label></yellow> (<command>)"),
                Map.entry("claim.dashboard.action-labels.create", "Create Claim"),
                Map.entry("claim.dashboard.action-labels.cost", "Claim Cost"),
                Map.entry("claim.menu.action-labels.flags", "Flags"),
                Map.entry("claim.menu.action-labels.members", "Members"),
                Map.entry("claim.menu.action-labels.info", "Info")
        ));
    }

    private static MessageService detailMessages() {
        return new MessageService(Map.ofEntries(
                Map.entry("command.unavailable.claim-info", "<red>Claim info is not available yet."),
                Map.entry("claim.menu.invalid-claim-id", "<red>Claim id must be a UUID."),
                Map.entry("claim.menu.claim-not-found", "<red>No claim found with that id."),
                Map.entry("claim.menu.not-owner", "<red>You can only manage claims you own or manage."),
                Map.entry("claim.deny.no-permission", "<red>You do not have permission to manage denied claim access."),
                Map.entry("claim.member.list-empty", "<yellow>This claim has no members."),
                Map.entry("claim.member.list-header", "<gold>Claim members:"),
                Map.entry("claim.member.list-entry", "<gray>- <yellow><player></yellow> (<role>)"),
                Map.entry("claim.member.removed", "<green>Removed <yellow><player></yellow> from this claim."),
                Map.entry("claim.deny.list-empty", "<yellow>This claim has no denied players."),
                Map.entry("claim.deny.list-header", "<gold>Denied players:"),
                Map.entry("claim.deny.list-entry", "<gray>- <yellow><player></yellow>"),
                Map.entry("claim.deny.removed", "<green>Allowed <yellow><player></yellow> back into this claim."),
                Map.entry("claim.info.name", "<gold>Claim: <yellow><claim_name></yellow>"),
                Map.entry("claim.info.owner-type", "<gray>Owner type: <white><owner_type></white>"),
                Map.entry("claim.info.blocks", "<gray>Blocks: <white><block_count></white>"),
                Map.entry("claim.info.members", "<gray>Members: <white><member_count></white>"),
                Map.entry("claim.info.denied", "<gray>Denied players: <white><denied_count></white>"),
                Map.entry("claim.info.flags", "<gray>Configured flags: <white><flag_count></white>"),
                Map.entry("claim.info.you-own", "<gray>You are owner: <white><is_owner></white>"),
                Map.entry("claim.info.actions-header", "<gold>Actions:"),
                Map.entry("claim.info.action", "<gray>- <yellow><label></yellow> (<command>)"),
                Map.entry("claim.member.dialog.title", "<gold>Members for <yellow><claim_name></yellow>"),
                Map.entry("claim.member.dialog.row", "<gray>- <yellow><player></yellow> (<role>)"),
                Map.entry("claim.member.dialog.actions-header", "<gold>Actions:"),
                Map.entry("claim.member.dialog.action", "<gray>- <yellow><label></yellow> (<command>)"),
                Map.entry("claim.deny.dialog.title", "<gold>Denied players for <yellow><claim_name></yellow>"),
                Map.entry("claim.deny.dialog.row", "<gray>- <yellow><player></yellow>"),
                Map.entry("claim.deny.dialog.actions-header", "<gold>Actions:"),
                Map.entry("claim.deny.dialog.action", "<gray>- <yellow><label></yellow> (<command>)")
        ));
    }

    private static ClaimLimitRepository emptyLimitRepository() {
        return new ClaimLimitRepository() {
            @Override public OptionalInt getLimit(UUID playerId) { return OptionalInt.empty(); }
            @Override public void setLimit(UUID playerId, int limit) {}
            @Override public void updateLimit(UUID playerId, int defaultLimit, java.util.function.IntUnaryOperator updater) {}
        };
    }

    private static List<Component> captureMessages(Player player) {
        List<Component> messages = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            messages.add(invocation.getArgument(0));
            return null;
        }).when(player).sendMessage(any(Component.class));
        return messages;
    }

    private static List<String> plain(List<Component> messages) {
        return messages.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();
    }

    private static ClaimsCommand commandForClaimDeletion(FakeClaimRepository repository, ClaimIndex claimIndex) {
        return new ClaimsCommand(
                mock(ClaimToolService.class),
                null,
                null,
                claimIndex,
                null,
                null,
                null,
                messages(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new com.invisiblespiders.havenclaims.plugin.admin.AdminClaimService(
                        repository, claimIndex, FlagRegistry.createDefault(), 32),
                mock(HavenClaimsLimitService.class),
                null,
                null,
                null
        );
    }

    private static Player playerAt(UUID playerId, UUID worldId, int chunkX, int chunkZ) {
        Player player = mock(Player.class);
        World world = mock(World.class);
        Location location = mock(Location.class);
        Chunk chunk = mock(Chunk.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getWorld()).thenReturn(world);
        when(world.getUID()).thenReturn(worldId);
        when(player.getLocation()).thenReturn(location);
        when(location.getChunk()).thenReturn(chunk);
        when(chunk.getX()).thenReturn(chunkX);
        when(chunk.getZ()).thenReturn(chunkZ);
        return player;
    }

    private static Claim playerClaim(String name, UUID ownerId, UUID worldId, int chunkX) {
        return new Claim(
                UUID.randomUUID(),
                name,
                OwnerType.PLAYER,
                ownerId,
                new ClaimRegion(worldId, chunkX * 16, 0, chunkX * 16 + 15, 15),
                Map.of(),
                java.time.Instant.now(),
                java.time.Instant.now()
        );
    }

    private static final class FakeClaimRepository implements ClaimRepository {
        private final List<Claim> claims = new ArrayList<>();
        private final List<Claim> savedClaims = new ArrayList<>();
        private final List<UUID> deletedClaims = new ArrayList<>();

        @Override public void saveClaim(Claim claim) { savedClaims.add(claim); }
        @Override public void replaceClaims(Claim replacementClaim, List<UUID> deletedClaimIds) { savedClaims.add(replacementClaim); }
        @Override public void deleteClaim(UUID claimId) {
            deletedClaims.add(claimId);
            claims.removeIf(claim -> claim.id().equals(claimId));
        }
        @Override public Optional<Claim> findClaimAt(UUID worldId, int chunkX, int chunkZ) { return Optional.empty(); }
        @Override public Optional<Claim> findClaimById(UUID claimId) {
            return claims.stream().filter(claim -> claim.id().equals(claimId)).findFirst();
        }
        @Override public List<Claim> findClaimsByOwner(OwnerType ownerType, UUID ownerUuid) { return List.of(); }
        @Override public List<Claim> findAllClaims() { return List.of(); }
    }
}
