package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.time.Instant;
import java.util.UUID;

public record ClaimModeRecoveryEntry(
        UUID playerId,
        String playerName,
        Instant timestamp,
        String originalSlot,
        String summary,
        String backup,
        String reason
) {
}
