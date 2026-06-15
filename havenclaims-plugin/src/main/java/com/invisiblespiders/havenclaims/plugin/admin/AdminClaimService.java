package com.invisiblespiders.havenclaims.plugin.admin;

import com.invisiblespiders.havenclaims.api.flag.FlagState;
import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimMember;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import com.invisiblespiders.havenclaims.plugin.economy.ClaimPaymentService;
import com.invisiblespiders.havenclaims.plugin.flag.FlagRegistry;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimCostQuote;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimCostService;
import com.invisiblespiders.havenclaims.plugin.storage.ClaimRepository;
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
                    // Drop the incoming owner from the member roster — owner and member are distinct
                    // roles, and leaving a stale member row would duplicate their access grant.
                    Set<ClaimMember> retainedMembers = claim.members().stream()
                            .filter(member -> !member.memberUuid().equals(newOwnerId))
                            .collect(Collectors.toSet());
                    Claim transferred = new Claim(
                            claim.id(),
                            claim.name(),
                            claim.owner(),
                            newOwnerId,
                            claim.worldId(),
                            claim.claimChunks(),
                            claim.flags(),
                            retainedMembers,
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

    public DisbandResult disbandPlayerClaims(UUID ownerId, boolean refund,
            ClaimCostService costService, ClaimPaymentService paymentService) {
        requireStorage();
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(costService, "costService");
        Objects.requireNonNull(paymentService, "paymentService");

        List<Claim> claims = listPlayerClaims(ownerId);
        if (claims.isEmpty()) {
            return new DisbandResult(0, 0.0);
        }
        double totalRefunded = 0.0;
        for (Claim claim : claims) {
            if (refund) {
                double amount = costService.computeDeletionRefund(ownerId, claim.claimChunks().size());
                if (amount > 0.0) {
                    paymentService.refund(ownerId, new ClaimCostQuote(0, 0, 0, 0, 0, amount));
                    totalRefunded += amount;
                }
            }
            claimRepository.deleteClaim(claim.id());
            claimIndex.remove(claim.id());
        }
        return new DisbandResult(claims.size(), totalRefunded);
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
