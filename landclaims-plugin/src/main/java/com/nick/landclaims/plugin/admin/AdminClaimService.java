package com.nick.landclaims.plugin.admin;

import com.nick.landclaims.plugin.claim.Claim;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class AdminClaimService {
    public List<Claim> sortForAdminList(List<Claim> claims) {
        Objects.requireNonNull(claims, "claims");
        return claims.stream()
                .map(claim -> Objects.requireNonNull(claim, "claim"))
                .sorted(Comparator.comparing(Claim::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
