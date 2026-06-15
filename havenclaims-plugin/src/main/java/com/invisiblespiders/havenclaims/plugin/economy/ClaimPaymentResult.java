package com.invisiblespiders.havenclaims.plugin.economy;

public record ClaimPaymentResult(boolean allowed, String messageKey) {
    public static ClaimPaymentResult success() {
        return new ClaimPaymentResult(true, "");
    }

    public static ClaimPaymentResult denied(String messageKey) {
        return new ClaimPaymentResult(false, messageKey);
    }
}
