package com.invisiblespiders.havenclaims.plugin.ui;

import java.util.Objects;

public record ClaimFlagEditorRow(
        String key,
        String category,
        String label,
        String description,
        String stateLabel,
        String nextStateLabel,
        String toggleCommand
) {
    public ClaimFlagEditorRow {
        key = Objects.requireNonNull(key, "key");
        category = Objects.requireNonNull(category, "category");
        label = Objects.requireNonNull(label, "label");
        description = Objects.requireNonNull(description, "description");
        stateLabel = Objects.requireNonNull(stateLabel, "stateLabel");
        nextStateLabel = Objects.requireNonNull(nextStateLabel, "nextStateLabel");
        toggleCommand = Objects.requireNonNull(toggleCommand, "toggleCommand");
    }
}
