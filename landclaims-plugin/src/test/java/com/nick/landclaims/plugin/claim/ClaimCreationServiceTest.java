package com.nick.landclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.plugin.flag.FlagRegistry;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimCreationServiceTest {
    @Test
    void createsPlayerClaimWithLockedDefaultFlags() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimCreationService service = service(repository, claimIndex);
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Set<ClaimChunk> chunks = Set.of(new ClaimChunk(worldId, 0, 0));

        ClaimValidationResult result = service.createPlayerClaim(ownerId, "Home", chunks);

        assertThat(result.isAllowed()).isTrue();
        assertThat(repository.savedClaims).hasSize(1);
        Claim saved = repository.savedClaims.get(0);
        assertThat(saved.name()).isEqualTo("Home");
        assertThat(saved.owner()).isEqualTo(OwnerType.PLAYER);
        assertThat(saved.ownerUuid()).isEqualTo(ownerId);
        assertThat(saved.flags()).containsEntry("build", false).containsEntry("break", false);
        assertThat(claimIndex.findAt(new ClaimChunk(worldId, 0, 0))).contains(saved);
    }

    @Test
    void rejectsInvalidName() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimCreationService service = service(repository, claimIndex);

        ClaimValidationResult result = service.createPlayerClaim(
                UUID.randomUUID(),
                "   ",
                Set.of(new ClaimChunk(UUID.randomUUID(), 0, 0))
        );

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.messageKey()).contains("claims.invalid-name");
        assertThat(repository.savedClaims).isEmpty();
    }

    @Test
    void rejectsOverlappingChunk() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        UUID worldId = UUID.randomUUID();
        ClaimChunk chunk = new ClaimChunk(worldId, 0, 0);
        claimIndex.add(existingClaim(UUID.randomUUID(), worldId, Set.of(chunk), OwnerType.PLAYER));
        ClaimCreationService service = service(repository, claimIndex);

        ClaimValidationResult result = service.createPlayerClaim(UUID.randomUUID(), "Home", Set.of(chunk));

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.messageKey()).contains("claims.overlap");
        assertThat(repository.savedClaims).isEmpty();
    }

    @Test
    void rejectsPlayerClaimInsideOtherPlayerBuffer() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        UUID worldId = UUID.randomUUID();
        claimIndex.add(existingClaim(UUID.randomUUID(), worldId, Set.of(new ClaimChunk(worldId, 0, 0)), OwnerType.PLAYER));
        ClaimCreationService service = service(repository, claimIndex);

        ClaimValidationResult result = service.createPlayerClaim(
                UUID.randomUUID(),
                "Home",
                Set.of(new ClaimChunk(worldId, 3, 0))
        );

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.messageKey()).contains("claims.too-close");
        assertThat(repository.savedClaims).isEmpty();
    }

    @Test
    void rejectsPlayerClaimInsideAdminBuffer() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        UUID worldId = UUID.randomUUID();
        claimIndex.add(existingClaim(null, worldId, Set.of(new ClaimChunk(worldId, 0, 0)), OwnerType.ADMIN));
        ClaimCreationService service = service(repository, claimIndex);

        ClaimValidationResult result = service.createPlayerClaim(
                UUID.randomUUID(),
                "Home",
                Set.of(new ClaimChunk(worldId, 3, 0))
        );

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.messageKey()).contains("claims.too-close-admin");
        assertThat(repository.savedClaims).isEmpty();
    }

    private static ClaimCreationService service(FakeClaimRepository repository, ClaimIndex claimIndex) {
        return new ClaimCreationService(
                repository,
                claimIndex,
                new ClaimService(),
                FlagRegistry.createDefault(),
                3,
                3,
                32
        );
    }

    private static Claim existingClaim(UUID ownerId, UUID worldId, Set<ClaimChunk> chunks, OwnerType ownerType) {
        java.time.Instant now = java.time.Instant.parse("2026-06-07T00:00:00Z");
        return new Claim(UUID.randomUUID(), "Existing", ownerType, ownerId, worldId, chunks, java.util.Map.of("build", false), now, now);
    }

    private static final class FakeClaimRepository implements ClaimRepository {
        private final List<Claim> savedClaims = new ArrayList<>();

        @Override
        public void initialize() {
        }

        @Override
        public void saveClaim(Claim claim) {
            savedClaims.add(claim);
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
            return List.of();
        }
    }
}
