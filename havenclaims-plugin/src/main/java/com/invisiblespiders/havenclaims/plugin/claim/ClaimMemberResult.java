package com.invisiblespiders.havenclaims.plugin.claim;

import java.util.Objects;

public record ClaimMemberResult(boolean allowed, String messageKey) {
    public static ClaimMemberResult success() {
        return new ClaimMemberResult(true, "");
    }

    public static ClaimMemberResult denied(String messageKey) {
        return new ClaimMemberResult(false, Objects.requireNonNull(messageKey, "messageKey"));
    }
}
