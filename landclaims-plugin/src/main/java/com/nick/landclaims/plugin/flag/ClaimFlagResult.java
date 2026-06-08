package com.nick.landclaims.plugin.flag;

import java.util.Objects;

public record ClaimFlagResult(boolean allowed, String messageKey) {
    public static ClaimFlagResult success() {
        return new ClaimFlagResult(true, "");
    }

    public static ClaimFlagResult denied(String messageKey) {
        return new ClaimFlagResult(false, Objects.requireNonNull(messageKey, "messageKey"));
    }
}
