package com.nick.landclaims.plugin.claim;

import com.nick.landclaims.plugin.flag.FlagRegistry;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
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
    private final int playerBufferDistance;
    private final int adminBufferDistance;
    private final int maxClaimNameLength;

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

    public ClaimValidationResult createPlayerClaim(UUID ownerUuid, String name, Set<ClaimChunk> chunks) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(chunks, "chunks");

        String trimmedName = name.trim();
        ClaimValidationResult validationResult = validatePlayerClaim(ownerUuid, trimmedName, chunks);
        if (!validationResult.isAllowed()) {
            return validationResult;
        }

        Instant now = Instant.now();
        Claim claim = new Claim(
                UUID.randomUUID(),
                trimmedName,
                OwnerType.PLAYER,
                ownerUuid,
                chunks.iterator().next().worldId(),
                chunks,
                defaultFlags(),
                now,
                now
        );
        claimRepository.saveClaim(claim);
        claimIndex.add(claim);
        return ClaimValidationResult.allowed();
    }

    public ClaimValidationResult validatePlayerClaim(UUID ownerUuid, String name, Set<ClaimChunk> chunks) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(chunks, "chunks");

        String trimmedName = name.trim();
        if (trimmedName.isEmpty() || trimmedName.length() > maxClaimNameLength) {
            return ClaimValidationResult.denied("claims.invalid-name");
        }
        if (chunks.isEmpty()) {
            return ClaimValidationResult.denied("claims.empty-selection");
        }
        if (chunks.stream().anyMatch(chunk -> claimIndex.findAt(chunk).isPresent())) {
            return ClaimValidationResult.denied("claims.overlap");
        }

        for (ClaimChunk proposedChunk : chunks) {
            for (Claim existingClaim : claimIndex.findAll()) {
                ClaimValidationResult bufferResult = validateBuffer(ownerUuid, proposedChunk, existingClaim);
                if (!bufferResult.isAllowed()) {
                    return bufferResult;
                }
            }
        }

        return ClaimValidationResult.allowed();
    }

    private ClaimValidationResult validateBuffer(UUID ownerUuid, ClaimChunk proposedChunk, Claim existingClaim) {
        int bufferDistance = existingClaim.owner() == OwnerType.ADMIN ? adminBufferDistance : playerBufferDistance;
        if (existingClaim.owner() == OwnerType.PLAYER && ownerUuid.equals(existingClaim.ownerUuid())) {
            return ClaimValidationResult.allowed();
        }

        boolean insideBuffer = existingClaim.claimChunks().stream()
                .anyMatch(existingChunk -> claimService.isWithinChunkBuffer(proposedChunk, existingChunk, bufferDistance));
        if (!insideBuffer) {
            return ClaimValidationResult.allowed();
        }

        if (existingClaim.owner() == OwnerType.ADMIN) {
            return ClaimValidationResult.denied("claims.too-close-admin");
        }
        return ClaimValidationResult.denied("claims.too-close");
    }

    private Map<String, Boolean> defaultFlags() {
        return flagRegistry.keys().stream()
                .collect(Collectors.toUnmodifiableMap(key -> key, flagRegistry::defaultValue));
    }
}
