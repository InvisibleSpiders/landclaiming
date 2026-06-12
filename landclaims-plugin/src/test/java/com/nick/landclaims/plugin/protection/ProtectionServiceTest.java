package com.nick.landclaims.plugin.protection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nick.landclaims.api.protection.ClaimProtectionResult;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimMember;
import com.nick.landclaims.plugin.claim.ClaimRole;
import com.nick.landclaims.plugin.claim.OwnerType;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import com.nick.landclaims.api.flag.FlagState;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProtectionServiceTest {
    private final ProtectionService service = new ProtectionService(FlagRegistry.createDefault());
    private final UUID owner = UUID.randomUUID();
    private final UUID manager = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID visitor = UUID.randomUUID();

    private Claim claimWith(String flag, FlagState state) {
        return new Claim(
                UUID.randomUUID(), "C", OwnerType.PLAYER, owner, UUID.randomUUID(),
                Set.of(new ClaimChunk(UUID.randomUUID(), 0, 0)),
                Map.of(flag, state),
                Set.of(new ClaimMember(manager, ClaimRole.MANAGER), new ClaimMember(member, ClaimRole.MEMBER)),
                Set.of(), Instant.now(), Instant.now());
    }

    private ClaimProtectionResult check(Claim c, UUID actor, String flag) {
        return service.checkClaimFlag(c, actor, flag);
    }

    @Test
    void playerActionOffAllowsEveryone() {
        Claim c = claimWith("container_access", FlagState.OFF);
        assertEquals(ClaimProtectionResult.ALLOW, check(c, visitor, "container_access"));
        assertEquals(ClaimProtectionResult.ALLOW, check(c, member, "container_access"));
        assertEquals(ClaimProtectionResult.ALLOW, check(c, owner, "container_access"));
    }

    @Test
    void playerActionVisitorsDeniesOnlyVisitor() {
        Claim c = claimWith("container_access", FlagState.VISITORS);
        assertEquals(ClaimProtectionResult.ALLOW, check(c, owner, "container_access"));
        assertEquals(ClaimProtectionResult.ALLOW, check(c, manager, "container_access"));
        assertEquals(ClaimProtectionResult.ALLOW, check(c, member, "container_access"));
        assertEquals(ClaimProtectionResult.DENY_WITH_MESSAGE, check(c, visitor, "container_access"));
        assertEquals(ClaimProtectionResult.DENY_WITH_MESSAGE, check(c, null, "container_access"));
    }

    @Test
    void playerActionAllAllowsOwnerAndManagerOnly() {
        Claim c = claimWith("container_access", FlagState.ALL);
        assertEquals(ClaimProtectionResult.ALLOW, check(c, owner, "container_access"));
        assertEquals(ClaimProtectionResult.ALLOW, check(c, manager, "container_access"));
        assertEquals(ClaimProtectionResult.DENY_WITH_MESSAGE, check(c, member, "container_access"));
        assertEquals(ClaimProtectionResult.DENY_WITH_MESSAGE, check(c, visitor, "container_access"));
    }

    @Test
    void worldEffectIgnoresActorAndOwner() {
        // explosion_damage: OFF = explosions denied, ALL = allowed. ownerExempt=false.
        Claim off = claimWith("explosion_damage", FlagState.OFF);
        assertEquals(ClaimProtectionResult.DENY_WITH_MESSAGE, check(off, owner, "explosion_damage"));
        Claim all = claimWith("explosion_damage", FlagState.ALL);
        assertEquals(ClaimProtectionResult.ALLOW, check(all, owner, "explosion_damage"));
    }

    @Test
    void pistonProtectionInversionPreserved() {
        Claim protectedClaim = claimWith("piston_protection", FlagState.ALL);
        assertEquals(ClaimProtectionResult.DENY_WITH_MESSAGE, check(protectedClaim, null, "piston_protection"));
        Claim unprotected = claimWith("piston_protection", FlagState.OFF);
        assertEquals(ClaimProtectionResult.ALLOW, check(unprotected, null, "piston_protection"));
    }
}
