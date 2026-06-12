package com.nick.landclaims.plugin.protection;

import com.nick.landclaims.api.claim.ClaimView;
import com.nick.landclaims.api.flag.ClaimFlagDefinition;
import com.nick.landclaims.api.flag.FlagKind;
import com.nick.landclaims.api.flag.FlagState;
import com.nick.landclaims.api.protection.ClaimProtectionResult;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimRole;
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

        FlagState state = claim.flags().getOrDefault(flagKey, flagRegistry.defaultState(flagKey));
        ClaimFlagDefinition definition = flagRegistry.definition(flagKey).orElse(null);
        FlagKind kind = definition == null ? FlagKind.PLAYER_ACTION : definition.kind();

        if (kind == FlagKind.WORLD_EFFECT) {
            return worldEffectResult(flagKey, state);
        }
        return playerActionResult(claim, actorUuid, definition, state);
    }

    private ClaimProtectionResult worldEffectResult(String flagKey, FlagState state) {
        boolean enabled = state != FlagState.OFF;
        if ("piston_protection".equals(flagKey)) {
            return enabled ? ClaimProtectionResult.DENY_WITH_MESSAGE : ClaimProtectionResult.ALLOW;
        }
        return enabled ? ClaimProtectionResult.ALLOW : ClaimProtectionResult.DENY_WITH_MESSAGE;
    }

    private ClaimProtectionResult playerActionResult(
            ClaimView claim, UUID actorUuid, ClaimFlagDefinition definition, FlagState state) {
        if (state == FlagState.OFF) {
            return ClaimProtectionResult.ALLOW;
        }
        boolean ownerExempt = definition == null || definition.ownerExempt();
        Relationship relationship = relationship(claim, actorUuid);
        return switch (state) {
            case VISITORS -> relationship == Relationship.VISITOR
                    ? ClaimProtectionResult.DENY_WITH_MESSAGE
                    : ClaimProtectionResult.ALLOW;
            case ALL -> (ownerExempt && relationship.isTrusted())
                    ? ClaimProtectionResult.ALLOW
                    : ClaimProtectionResult.DENY_WITH_MESSAGE;
            default -> ClaimProtectionResult.ALLOW;
        };
    }

    private enum Relationship {
        OWNER, MANAGER, MEMBER, VISITOR;

        boolean isTrusted() {
            return this == OWNER || this == MANAGER;
        }
    }

    private Relationship relationship(ClaimView claim, UUID actorUuid) {
        if (actorUuid == null) {
            return Relationship.VISITOR;
        }
        if (actorUuid.equals(claim.ownerUuid())) {
            return Relationship.OWNER;
        }
        if (!(claim instanceof Claim landClaim)) {
            return Relationship.VISITOR;
        }
        return landClaim.members().stream()
                .filter(member -> member.memberUuid().equals(actorUuid))
                .findFirst()
                .map(member -> member.role() == ClaimRole.MANAGER ? Relationship.MANAGER : Relationship.MEMBER)
                .orElse(Relationship.VISITOR);
    }
}
