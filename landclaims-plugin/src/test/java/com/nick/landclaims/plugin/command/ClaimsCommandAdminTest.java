package com.nick.landclaims.plugin.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nick.landclaims.plugin.admin.AdminClaimService;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import com.nick.landclaims.plugin.message.MessageService;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import com.nick.landclaims.plugin.tool.ClaimToolService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
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
                .containsExactlyInAnyOrder("create", "list", "delete", "teleport");
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
        when(player.hasPermission("landclaims.admin.claim.list")).thenReturn(true);
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

    private static ClaimsCommand command(AdminClaimService adminClaimService) {
        return new ClaimsCommand(
                mock(ClaimToolService.class),
                null,
                null,
                null,
                null,
                null,
                null,
                new MessageService(Map.of(
                        "admin.claim.list-header", "<gold>Admin claims:",
                        "admin.claim.list-empty", "<yellow>No admin claims exist.",
                        "admin.claim.list-entry", "<gray>- <yellow><claim_name></yellow> (<chunk_count> chunks) <claim_id>",
                        "admin.claim.no-permission", "<red>You do not have permission to manage admin claims."
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
                adminClaimService
        );
    }

    private static Claim claim(String name, UUID worldId, int chunkX) {
        Instant now = Instant.parse("2026-06-10T00:00:00Z");
        return new Claim(
                UUID.randomUUID(),
                name,
                OwnerType.ADMIN,
                null,
                worldId,
                Set.of(new ClaimChunk(worldId, chunkX, 0)),
                Map.of(),
                now,
                now
        );
    }

    private static final class FakeClaimRepository implements ClaimRepository {
        private final List<Claim> claims = new ArrayList<>();

        @Override
        public void saveClaim(Claim claim) {
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
            return List.of();
        }

        @Override
        public List<Claim> findAllClaims() {
            return List.copyOf(claims);
        }
    }
}
