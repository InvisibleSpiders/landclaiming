package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ClaimModeSession(
        UUID playerId,
        String playerName,
        Instant enteredAt,
        List<ClaimModeItemSnapshot> snapshots
) {
    public ClaimModeSession {
        playerId = Objects.requireNonNull(playerId, "playerId");
        playerName = Objects.requireNonNull(playerName, "playerName");
        enteredAt = Objects.requireNonNull(enteredAt, "enteredAt");
        snapshots = List.copyOf(Objects.requireNonNull(snapshots, "snapshots"));
    }
}
