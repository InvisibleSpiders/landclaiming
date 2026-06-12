package com.nick.landclaims.plugin.flag;

import com.nick.landclaims.api.flag.FlagKind;
import com.nick.landclaims.api.flag.FlagState;

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
