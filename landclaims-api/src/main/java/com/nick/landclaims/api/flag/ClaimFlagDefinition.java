package com.nick.landclaims.api.flag;

public record ClaimFlagDefinition(
        String key,
        String category,
        String label,
        String description,
        FlagKind kind,
        boolean ownerExempt,
        FlagState defaultState,
        String editPermission
) {
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
        if (kind == null) {
            throw new IllegalArgumentException("Flag kind cannot be null.");
        }
        if (defaultState == null) {
            throw new IllegalArgumentException("Flag defaultState cannot be null.");
        }
        if (editPermission == null) {
            throw new IllegalArgumentException("Flag editPermission cannot be null.");
        }
        if (!kind.supports(defaultState)) {
            throw new IllegalArgumentException(
                    "Flag " + key + " kind " + kind + " does not support default state " + defaultState);
        }
    }
}
