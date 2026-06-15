package com.invisiblespiders.havenclaims.plugin.ui;

import java.util.Objects;

public record ClaimMemberViewRow(String playerName, String role) {
    public ClaimMemberViewRow {
        playerName = Objects.requireNonNull(playerName, "playerName");
        role = Objects.requireNonNull(role, "role");
    }
}
