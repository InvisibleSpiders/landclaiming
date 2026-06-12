package com.nick.landclaims.plugin.claim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nick.landclaims.api.flag.FlagState;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimFlagTypeTest {
    @Test
    void claimStoresFlagStates() {
        Claim claim = new Claim(
                UUID.randomUUID(), "Home", OwnerType.PLAYER, UUID.randomUUID(), UUID.randomUUID(),
                Set.of(new ClaimChunk(UUID.randomUUID(), 0, 0)),
                Map.of("container_access", FlagState.VISITORS),
                Instant.now(), Instant.now());
        assertEquals(FlagState.VISITORS, claim.flags().get("container_access"));
    }
}
