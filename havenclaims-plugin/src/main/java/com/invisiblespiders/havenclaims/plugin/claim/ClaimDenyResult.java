package com.invisiblespiders.havenclaims.plugin.claim;

public record ClaimDenyResult(boolean allowed, String messageKey) {
    public static ClaimDenyResult success() {
        return new ClaimDenyResult(true, "");
    }

    public static ClaimDenyResult denied(String messageKey) {
        return new ClaimDenyResult(false, messageKey);
    }
}
