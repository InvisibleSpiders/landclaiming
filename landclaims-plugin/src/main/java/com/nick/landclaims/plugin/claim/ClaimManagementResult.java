package com.nick.landclaims.plugin.claim;

import java.util.Objects;

public record ClaimManagementResult(boolean success, String messageKey) {
    public static ClaimManagementResult ok(String messageKey) {
        return new ClaimManagementResult(true, Objects.requireNonNull(messageKey, "messageKey"));
    }

    public static ClaimManagementResult denied(String messageKey) {
        return new ClaimManagementResult(false, Objects.requireNonNull(messageKey, "messageKey"));
    }
}
