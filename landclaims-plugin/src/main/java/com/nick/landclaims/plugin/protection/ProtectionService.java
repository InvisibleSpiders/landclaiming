package com.nick.landclaims.plugin.protection;

import com.nick.landclaims.api.claim.ClaimView;
import com.nick.landclaims.api.protection.ClaimProtectionResult;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import java.util.Objects;
import java.util.UUID;

public final class ProtectionService {
    private final FlagRegistry flagRegistry;

    public ProtectionService(FlagRegistry flagRegistry) {
        this.flagRegistry = Objects.requireNonNull(flagRegistry, "flagRegistry");
    }

    public ClaimProtectionResult checkClaimFlag(ClaimView claim, UUID actorUuid, String flagKey) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(flagKey, "flagKey");

        if (Objects.equals(claim.ownerUuid(), actorUuid)) {
            return ClaimProtectionResult.ALLOW;
        }

        boolean allowed = claim.flags().getOrDefault(flagKey, flagRegistry.defaultValue(flagKey));
        return allowed ? ClaimProtectionResult.ALLOW : ClaimProtectionResult.DENY_WITH_MESSAGE;
    }
}
