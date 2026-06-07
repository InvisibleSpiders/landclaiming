package com.nick.landclaims.api.flag;

public record ClaimFlagDefinition(
        String key,
        String category,
        boolean defaultValue,
        String editPermission
) {
    public ClaimFlagDefinition {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Flag key cannot be blank.");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Flag category cannot be blank.");
        }
    }
}
