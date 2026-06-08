package com.nick.landclaims.plugin.ui;

import java.util.Objects;

public record ClaimFlagEditorRow(
        String key,
        String category,
        String stateLabel,
        String nextStateLabel,
        String toggleCommand
) {
    public ClaimFlagEditorRow {
        key = Objects.requireNonNull(key, "key");
        category = Objects.requireNonNull(category, "category");
        stateLabel = Objects.requireNonNull(stateLabel, "stateLabel");
        nextStateLabel = Objects.requireNonNull(nextStateLabel, "nextStateLabel");
        toggleCommand = Objects.requireNonNull(toggleCommand, "toggleCommand");
    }
}
