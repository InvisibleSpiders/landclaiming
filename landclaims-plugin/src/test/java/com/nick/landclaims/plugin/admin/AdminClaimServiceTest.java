package com.nick.landclaims.plugin.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.OwnerType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminClaimServiceTest {
    @Test
    void sortForAdminListReturnsNewListSortedByNameIgnoringCase() {
        AdminClaimService service = new AdminClaimService();
        Claim beta = claim("beta");
        Claim alphaUpper = claim("Alpha");
        Claim alphaLower = claim("alpha");
        List<Claim> claims = new ArrayList<>(List.of(beta, alphaUpper, alphaLower));

        List<Claim> sorted = service.sortForAdminList(claims);

        assertThat(sorted).containsExactly(alphaUpper, alphaLower, beta);
        assertThat(sorted).isNotSameAs(claims);
        assertThat(claims).containsExactly(beta, alphaUpper, alphaLower);
    }

    private static Claim claim(String name) {
        UUID worldId = UUID.randomUUID();
        return new Claim(
                UUID.randomUUID(),
                name,
                OwnerType.PLAYER,
                UUID.randomUUID(),
                worldId,
                Set.of(),
                Map.of(),
                Instant.parse("2026-06-07T00:00:00Z"),
                Instant.parse("2026-06-07T00:00:00Z")
        );
    }
}
