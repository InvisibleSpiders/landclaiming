package com.nick.landclaims.plugin.ui;

import java.util.Objects;

public record ClaimDeniedPlayerViewRow(String playerName) {
    public ClaimDeniedPlayerViewRow {
        playerName = Objects.requireNonNull(playerName, "playerName");
    }
}
