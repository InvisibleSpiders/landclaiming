package com.invisiblespiders.havenclaims.plugin.listener;

enum BoundaryMessageDelivery {
    CHAT,
    ACTION_BAR,
    BOTH,
    NONE;

    static BoundaryMessageDelivery from(String value, BoundaryMessageDelivery fallback) {
        if ("chat".equalsIgnoreCase(value)) {
            return CHAT;
        }
        if ("action_bar".equalsIgnoreCase(value) || "actionbar".equalsIgnoreCase(value)) {
            return ACTION_BAR;
        }
        if ("both".equalsIgnoreCase(value)) {
            return BOTH;
        }
        if ("none".equalsIgnoreCase(value) || "disabled".equalsIgnoreCase(value)) {
            return NONE;
        }
        return fallback;
    }

    boolean sendsChat() {
        return this == CHAT || this == BOTH;
    }

    boolean sendsActionBar() {
        return this == ACTION_BAR || this == BOTH;
    }
}
