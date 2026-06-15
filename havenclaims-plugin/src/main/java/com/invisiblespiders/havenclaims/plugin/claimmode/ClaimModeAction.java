package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.util.Locale;

public enum ClaimModeAction {
    ON,
    OFF,
    TOGGLE;

    public static ClaimModeAction from(String[] args) {
        if (args == null || args.length == 0) {
            return TOGGLE;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on", "enable", "start" -> ON;
            case "off", "disable", "stop", "exit" -> OFF;
            default -> TOGGLE;
        };
    }
}
