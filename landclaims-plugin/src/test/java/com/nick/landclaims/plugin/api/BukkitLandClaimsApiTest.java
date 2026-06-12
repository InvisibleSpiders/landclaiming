package com.nick.landclaims.plugin.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nick.landclaims.api.claim.ClaimView;
import com.nick.landclaims.api.flag.FlagState;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import com.nick.landclaims.plugin.protection.ProtectionService;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class BukkitLandClaimsApiTest {
    @Test
    void findsClaimAtLocation() {
        UUID worldId = UUID.randomUUID();
        Claim claim = claim(UUID.randomUUID(), worldId, Set.of(new ClaimChunk(worldId, 2, -3)), Map.of("build", FlagState.OFF));
        FakeClaimRepository repository = new FakeClaimRepository(List.of(claim));
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.add(claim);
        BukkitLandClaimsApi api = api(repository, claimIndex);

        Optional<ClaimView> found = api.getClaimAt(location(worldId, 2, -3));

        assertThat(found).contains(claim);
    }

    @Test
    void allowsInteractionInUnclaimedChunks() {
        BukkitLandClaimsApi api = api(new FakeClaimRepository(List.of()), new ClaimIndex());

        boolean allowed = api.canInteract(player(UUID.randomUUID()), location(UUID.randomUUID(), 0, 0), "build");

        assertThat(allowed).isTrue();
    }

    @Test
    void deniesInteractionWhenClaimFlagDeniesStranger() {
        UUID worldId = UUID.randomUUID();
        Claim claim = claim(UUID.randomUUID(), worldId, Set.of(new ClaimChunk(worldId, 0, 0)), Map.of("item_drop", FlagState.OFF));
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.add(claim);
        BukkitLandClaimsApi api = api(new FakeClaimRepository(List.of(claim)), claimIndex);

        boolean allowed = api.canInteract(player(UUID.randomUUID()), location(worldId, 0, 0), "item_drop");

        assertThat(allowed).isFalse();
    }

    @Test
    void allowsOwnerEvenWhenClaimFlagDeniesStrangers() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim claim = claim(ownerId, worldId, Set.of(new ClaimChunk(worldId, 0, 0)), Map.of("build", FlagState.OFF));
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.add(claim);
        BukkitLandClaimsApi api = api(new FakeClaimRepository(List.of(claim)), claimIndex);

        boolean allowed = api.canBuild(player(ownerId), location(worldId, 0, 0));

        assertThat(allowed).isTrue();
    }

    @Test
    void bypassPermissionAllowsInteraction() {
        UUID worldId = UUID.randomUUID();
        Claim claim = claim(UUID.randomUUID(), worldId, Set.of(new ClaimChunk(worldId, 0, 0)), Map.of("build", FlagState.OFF));
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.add(claim);
        Player player = player(UUID.randomUUID());
        when(player.hasPermission("landclaims.bypass.protection")).thenReturn(true);
        BukkitLandClaimsApi api = api(new FakeClaimRepository(List.of(claim)), claimIndex);

        boolean allowed = api.canBuild(player, location(worldId, 0, 0));

        assertThat(allowed).isTrue();
    }

    @Test
    void returnsRepositoryBackedClaimQueries() {
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim claim = claim(ownerId, worldId, Set.of(new ClaimChunk(worldId, 1, 1)), Map.of());
        FakeClaimRepository repository = new FakeClaimRepository(List.of(claim));
        BukkitLandClaimsApi api = api(repository, new ClaimIndex());

        assertThat(api.getClaimById(claim.id())).contains(claim);
        assertThat(api.getClaimsOwnedBy(ownerId)).containsExactly(claim);
    }

    private static BukkitLandClaimsApi api(ClaimRepository repository, ClaimIndex claimIndex) {
        return new BukkitLandClaimsApi(repository, claimIndex, new ProtectionService(FlagRegistry.createDefault()));
    }

    private static Player player(UUID playerId) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        return player;
    }

    private static Location location(UUID worldId, int chunkX, int chunkZ) {
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getX()).thenReturn(chunkX);
        when(chunk.getZ()).thenReturn(chunkZ);
        Location location = mock(Location.class);
        when(location.getWorld()).thenReturn(world);
        when(location.getChunk()).thenReturn(chunk);
        return location;
    }

    private static Claim claim(UUID ownerId, UUID worldId, Set<ClaimChunk> chunks, Map<String, FlagState> flags) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new Claim(
                UUID.randomUUID(),
                "Home",
                OwnerType.PLAYER,
                ownerId,
                worldId,
                chunks,
                flags,
                now,
                now
        );
    }

    private static final class FakeClaimRepository implements ClaimRepository {
        private final List<Claim> claims;

        private FakeClaimRepository(List<Claim> claims) {
            this.claims = new ArrayList<>(claims);
        }

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
            return claims.stream()
                    .filter(claim -> claim.claimChunks().contains(new ClaimChunk(worldId, chunkX, chunkZ)))
                    .findFirst();
        }

        @Override
        public Optional<Claim> findClaimById(UUID claimId) {
            return claims.stream().filter(claim -> claim.id().equals(claimId)).findFirst();
        }

        @Override
        public List<Claim> findClaimsByOwner(OwnerType ownerType, UUID ownerUuid) {
            return claims.stream()
                    .filter(claim -> claim.owner() == ownerType)
                    .filter(claim -> ownerUuid.equals(claim.ownerUuid()))
                    .toList();
        }

        @Override
        public List<Claim> findAllClaims() {
            return List.copyOf(claims);
        }
    }
}
