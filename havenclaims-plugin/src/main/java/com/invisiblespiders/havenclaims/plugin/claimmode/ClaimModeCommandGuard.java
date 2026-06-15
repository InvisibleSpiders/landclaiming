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
            return false;
        }
        return config.blockedCommands().contains(command);
    }
}
