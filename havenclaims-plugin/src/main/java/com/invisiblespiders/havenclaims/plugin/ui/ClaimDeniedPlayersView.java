package com.invisiblespiders.havenclaims.plugin.ui;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ClaimDeniedPlayersView(
        UUID claimId,
        String claimName,
        List<ClaimDeniedPlayerViewRow> deniedPlayers,
        List<ClaimMenuAction> actions,
        ClaimMenuAction backAction
) {
    public ClaimDeniedPlayersView {
        claimId = Objects.requireNonNull(claimId, "claimId");
        claimName = Objects.requireNonNull(claimName, "claimName");
        deniedPlayers = List.copyOf(Objects.requireNonNull(deniedPlayers, "deniedPlayers"));
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        backAction = Objects.requireNonNull(backAction, "backAction");
    }
}
