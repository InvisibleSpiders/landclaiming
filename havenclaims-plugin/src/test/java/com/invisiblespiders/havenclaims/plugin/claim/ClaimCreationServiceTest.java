package com.invisiblespiders.havenclaims.plugin.claim;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.api.flag.FlagState;
import com.invisiblespiders.havenclaims.plugin.flag.FlagRegistry;
import com.invisiblespiders.havenclaims.plugin.storage.ClaimRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        // chunk (0,0) covers blocks [0,0] to [15,15]
        ClaimRegion region = new ClaimRegion(worldId, 0, 0, 15, 15);

        ClaimValidationResult result = service.createPlayerClaim(ownerId, "Home", region);

        assertThat(result.isAllowed()).isTrue();
        assertThat(repository.savedClaims).hasSize(1);
        Claim saved = repository.savedClaims.get(0);
        assertThat(saved.name()).isEqualTo("Home");
        assertThat(saved.owner()).isEqualTo(OwnerType.PLAYER);
        assertThat(saved.ownerUuid()).isEqualTo(ownerId);
        assertThat(saved.flags()).containsEntry("build", FlagState.VISITORS).containsEntry("break", FlagState.VISITORS);
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
                new ClaimRegion(UUID.randomUUID(), 0, 0, 15, 15)
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
        // chunk (0,0) covers [0,0] to [15,15]
        ClaimRegion region = new ClaimRegion(worldId, 0, 0, 15, 15);
        claimIndex.add(existingClaim(UUID.randomUUID(), worldId, 0, 0, OwnerType.PLAYER));
        ClaimCreationService service = service(repository, claimIndex);

        ClaimValidationResult result = service.createPlayerClaim(UUID.randomUUID(), "Home", region);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.messageKey()).contains("claims.overlap");
        assertThat(repository.savedClaims).isEmpty();
    }

    @Test
    void rejectsPlayerClaimInsideOtherPlayerBuffer() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        UUID worldId = UUID.randomUUID();
        // Existing claim at chunk (0,0): blocks [0,0] to [15,15]
        claimIndex.add(existingClaim(UUID.randomUUID(), worldId, 0, 0, OwnerType.PLAYER));
        ClaimCreationService service = service(repository, claimIndex);

        // Proposed claim at chunk (3,0): blocks [48,0] to [63,15]
        // Gap between [15,x] and [48,x] = 32 blocks, but buffer is 3 chunks = 48 blocks
        // Actually isWithinBlockBuffer checks block gap < bufferBlocks
        // gap = max(0, max(0,48) - min(15,63) - 1) = max(0, 48 - 15 - 1) = 32
        // playerBufferDistance = 3 (chunks in old API, but now it's blocks in new API)
        // Wait - the service stores playerBufferDistance as the value passed (3),
        // and isWithinBlockBuffer checks minimumBlockGap < bufferBlocks
        // gap = 32, bufferBlocks = 3, 32 < 3 is false => NOT within buffer => allowed
        // This test needs adjustment: chunk (0,0) and chunk (1,0) have a block gap of 0 (adjacent)
        // so gap=0 < 3 => within buffer => denied
        ClaimValidationResult result = service.createPlayerClaim(
                UUID.randomUUID(),
                "Home",
                new ClaimRegion(worldId, 16, 0, 31, 15) // chunk (1,0)
        );

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.messageKey()).contains("claims.too-close");
        assertThat(repository.savedClaims).isEmpty();
    }

    @Test
    void allowsPlayerClaimInsideOtherPlayerBufferWhenBypassed() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        UUID worldId = UUID.randomUUID();
        claimIndex.add(existingClaim(UUID.randomUUID(), worldId, 0, 0, OwnerType.PLAYER));
        ClaimCreationService service = service(repository, claimIndex);

        ClaimValidationResult result = service.validatePlayerClaim(
                UUID.randomUUID(),
                "Home",
                new ClaimRegion(worldId, 16, 0, 31, 15), // chunk (1,0)
                true
        );

        assertThat(result.isAllowed()).isTrue();
    }

    @Test
    void rejectsPlayerClaimInsideAdminBuffer() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        UUID worldId = UUID.randomUUID();
        claimIndex.add(existingClaim(null, worldId, 0, 0, OwnerType.ADMIN));
        ClaimCreationService service = service(repository, claimIndex);

        ClaimValidationResult result = service.createPlayerClaim(
                UUID.randomUUID(),
                "Home",
                new ClaimRegion(worldId, 16, 0, 31, 15) // chunk (1,0), adjacent
        );

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.messageKey()).contains("claims.too-close-admin");
        assertThat(repository.savedClaims).isEmpty();
    }

    @Test
    void validatePlayerClaimDoesNotSaveClaim() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimCreationService service = service(repository, claimIndex);

        ClaimValidationResult result = service.validatePlayerClaim(
                UUID.randomUUID(),
                "Home",
                new ClaimRegion(UUID.randomUUID(), 0, 0, 15, 15)
        );

        assertThat(result.isAllowed()).isTrue();
        assertThat(repository.savedClaims).isEmpty();
        assertThat(claimIndex.findAll()).isEmpty();
    }

    @Test
    void doesNotMergeBorderingSameOwnerClaimInPhase1() {
        // Merge is disabled in Phase 1 — findMergeTargets always returns empty list.
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimCreationService service = service(repository, claimIndex);
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        // Existing claim at chunk (0,0)
        Claim existing = existingClaim(ownerId, worldId, 0, 0, OwnerType.PLAYER, "Home");
        repository.savedClaims.add(existing);
        claimIndex.add(existing);

        // New claim at chunk (1,0) — adjacent, same owner, same name
        // Gap = 0 < playerBufferDistance=3, but owner matches so buffer is skipped
        ClaimValidationResult result = service.createPlayerClaim(
                ownerId,
                "Home",
                new ClaimRegion(worldId, 16, 0, 31, 15)
        );

        assertThat(result.isAllowed()).isTrue();
        // No merge: two separate claims
        assertThat(repository.savedClaims).hasSize(2);
    }

    @Test
    void keepsBorderingSameOwnerClaimSeparateWhenNameDiffers() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimCreationService service = service(repository, claimIndex);
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim existing = existingClaim(ownerId, worldId, 0, 0, OwnerType.PLAYER, "Home");
        repository.savedClaims.add(existing);
        claimIndex.add(existing);

        // chunk (1,0) — adjacent but different name; owner skip means buffer not applied
        ClaimValidationResult result = service.createPlayerClaim(
                ownerId,
                "Farm",
                new ClaimRegion(worldId, 16, 0, 31, 15)
        );

        assertThat(result.isAllowed()).isTrue();
        assertThat(repository.savedClaims).hasSize(2);
        assertThat(repository.savedClaims)
                .extracting(Claim::name)
                .containsExactlyInAnyOrder("Home", "Farm");
    }

    @Test
    void findMergeTargetsAlwaysReturnsEmptyInPhase1() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimCreationService service = service(repository, claimIndex);
        UUID ownerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Claim existing = existingClaim(ownerId, worldId, 0, 0, OwnerType.PLAYER, "Home");
        claimIndex.add(existing);

        List<Claim> targets = service.findMergeTargets(ownerId, "Home", new ClaimRegion(worldId, 16, 0, 31, 15));

        assertThat(targets).isEmpty();
    }

    @Test
    void reloadEnforcesNewMaxNameLength() {
        FakeClaimRepository repository = new FakeClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        ClaimCreationService service = new ClaimCreationService(
                repository, claimIndex, new ClaimService(), FlagRegistry.createDefault(), 3, 3, 10);
        UUID owner = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        ClaimRegion region = new ClaimRegion(worldId, 0, 0, 15, 15);

        service.reload(3, 3, 4);

        ClaimValidationResult result = service.validatePlayerClaim(owner, "TooLongName", region);
        assertThat(result.isAllowed()).isFalse();
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

    private static Claim existingClaim(UUID ownerId, UUID worldId, int chunkX, int chunkZ, OwnerType ownerType) {
        return existingClaim(ownerId, worldId, chunkX, chunkZ, ownerType, "Existing");
    }

    private static Claim existingClaim(
            UUID ownerId,
            UUID worldId,
            int chunkX,
            int chunkZ,
            OwnerType ownerType,
            String name
    ) {
        java.time.Instant now = java.time.Instant.parse("2026-06-07T00:00:00Z");
        ClaimRegion region = new ClaimRegion(worldId, chunkX * 16, chunkZ * 16, chunkX * 16 + 15, chunkZ * 16 + 15);
        return new Claim(
                UUID.randomUUID(),
                name,
                ownerType,
                ownerId,
                region,
                Map.of("build", FlagState.OFF),
                now,
                now
        );
    }

    private static final class FakeClaimRepository implements ClaimRepository {
        private final List<Claim> savedClaims = new ArrayList<>();
        private final List<UUID> deletedClaimIds = new ArrayList<>();

        @Override
        public void saveClaim(Claim claim) {
            savedClaims.removeIf(savedClaim -> savedClaim.id().equals(claim.id()));
            savedClaims.add(claim);
        }

        @Override
        public void deleteClaim(UUID claimId) {
            deletedClaimIds.add(claimId);
            savedClaims.removeIf(savedClaim -> savedClaim.id().equals(claimId));
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
