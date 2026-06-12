package com.nick.landclaims.plugin.admin;

import com.nick.landclaims.api.flag.FlagState;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class AdminClaimService {
    private final ClaimRepository claimRepository;
    private final ClaimIndex claimIndex;
    private final FlagRegistry flagRegistry;
    private final int maxClaimNameLength;

    public AdminClaimService() {
        this(null, null, null, 32);
    }

    public AdminClaimService(
            ClaimRepository claimRepository,
            ClaimIndex claimIndex,
            FlagRegistry flagRegistry,
            int maxClaimNameLength
    ) {
        if (maxClaimNameLength < 1) {
            throw new IllegalArgumentException("maxClaimNameLength must be at least 1");
        }
        this.claimRepository = claimRepository;
        this.claimIndex = claimIndex;
        this.flagRegistry = flagRegistry;
        this.maxClaimNameLength = maxClaimNameLength;
    }

    public AdminClaimResult createAdminClaim(String name, Set<ClaimChunk> chunks) {
        requireStorage();
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(chunks, "chunks");

        String trimmedName = name.trim();
        if (trimmedName.isEmpty() || trimmedName.length() > maxClaimNameLength) {
            return AdminClaimResult.denied("admin.claim.invalid-name");
        }
        if (chunks.isEmpty()) {
            return AdminClaimResult.denied("admin.claim.empty-selection");
        }
        if (chunks.stream().anyMatch(chunk -> claimIndex.findAt(chunk).isPresent())) {
            return AdminClaimResult.denied("admin.claim.overlap");
        }

        Instant now = Instant.now();
        Claim claim = new Claim(
                UUID.randomUUID(),
                trimmedName,
                OwnerType.ADMIN,
                null,
                chunks.iterator().next().worldId(),
                chunks,
                defaultFlags(),
                now,
                now
        );
        claimRepository.saveClaim(claim);
        claimIndex.add(claim);
        return AdminClaimResult.success(claim);
    }

    public AdminClaimResult deleteAdminClaim(UUID claimId) {
        requireStorage();
        Objects.requireNonNull(claimId, "claimId");

        return claimRepository.findClaimById(claimId)
                .map(claim -> {
                    if (claim.owner() != OwnerType.ADMIN) {
                        return AdminClaimResult.denied("admin.claim.not-admin");
                    }
                    claimRepository.deleteClaim(claim.id());
                    claimIndex.remove(claim.id());
                    return AdminClaimResult.success(claim);
                })
                .orElseGet(() -> AdminClaimResult.denied("admin.claim.not-found"));
    }

    public List<Claim> listAdminClaims() {
        requireStorage();
        return sortForAdminList(claimRepository.findAllClaims().stream()
                .filter(claim -> claim.owner() == OwnerType.ADMIN)
                .toList());
    }

    public Optional<Claim> findAdminClaim(UUID claimId) {
        requireStorage();
        Objects.requireNonNull(claimId, "claimId");
        return claimRepository.findClaimById(claimId)
                .filter(claim -> claim.owner() == OwnerType.ADMIN);
    }

    public List<Claim> listPlayerClaims(UUID ownerId) {
        requireStorage();
        Objects.requireNonNull(ownerId, "ownerId");
        return sortForAdminList(claimRepository.findClaimsByOwner(OwnerType.PLAYER, ownerId));
    }

    public Optional<Claim> findPlayerClaim(UUID claimId) {
        requireStorage();
        Objects.requireNonNull(claimId, "claimId");
        return claimRepository.findClaimById(claimId)
                .filter(claim -> claim.owner() == OwnerType.PLAYER);
    }

    public AdminClaimResult deletePlayerClaim(UUID claimId) {
        requireStorage();
        Objects.requireNonNull(claimId, "claimId");

        return claimRepository.findClaimById(claimId)
                .map(claim -> {
                    if (claim.owner() != OwnerType.PLAYER) {
                        return AdminClaimResult.denied("admin.userclaims.not-player");
                    }
                    claimRepository.deleteClaim(claim.id());
                    claimIndex.remove(claim.id());
                    return AdminClaimResult.success(claim);
                })
                .orElseGet(() -> AdminClaimResult.denied("admin.userclaims.not-found"));
    }

    public AdminClaimResult transferPlayerClaim(UUID claimId, UUID newOwnerId) {
        requireStorage();
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(newOwnerId, "newOwnerId");

        return claimRepository.findClaimById(claimId)
                .map(claim -> {
                    if (claim.owner() != OwnerType.PLAYER) {
                        return AdminClaimResult.denied("admin.userclaims.not-player");
                    }
                    Claim transferred = new Claim(
                            claim.id(),
                            claim.name(),
                            claim.owner(),
                            newOwnerId,
                            claim.worldId(),
                            claim.claimChunks(),
                            claim.flags(),
                            claim.members(),
                            claim.deniedPlayers(),
                            claim.createdAt(),
                            Instant.now()
                    );
                    claimRepository.saveClaim(transferred);
                    claimIndex.replace(transferred);
                    return AdminClaimResult.success(transferred);
                })
                .orElseGet(() -> AdminClaimResult.denied("admin.userclaims.not-found"));
    }

    public List<Claim> sortForAdminList(List<Claim> claims) {
        Objects.requireNonNull(claims, "claims");
        return claims.stream()
                .map(claim -> Objects.requireNonNull(claim, "claim"))
                .sorted(Comparator.comparing(Claim::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void requireStorage() {
        if (claimRepository == null || claimIndex == null || flagRegistry == null) {
            throw new IllegalStateException("Admin claim storage is not configured.");
        }
    }

    private Map<String, FlagState> defaultFlags() {
        return flagRegistry.keys().stream()
                .collect(Collectors.toUnmodifiableMap(key -> key, flagRegistry::defaultState));
    }
}
