package com.nick.landclaims.plugin.flag;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimFlagServiceTest {
    @Test
    void ownerCanSetEditableFlagWhenPermissionAllowsIt() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimFlagService service = new ClaimFlagService(repository, claimIndex, FlagRegistry.createDefault());
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim claim = claim(ownerId, worldId, Map.of("build", false));
        repository.claims.add(claim);
        claimIndex.add(claim);

        ClaimFlagResult result = service.setFlag(
                ownerId,
                claim,
                "build",
                true,
                permission -> permission.equals("landclaims.flag.build")
        );

        assertThat(result.allowed()).isTrue();
        Claim updated = repository.claims.get(0);
        assertThat(updated.flags()).containsEntry("build", true);
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).contains(updated);
    }

    @Test
    void ownerCanToggleEditableFlagWhenPermissionAllowsIt() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimFlagService service = new ClaimFlagService(repository, claimIndex, FlagRegistry.createDefault());
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim claim = claim(ownerId, worldId, Map.of("build", true));
        repository.claims.add(claim);
        claimIndex.add(claim);

        ClaimFlagResult result = service.toggleFlag(
                ownerId,
                claim,
                "build",
                permission -> permission.equals("landclaims.flag.build")
        );

        assertThat(result.allowed()).isTrue();
        Claim updated = repository.claims.get(0);
        assertThat(updated.flags()).containsEntry("build", false);
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).contains(updated);
    }

    @Test
    void nonOwnerCannotSetFlag() {
        ClaimFlagService service = new ClaimFlagService(new FakeClaimRepository(), new ClaimIndex(), FlagRegistry.createDefault());
        Claim claim = claim(UUID.randomUUID(), UUID.randomUUID(), Map.of("build", false));

        ClaimFlagResult result = service.setFlag(UUID.randomUUID(), claim, "build", true, permission -> true);

        assertThat(result.allowed()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claim.flag.not-owner");
    }

    @Test
    void unknownFlagIsDenied() {
        ClaimFlagService service = new ClaimFlagService(new FakeClaimRepository(), new ClaimIndex(), FlagRegistry.createDefault());
        UUID ownerId = UUID.randomUUID();
        Claim claim = claim(ownerId, UUID.randomUUID(), Map.of());

        ClaimFlagResult result = service.setFlag(ownerId, claim, "unknown", true, permission -> true);

        assertThat(result.allowed()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claim.flag.unknown");
    }

    @Test
    void missingEditPermissionIsDenied() {
        ClaimFlagService service = new ClaimFlagService(new FakeClaimRepository(), new ClaimIndex(), FlagRegistry.createDefault());
        UUID ownerId = UUID.randomUUID();
        Claim claim = claim(ownerId, UUID.randomUUID(), Map.of("build", false));

        ClaimFlagResult result = service.setFlag(ownerId, claim, "build", true, permission -> false);

        assertThat(result.allowed()).isFalse();
        assertThat(result.messageKey()).isEqualTo("claim.flag.no-permission");
    }

    @Test
    void flagRowsIncludeRegistryDefaultsWhenClaimDoesNotStoreValue() {
        ClaimFlagService service = new ClaimFlagService(new FakeClaimRepository(), new ClaimIndex(), FlagRegistry.createDefault());
        Claim claim = claim(UUID.randomUUID(), UUID.randomUUID(), Map.of("build", true));

        List<ClaimFlagRow> rows = service.listFlags(claim);

        assertThat(rows)
                .anySatisfy(row -> {
                    assertThat(row.key()).isEqualTo("build");
                    assertThat(row.enabled()).isTrue();
                    assertThat(row.category()).isEqualTo("access");
                })
                .anySatisfy(row -> {
                    assertThat(row.key()).isEqualTo("piston_protection");
                    assertThat(row.enabled()).isTrue();
                    assertThat(row.category()).isEqualTo("protection");
                });
    }

    private static Claim claim(UUID ownerId, UUID worldId, Map<String, Boolean> flags) {
        Instant now = Instant.parse("2026-06-08T00:00:00Z");
        return new Claim(
                UUID.randomUUID(),
                "Home",
                OwnerType.PLAYER,
                ownerId,
                worldId,
                Set.of(new ClaimChunk(worldId, 0, 0)),
                flags,
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
            return Optional.empty();
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
