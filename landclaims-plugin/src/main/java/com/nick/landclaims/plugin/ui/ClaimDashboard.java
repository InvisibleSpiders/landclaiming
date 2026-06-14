package com.nick.landclaims.plugin.ui;

import java.util.List;
import java.util.Objects;

public record ClaimDashboard(
        String title,
        List<ClaimDashboardRow> claims,
        List<ClaimMenuAction> actions
) {
    public ClaimDashboard {
        title = Objects.requireNonNull(title, "title");
        claims = List.copyOf(Objects.requireNonNull(claims, "claims"));
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
    }
}
