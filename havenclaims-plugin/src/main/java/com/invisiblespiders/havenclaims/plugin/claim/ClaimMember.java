package com.invisiblespiders.havenclaims.plugin.claim;

import java.util.Objects;
import java.util.UUID;

public record ClaimMember(UUID memberUuid, ClaimRole role) {
    public ClaimMember {
        memberUuid = Objects.requireNonNull(memberUuid, "memberUuid");
        role = Objects.requireNonNull(role, "role");
    }
}
