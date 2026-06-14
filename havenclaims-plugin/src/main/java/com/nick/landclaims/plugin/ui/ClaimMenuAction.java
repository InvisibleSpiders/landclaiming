package com.nick.landclaims.plugin.ui;

import java.util.Objects;

public record ClaimMenuAction(String label, String command) {
    public ClaimMenuAction {
        label = Objects.requireNonNull(label, "label");
        command = Objects.requireNonNull(command, "command");
    }
}
