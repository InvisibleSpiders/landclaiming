package com.nick.landclaims.plugin.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nick.landclaims.api.limit.LandClaimsLimitService;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimCreationService;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.ClaimService;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.economy.ClaimPaymentService;
import com.nick.landclaims.plugin.economy.NoopEconomyService;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import com.nick.landclaims.plugin.limit.ClaimCostConfig;
import com.nick.landclaims.plugin.limit.ClaimCostService;
import com.nick.landclaims.plugin.limit.ClaimLimitRepository;
import com.nick.landclaims.plugin.limit.LimitService;
import com.nick.landclaims.plugin.message.MessageService;
import com.nick.landclaims.plugin.selection.SelectionService;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import com.nick.landclaims.plugin.tool.ClaimToolService;
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
                mock(LandClaimsLimitService.class),
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
        selectionService.replacePendingSelection(ownerId, Set.of(
                new ClaimChunk(worldId, 0, 0),
                new ClaimChunk(worldId, 1, 0)
        ));
        LimitService limitService = new LimitService(1, emptyLimitRepository());
        ClaimCostService costService = new ClaimCostService(
                claimIndex,
                limitService,
                new ClaimCostConfig(false, ClaimCostConfig.PricingMode.FLAT, 100.0, 100.0, 2.0)
        );
        ClaimToolService toolService = mock(ClaimToolService.class);
        ItemStack tool = mock(ItemStack.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.hasPermission("landclaims.claim")).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(tool);
        when(toolService.isClaimTool(tool)).thenReturn(true);
        when(toolService.currentCharges(tool)).thenReturn(10);
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
                null
        );

        command.onCommand(player, mock(Command.class), "claim", new String[]{"create", "Home"});

        assertThat(repository.savedClaims).isEmpty();
        assertThat(plain(messages)).containsExactly("This claim exceeds your claim limit.");
        verify(toolService, never()).spendCharges(any(), any(Integer.class));
    }

    @Test
    void costPreviewShowsUnavailableWhenPaidOverLimitIsDisabled() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        ClaimIndex claimIndex = new ClaimIndex();
        SelectionService selectionService = new SelectionService(new ClaimService());
        selectionService.replacePendingSelection(ownerId, Set.of(
                new ClaimChunk(worldId, 0, 0),
                new ClaimChunk(worldId, 1, 0)
        ));
        LimitService limitService = new LimitService(1, emptyLimitRepository());
        ClaimCostService costService = new ClaimCostService(
                claimIndex,
                limitService,
                new ClaimCostConfig(false, ClaimCostConfig.PricingMode.FLAT, 100.0, 100.0, 2.0)
        );
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(ownerId);
        when(player.hasPermission("landclaims.claim")).thenReturn(true);
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
                null
        );

        command.onCommand(player, mock(Command.class), "claim", new String[]{"cost"});

        assertThat(plain(messages)).containsExactly(
                "Selection: 2 chunks",
                "Current total after claim: 2 / 1 chunks",
                "Over limit: 1 chunks",
                "Cost: not available"
        );
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

    private static MessageService messages() {
        return new MessageService(Map.ofEntries(
                Map.entry("command.claim.no-permission", "<red>You do not have permission to create claims."),
                Map.entry("claim.over-limit-disabled", "<red>This claim exceeds your claim limit."),
                Map.entry("claim.cost-preview.selection", "<gray>Selection: <yellow><selected_chunks></yellow> chunks"),
                Map.entry("claim.cost-preview.current-total", "<gray>Current total after claim: <yellow><proposed_total_chunks></yellow> / <yellow><allowed_chunks></yellow> chunks"),
                Map.entry("claim.cost-preview.over-limit", "<gray>Over limit: <yellow><overage_chunks></yellow> chunks"),
                Map.entry("claim.cost-preview.cost", "<gray>Cost: <green><cost></green>"),
                Map.entry("claim.cost-preview.free", "free"),
                Map.entry("claim.cost-preview.unavailable", "not available"),
                Map.entry("claim.deleted", "<green>Deleted claim <white><claim_name></white>."),
                Map.entry("claim.delete.not-owner", "<red>Only the claim owner can delete this claim."),
                Map.entry("claim.info.unclaimed", "<yellow>You are not standing in a claim."),
                Map.entry("claim.selection-required", "<red>Select chunks first."),
                Map.entry("claim.hold-tool", "<red>Hold the claim tool."),
                Map.entry("claim.not-enough-charges", "<red>Need <needed>, have <available>."),
                Map.entry("claim.created", "<green>Claim <claim_name> created."),
                Map.entry("claim.create-denied", "<red><reason>"),
                Map.entry("claim.charged", "<green>Charged <cost>.")
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
                new com.nick.landclaims.plugin.admin.AdminClaimService(
                        repository, claimIndex, FlagRegistry.createDefault(), 32),
                mock(LandClaimsLimitService.class),
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
                worldId,
                Set.of(new ClaimChunk(worldId, chunkX, 0)),
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
