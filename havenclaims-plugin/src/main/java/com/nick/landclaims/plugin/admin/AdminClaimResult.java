package com.nick.landclaims.plugin.admin;

import com.nick.landclaims.plugin.claim.Claim;

public record AdminClaimResult(boolean allowed, String messageKey, Claim claim) {
    public static AdminClaimResult success(Claim claim) {
        return new AdminClaimResult(true, "admin.claim.success", claim);
    }

    public static AdminClaimResult denied(String messageKey) {
        return new AdminClaimResult(false, messageKey, null);
    }
}
