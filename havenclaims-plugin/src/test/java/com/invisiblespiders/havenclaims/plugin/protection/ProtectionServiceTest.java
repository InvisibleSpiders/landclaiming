package com.invisiblespiders.havenclaims.plugin.protection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.invisiblespiders.havenclaims.api.protection.ClaimProtectionResult;
import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimMember;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRole;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import com.invisiblespiders.havenclaims.plugin.flag.FlagRegistry;
import com.invisiblespiders.havenclaims.api.flag.FlagState;
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
        UUID worldId = UUID.randomUUID();
        return new Claim(
                UUID.randomUUID(), "C", OwnerType.PLAYER, owner,
                new ClaimRegion(worldId, 0, 0, 15, 15),
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
