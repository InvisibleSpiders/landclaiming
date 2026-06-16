package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.util.Objects;

public final class ClaimModeCommandGuard {
    private ClaimModeConfig config;

    public ClaimModeCommandGuard(ClaimModeConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public void reload(ClaimModeConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public boolean isBlocked(String commandLine) {
        String command = ClaimModeConfig.normalizeCommandLabel(commandLine);
        if (config.allowedCommands().contains(command)) {
            if ("claim".equals(command) && config.blockedCommands().contains(command)) {
                return !isClaimModeBridge(commandLine);
            }
            return false;
        }
        return config.blockedCommands().contains(command);
    }

    private static boolean isClaimModeBridge(String commandLine) {
        String normalized = commandLine == null ? "" : commandLine.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int firstSpace = normalized.indexOf(' ');
        if (firstSpace < 0) {
            return false;
        }
        String label = normalized.substring(0, firstSpace);
        int namespaceSeparator = label.indexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < label.length()) {
            label = label.substring(namespaceSeparator + 1);
        }
        if (!"claim".equals(label)) {
            return false;
        }
        String remaining = normalized.substring(firstSpace + 1).trim();
        return remaining.equals("mode") || remaining.startsWith("mode ");
    }
}
