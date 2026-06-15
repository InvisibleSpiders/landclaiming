package com.invisiblespiders.havenclaims.plugin.claim;

import com.invisiblespiders.havenclaims.api.flag.FlagState;
import com.invisiblespiders.havenclaims.plugin.flag.FlagRegistry;
import com.invisiblespiders.havenclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

    public ClaimValidationResult createPlayerClaim(UUID ownerUuid, String name, Set<ClaimChunk> chunks) {
        return createPlayerClaim(ownerUuid, name, chunks, false);
    }

    public ClaimValidationResult createPlayerClaim(UUID ownerUuid, String name, Set<ClaimChunk> chunks, boolean bypassBuffer) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(chunks, "chunks");

        String trimmedName = name.trim();
        ClaimValidationResult validationResult = validatePlayerClaim(ownerUuid, trimmedName, chunks, bypassBuffer);
        if (!validationResult.isAllowed()) {
            return validationResult;
        }

        return persistClaim(ownerUuid, trimmedName, chunks, findMergeTargets(ownerUuid, trimmedName, chunks));
    }

    // Accepts pre-computed merge targets to avoid a redundant findMergeTargets() scan when the
    // caller already has them (e.g. to decide whether to show a merge-confirmation prompt).
    public ClaimValidationResult createPlayerClaim(
            UUID ownerUuid, String name, Set<ClaimChunk> chunks, List<Claim> mergeTargets) {
        return createPlayerClaim(ownerUuid, name, chunks, mergeTargets, false);
    }

    public ClaimValidationResult createPlayerClaim(
            UUID ownerUuid, String name, Set<ClaimChunk> chunks, List<Claim> mergeTargets, boolean bypassBuffer) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(mergeTargets, "mergeTargets");

        String trimmedName = name.trim();
        ClaimValidationResult validationResult = validatePlayerClaim(ownerUuid, trimmedName, chunks, bypassBuffer);
        if (!validationResult.isAllowed()) {
            return validationResult;
        }

        return persistClaim(ownerUuid, trimmedName, chunks, mergeTargets);
    }

    private ClaimValidationResult persistClaim(
            UUID ownerUuid, String trimmedName, Set<ClaimChunk> chunks, List<Claim> mergeTargets) {
        Instant now = Instant.now();
        if (!mergeTargets.isEmpty()) {
            Claim existingClaim = mergeTargets.get(0);
            Set<ClaimChunk> mergedChunks = new HashSet<>();
            // Union flags, members, and deniedPlayers from ALL targets so nothing is silently dropped.
            Map<String, FlagState> mergedFlags = new HashMap<>(defaultFlags());
            Set<ClaimMember> mergedMembers = new HashSet<>();
            Set<UUID> mergedDeniedPlayers = new HashSet<>();
            for (Claim mergeTarget : mergeTargets) {
                mergedChunks.addAll(mergeTarget.claimChunks());
                mergedFlags.putAll(mergeTarget.flags());
                mergedMembers.addAll(mergeTarget.members());
                mergedDeniedPlayers.addAll(mergeTarget.deniedPlayers());
            }
            mergedChunks.addAll(chunks);
            Claim mergedClaim = new Claim(
                    existingClaim.id(),
                    trimmedName,
                    existingClaim.owner(),
                    existingClaim.ownerUuid(),
                    existingClaim.worldId(),
                    mergedChunks,
                    mergedFlags,
                    mergedMembers,
                    mergedDeniedPlayers,
                    existingClaim.createdAt(),
                    now
            );
            List<UUID> redundantClaimIds = mergeTargets.stream()
                    .skip(1)
                    .map(Claim::id)
                    .toList();
            claimRepository.replaceClaims(mergedClaim, redundantClaimIds);
            for (UUID redundantClaimId : redundantClaimIds) {
                claimIndex.remove(redundantClaimId);
            }
            claimIndex.replace(mergedClaim);
            return ClaimValidationResult.allowed();
        }

        UUID claimWorldId = chunks.iterator().next().worldId();
        if (chunks.stream().anyMatch(c -> !c.worldId().equals(claimWorldId))) {
            throw new IllegalArgumentException("All selected chunks must belong to the same world");
        }
        Claim claim = new Claim(
                UUID.randomUUID(),
                trimmedName,
                OwnerType.PLAYER,
                ownerUuid,
                claimWorldId,
                chunks,
                defaultFlags(),
                now,
                now
        );
        claimRepository.saveClaim(claim);
        claimIndex.add(claim);
        return ClaimValidationResult.allowed();
    }

    public List<Claim> findMergeTargets(UUID ownerUuid, String name, Set<ClaimChunk> chunks) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(chunks, "chunks");

        return claimIndex.findAll().stream()
                .filter(claim -> claim.owner() == OwnerType.PLAYER)
                .filter(claim -> ownerUuid.equals(claim.ownerUuid()))
                .filter(claim -> claim.name().equalsIgnoreCase(name.trim()))
                .filter(claim -> chunks.stream().anyMatch(chunk -> bordersClaim(chunk, claim)))
                .sorted(Comparator
                        .comparingInt(this::minimumChunkX)
                        .thenComparingInt(this::minimumChunkZ)
                        .thenComparing(claim -> claim.id().toString()))
                .toList();
    }

    private int minimumChunkX(Claim claim) {
        return claim.claimChunks().stream()
                .mapToInt(ClaimChunk::chunkX)
                .min()
                .orElse(0);
    }

    private int minimumChunkZ(Claim claim) {
        return claim.claimChunks().stream()
                .mapToInt(ClaimChunk::chunkZ)
                .min()
                .orElse(0);
    }

    private boolean bordersClaim(ClaimChunk proposedChunk, Claim claim) {
        return claim.claimChunks().stream().anyMatch(existingChunk ->
                proposedChunk.worldId().equals(existingChunk.worldId())
                        && manhattanDistance(proposedChunk, existingChunk) == 1
        );
    }

    private int manhattanDistance(ClaimChunk first, ClaimChunk second) {
        return Math.abs(first.chunkX() - second.chunkX()) + Math.abs(first.chunkZ() - second.chunkZ());
    }

    public ClaimValidationResult validatePlayerClaim(UUID ownerUuid, String name, Set<ClaimChunk> chunks) {
        return validatePlayerClaim(ownerUuid, name, chunks, false);
    }

    public ClaimValidationResult validatePlayerClaim(UUID ownerUuid, String name, Set<ClaimChunk> chunks, boolean bypassBuffer) {
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

        // Snapshot once — findAll() rebuilds the distinct list, so calling it inside the loop
        // would re-scan the entire chunk map on every proposed-chunk iteration.
        if (!bypassBuffer) {
            List<Claim> allClaims = claimIndex.findAll();
            for (ClaimChunk proposedChunk : chunks) {
                for (Claim existingClaim : allClaims) {
                    ClaimValidationResult bufferResult = validateBuffer(ownerUuid, proposedChunk, existingClaim);
                    if (!bufferResult.isAllowed()) {
                        return bufferResult;
                    }
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

    private Map<String, FlagState> defaultFlags() {
        return flagRegistry.keys().stream()
                .collect(Collectors.toUnmodifiableMap(key -> key, flagRegistry::defaultState));
    }
}
