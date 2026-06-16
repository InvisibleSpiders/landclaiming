package com.invisiblespiders.havenclaims.plugin.claim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.invisiblespiders.havenclaims.api.flag.FlagState;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimFlagTypeTest {
    @Test
    void claimStoresFlagStates() {
        UUID worldId = UUID.randomUUID();
        Claim claim = new Claim(
                UUID.randomUUID(), "Home", OwnerType.PLAYER, UUID.randomUUID(),
                new ClaimRegion(worldId, 0, 0, 15, 15),
                Map.of("container_access", FlagState.VISITORS),
                Instant.now(), Instant.now());
        assertEquals(FlagState.VISITORS, claim.flags().get("container_access"));
    }
}
