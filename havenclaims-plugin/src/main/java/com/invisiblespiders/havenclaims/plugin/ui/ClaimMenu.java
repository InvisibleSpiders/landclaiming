package com.invisiblespiders.havenclaims.plugin.ui;

import java.util.List;
import java.util.Objects;

public record ClaimMenu(
        String title,
        String ownerType,
        int chunkCount,
        int memberCount,
        int flagCount,
        boolean viewerOwnsClaim,
        boolean adminClaim,
        List<ClaimMenuAction> actions
) {
    public ClaimMenu {
        title = Objects.requireNonNull(title, "title");
        ownerType = Objects.requireNonNull(ownerType, "ownerType");
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
    }
}
