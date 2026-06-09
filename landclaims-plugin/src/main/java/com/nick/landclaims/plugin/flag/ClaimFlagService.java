package com.nick.landclaims.plugin.flag;

import com.nick.landclaims.api.flag.ClaimFlagDefinition;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.ClaimRole;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

public final class ClaimFlagService {
    private static final List<String> CATEGORY_ORDER = List.of(
            "Access",
            "Protection",
            "Environment",
            "Entity",
            "Entity Control",
            "Items"
    );

    private final ClaimRepository claimRepository;
    private final ClaimIndex claimIndex;
    private final FlagRegistry flagRegistry;

    public ClaimFlagService(ClaimRepository claimRepository, ClaimIndex claimIndex, FlagRegistry flagRegistry) {
        this.claimRepository = Objects.requireNonNull(claimRepository, "claimRepository");
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
        this.flagRegistry = Objects.requireNonNull(flagRegistry, "flagRegistry");
    }

    public ClaimFlagResult setFlag(
            UUID actorId,
            Claim claim,
            String flagKey,
            boolean enabled,
            Predicate<String> permissionCheck
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(flagKey, "flagKey");
        Objects.requireNonNull(permissionCheck, "permissionCheck");

        if (!actorId.equals(claim.ownerUuid()) && !isManager(actorId, claim)) {
            return ClaimFlagResult.denied("claim.flag.not-owner");
        }

        ClaimFlagDefinition definition = flagRegistry.definition(flagKey)
                .orElse(null);
        if (definition == null) {
            return ClaimFlagResult.denied("claim.flag.unknown");
        }
        if (!permissionCheck.test(definition.editPermission())) {
            return ClaimFlagResult.denied("claim.flag.no-permission");
        }

        Map<String, Boolean> flags = new HashMap<>(claim.flags());
        flags.put(definition.key(), enabled);
        Claim updatedClaim = new Claim(
                claim.id(),
                claim.name(),
                claim.owner(),
                claim.ownerUuid(),
                claim.worldId(),
                claim.claimChunks(),
                flags,
                claim.members(),
                claim.createdAt(),
                Instant.now()
        );
        claimRepository.saveClaim(updatedClaim);
        claimIndex.replace(updatedClaim);
        return ClaimFlagResult.success();
    }

    public ClaimFlagResult toggleFlag(
            UUID actorId,
            Claim claim,
            String flagKey,
            Predicate<String> permissionCheck
    ) {
        Objects.requireNonNull(flagKey, "flagKey");
        ClaimFlagDefinition definition = flagRegistry.definition(flagKey)
                .orElse(null);
        if (definition == null) {
            return ClaimFlagResult.denied("claim.flag.unknown");
        }
        boolean currentValue = claim.flags().getOrDefault(definition.key(), definition.defaultValue());
        return setFlag(actorId, claim, definition.key(), !currentValue, permissionCheck);
    }

    private boolean isManager(UUID actorId, Claim claim) {
        if (actorId == null) {
            return false;
        }
        return claim.members().stream()
                .anyMatch(m -> m.memberUuid().equals(actorId) && m.role() == ClaimRole.MANAGER);
    }

    public List<ClaimFlagRow> listFlags(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        return flagRegistry.definitions().stream()
                .sorted(Comparator
                        .comparingInt((ClaimFlagDefinition definition) -> categoryIndex(definition.category()))
                        .thenComparing(ClaimFlagDefinition::category)
                        .thenComparing(ClaimFlagDefinition::key))
                .map(definition -> new ClaimFlagRow(
                        definition.key(),
                        definition.category(),
                        definition.label(),
                        definition.description(),
                        claim.flags().getOrDefault(definition.key(), definition.defaultValue()),
                        definition.editPermission()
                ))
                .toList();
    }

    private int categoryIndex(String category) {
        int index = CATEGORY_ORDER.indexOf(category);
        return index < 0 ? CATEGORY_ORDER.size() : index;
    }
}
