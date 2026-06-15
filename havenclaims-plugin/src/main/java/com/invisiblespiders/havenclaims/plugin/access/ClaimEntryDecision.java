package com.invisiblespiders.havenclaims.plugin.access;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import java.util.Optional;

public record ClaimEntryDecision(boolean denied, Optional<ClaimChunk> fallbackChunk, String messageKey) {
    public static ClaimEntryDecision allowed() {
        return new ClaimEntryDecision(false, Optional.empty(), "");
    }

    public static ClaimEntryDecision denied(ClaimChunk fallbackChunk, String messageKey) {
        return new ClaimEntryDecision(true, Optional.of(fallbackChunk), messageKey);
    }
}
