package com.invisiblespiders.havenclaims.plugin.ui;

import java.util.List;
import java.util.Objects;

public record ClaimFlagEditor(String claimName, List<ClaimFlagEditorRow> rows) {
    public ClaimFlagEditor {
        claimName = Objects.requireNonNull(claimName, "claimName");
        rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
    }
}
