package com.nick.landclaims.plugin.claim;

import java.util.Objects;
import java.util.Optional;

public record ClaimValidationResult(boolean isAllowed, Optional<String> messageKey) {
    public ClaimValidationResult {
        messageKey = Objects.requireNonNull(messageKey, "messageKey");
    }

    public static ClaimValidationResult allowed() {
        return new ClaimValidationResult(true, Optional.empty());
    }

    public static ClaimValidationResult denied(String messageKey) {
        return new ClaimValidationResult(false, Optional.of(Objects.requireNonNull(messageKey, "messageKey")));
    }
}
