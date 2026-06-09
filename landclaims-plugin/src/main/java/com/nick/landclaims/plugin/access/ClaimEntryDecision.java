package com.nick.landclaims.plugin.access;

import com.nick.landclaims.plugin.claim.ClaimChunk;
import java.util.Optional;

public record ClaimEntryDecision(boolean denied, Optional<ClaimChunk> fallbackChunk, String messageKey) {
    public static ClaimEntryDecision allowed() {
        return new ClaimEntryDecision(false, Optional.empty(), "");
    }

    public static ClaimEntryDecision denied(ClaimChunk fallbackChunk, String messageKey) {
        return new ClaimEntryDecision(true, Optional.of(fallbackChunk), messageKey);
    }
}
