package com.invisiblespiders.havenclaims.plugin.protection;

import com.invisiblespiders.havenclaims.api.claim.ClaimView;
import com.invisiblespiders.havenclaims.api.protection.ClaimProtectionResult;
import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRole;
import com.invisiblespiders.havenclaims.plugin.flag.FlagRegistry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ProtectionService {
    private static final Set<String> MEMBER_ACCESS_FLAGS = Set.of(
            "build",
            "break",
            "interact",
            "container_access",
            "door_access",
            "switch_access",
            "redstone_access"
    );

    private final FlagRegistry flagRegistry;

    public ProtectionService(FlagRegistry flagRegistry) {
        this.flagRegistry = Objects.requireNonNull(flagRegistry, "flagRegistry");
    }

    public ClaimProtectionResult checkClaimFlag(ClaimView claim, UUID actorUuid, String flagKey) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(flagKey, "flagKey");

        if (actorUuid != null && actorUuid.equals(claim.ownerUuid())) {
            return ClaimProtectionResult.ALLOW;
        }

        if (actorUuid != null && isClaimManager(claim, actorUuid)) {
            return ClaimProtectionResult.ALLOW;
        }

        if (actorUuid != null && MEMBER_ACCESS_FLAGS.contains(flagKey) && isClaimMember(claim, actorUuid)) {
            return ClaimProtectionResult.ALLOW;
        }

        boolean allowed = claim.flags().getOrDefault(flagKey, flagRegistry.defaultValue(flagKey));
        if ("piston_protection".equals(flagKey)) {
            return allowed ? ClaimProtectionResult.DENY_WITH_MESSAGE : ClaimProtectionResult.ALLOW;
        }
        return allowed ? ClaimProtectionResult.ALLOW : ClaimProtectionResult.DENY_WITH_MESSAGE;
    }

    private boolean isClaimMember(ClaimView claim, UUID actorUuid) {
        if (!(claim instanceof Claim landClaim)) {
            return false;
        }
        return landClaim.members().stream().anyMatch(member -> member.memberUuid().equals(actorUuid));
    }

    private boolean isClaimManager(ClaimView claim, UUID actorUuid) {
        if (!(claim instanceof Claim landClaim)) {
            return false;
        }
        return landClaim.members().stream()
                .anyMatch(member -> member.memberUuid().equals(actorUuid) && member.role() == ClaimRole.MANAGER);
    }
}
