package com.invisiblespiders.havenclaims.plugin.ui;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ClaimMembersView(
        UUID claimId,
        String claimName,
        List<ClaimMemberViewRow> members,
        List<ClaimMenuAction> actions,
        ClaimMenuAction backAction
) {
    public ClaimMembersView {
        claimId = Objects.requireNonNull(claimId, "claimId");
        claimName = Objects.requireNonNull(claimName, "claimName");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
        backAction = Objects.requireNonNull(backAction, "backAction");
    }
}
