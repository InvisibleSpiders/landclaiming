package com.invisiblespiders.havenclaims.plugin.claim;

import com.invisiblespiders.havenclaims.api.flag.FlagState;
import com.invisiblespiders.havenclaims.plugin.flag.FlagRegistry;
import com.invisiblespiders.havenclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ClaimCreationService {
    private final ClaimRepository claimRepository;
    private final ClaimIndex claimIndex;
    private final ClaimService claimService;
    private final FlagRegistry flagRegistry;
    private int playerBufferDistance;
    private int adminBufferDistance;
    private int maxClaimNameLength;

    public ClaimCreationService(
            ClaimRepository claimRepository,
            ClaimIndex claimIndex,
            ClaimService claimService,
            FlagRegistry flagRegistry,
            int playerBufferDistance,
            int adminBufferDistance,
            int maxClaimNameLength
    ) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
        this.claimService = Objects.requireNonNull(claimService, "claimService");
        this.flagRegistry = Objects.requireNonNull(flagRegistry, "flagRegistry");
        if (playerBufferDistance < 0 || adminBufferDistance < 0) {
            throw new IllegalArgumentException("buffer distances must be non-negative");
        }
        if (maxClaimNameLength < 1) {
            throw new IllegalArgumentException("maxClaimNameLength must be at least 1");
        }
        this.playerBufferDistance = playerBufferDistance;
        this.adminBufferDistance = adminBufferDistance;
        this.maxClaimNameLength = maxClaimNameLength;
    }

    public void reload(int newPlayerBufferDistance, int newAdminBufferDistance, int newMaxNameLength) {
        if (newPlayerBufferDistance < 0 || newAdminBufferDistance < 0) {
            throw new IllegalArgumentException("buffer distances must be non-negative");
        }
        if (newMaxNameLength < 1) {
            throw new IllegalArgumentException("maxClaimNameLength must be at least 1");
        }
        this.playerBufferDistance = newPlayerBufferDistance;
        this.adminBufferDistance = newAdminBufferDistance;
        this.maxClaimNameLength = newMaxNameLength;
    }

    public ClaimValidationResult createPlayerClaim(UUID ownerUuid, String name, ClaimRegion region) {
        return createPlayerClaim(ownerUuid, name, region, false);
    }

    public ClaimValidationResult createPlayerClaim(UUID ownerUuid, String name, ClaimRegion region, boolean bypassBuffer) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(region, "region");

        String trimmedName = name.trim();
        ClaimValidationResult validation = validatePlayerClaim(ownerUuid, trimmedName, region, bypassBuffer);
        if (!validation.isAllowed()) {
            return validation;
        }

        Instant now = Instant.now();
        Claim claim = new Claim(UUID.randomUUID(), trimmedName, OwnerType.PLAYER, ownerUuid,
                region, defaultFlags(), now, now);
        claimRepository.saveClaim(claim);
        claimIndex.add(claim);
        return ClaimValidationResult.allowed();
    }

    public ClaimValidationResult validatePlayerClaim(UUID ownerUuid, String name, ClaimRegion region) {
        return validatePlayerClaim(ownerUuid, name, region, false);
    }

    public ClaimValidationResult validatePlayerClaim(UUID ownerUuid, String name, ClaimRegion region, boolean bypassBuffer) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(region, "region");

        String trimmedName = name.trim();
        if (trimmedName.isEmpty() || trimmedName.length() > maxClaimNameLength) {
            return ClaimValidationResult.denied("claims.invalid-name");
        }

        for (ClaimChunk chunk : region.overlappingChunks()) {
            if (claimIndex.findAt(chunk).isPresent()) {
                return ClaimValidationResult.denied("claims.overlap");
            }
        }

        if (!bypassBuffer) {
            List<Claim> allClaims = claimIndex.findAll();
            for (Claim existingClaim : allClaims) {
                ClaimValidationResult bufferResult = validateBlockBuffer(ownerUuid, region, existingClaim);
                if (!bufferResult.isAllowed()) {
                    return bufferResult;
                }
            }
        }

        return ClaimValidationResult.allowed();
    }

    private ClaimValidationResult validateBlockBuffer(UUID ownerUuid, ClaimRegion proposed, Claim existingClaim) {
        if (existingClaim.owner() == OwnerType.PLAYER && ownerUuid.equals(existingClaim.ownerUuid())) {
            return ClaimValidationResult.allowed();
        }
        int bufferBlocks = existingClaim.owner() == OwnerType.ADMIN ? adminBufferDistance : playerBufferDistance;
        if (!claimService.isWithinBlockBuffer(proposed, existingClaim.region(), bufferBlocks)) {
            return ClaimValidationResult.allowed();
        }
        if (existingClaim.owner() == OwnerType.ADMIN) {
            return ClaimValidationResult.denied("claims.too-close-admin");
        }
        return ClaimValidationResult.denied("claims.too-close");
    }

    /** Merge targets always empty in Phase 1. */
    public List<Claim> findMergeTargets(UUID ownerUuid, String name, ClaimRegion region) {
        return List.of();
    }

    /** Backward-compat shim — removed in Task 14. */
    public ClaimValidationResult createPlayerClaim(UUID ownerUuid, String name, Set<ClaimChunk> chunks,
            List<Claim> mergeTargets, boolean bypassBuffer) {
        return createPlayerClaim(ownerUuid, name, chunksToRegion(chunks), bypassBuffer);
    }

    /** Backward-compat shim for callers that pass Set<ClaimChunk> — removed in Task 14. */
    public ClaimValidationResult validatePlayerClaim(UUID ownerUuid, String name, Set<ClaimChunk> chunks) {
        return validatePlayerClaim(ownerUuid, name, chunksToRegion(chunks), false);
    }

    /** Backward-compat shim for callers that pass Set<ClaimChunk> — removed in Task 14. */
    public ClaimValidationResult validatePlayerClaim(UUID ownerUuid, String name, Set<ClaimChunk> chunks, boolean bypassBuffer) {
        return validatePlayerClaim(ownerUuid, name, chunksToRegion(chunks), bypassBuffer);
    }

    /** Backward-compat shim for callers that pass Set<ClaimChunk> — removed in Task 14. */
    public List<Claim> findMergeTargets(UUID ownerUuid, String name, Set<ClaimChunk> chunks) {
        return List.of();
    }

    // Bounding-box approximation — callers must only pass rectangular chunk sets.
    private static ClaimRegion chunksToRegion(Set<ClaimChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("chunks must not be null or empty");
        }
        UUID worldId = chunks.iterator().next().worldId();
        int minX = chunks.stream().mapToInt(c -> c.chunkX() * 16).min().getAsInt();
        int minZ = chunks.stream().mapToInt(c -> c.chunkZ() * 16).min().getAsInt();
        int maxX = chunks.stream().mapToInt(c -> c.chunkX() * 16 + 15).max().getAsInt();
        int maxZ = chunks.stream().mapToInt(c -> c.chunkZ() * 16 + 15).max().getAsInt();
        return new ClaimRegion(worldId, minX, minZ, maxX, maxZ);
    }

    private Map<String, FlagState> defaultFlags() {
        return flagRegistry.keys().stream()
                .collect(Collectors.toUnmodifiableMap(key -> key, flagRegistry::defaultState));
    }
}
