package com.invisiblespiders.havenclaims.plugin.flag;

public record ClaimFlagRow(
        String key,
        String category,
        String label,
        String description,
        boolean enabled,
        String editPermission
) {
}
