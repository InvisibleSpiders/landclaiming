package com.nick.landclaims.api.flag;

public record ClaimFlagDefinition(
        String key,
        String category,
        String label,
        String description,
        boolean defaultValue,
        String editPermission
) {
    public ClaimFlagDefinition(String key, String category, boolean defaultValue, String editPermission) {
        this(key, category, key, "", defaultValue, editPermission);
    }

    public ClaimFlagDefinition {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Flag key cannot be blank.");
        }
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Flag category cannot be blank.");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Flag label cannot be blank.");
        }
        if (description == null) {
            throw new IllegalArgumentException("Flag description cannot be null.");
        }
    }
}
