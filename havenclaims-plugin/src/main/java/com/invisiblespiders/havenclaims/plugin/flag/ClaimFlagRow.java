package com.invisiblespiders.havenclaims.plugin.flag;

import com.invisiblespiders.havenclaims.api.flag.FlagKind;
import com.invisiblespiders.havenclaims.api.flag.FlagState;

public record ClaimFlagRow(
        String key,
        String category,
        String label,
        String description,
        FlagKind kind,
        FlagState state,
        String editPermission
) {
}
