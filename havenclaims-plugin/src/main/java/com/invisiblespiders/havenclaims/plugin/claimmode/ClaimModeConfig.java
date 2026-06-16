package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.file.FileConfiguration;

public record ClaimModeConfig(
        boolean enabled,
        int historyPerPlayer,
        List<String> blockedCommands,
        List<String> allowedCommands
) {
    private static final List<String> DEFAULT_BLOCKED_COMMANDS = List.of(
            "storage", "vault", "shop", "auction", "ah", "trade", "pay", "sell", "buy", "kit", "mail");
    private static final List<String> DEFAULT_ALLOWED_COMMANDS = List.of("claimmode", "cm", "claim");

    public ClaimModeConfig {
        historyPerPlayer = Math.max(1, historyPerPlayer);
        blockedCommands = normalizeList(blockedCommands);
        allowedCommands = normalizeList(allowedCommands);
    }

    public static ClaimModeConfig from(FileConfiguration configuration) {
        boolean enabled = configuration.getBoolean("claim-mode.enabled", true);
        int historyPerPlayer = configuration.getInt("claim-mode.history-per-player", 5);
        String blockedPath = "claim-mode.blocked-commands";
        String allowedPath = "claim-mode.allowed-commands";
        List<String> blocked = configuration.contains(blockedPath)
                ? configuration.getStringList(blockedPath)
                : DEFAULT_BLOCKED_COMMANDS;
        List<String> allowed = configuration.contains(allowedPath)
                ? configuration.getStringList(allowedPath)
                : DEFAULT_ALLOWED_COMMANDS;
        return new ClaimModeConfig(
                enabled,
                historyPerPlayer,
                blocked,
                allowed
        );
    }

    static String normalizeCommandLabel(String label) {
        String normalized = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < normalized.length()) {
            normalized = normalized.substring(namespaceSeparator + 1);
        }
        int firstSpace = normalized.indexOf(' ');
        if (firstSpace >= 0) {
            normalized = normalized.substring(0, firstSpace);
        }
        return normalized;
    }

    private static List<String> normalizeList(List<String> commands) {
        return commands.stream()
                .map(ClaimModeConfig::normalizeCommandLabel)
                .filter(command -> !command.isBlank())
                .distinct()
                .toList();
    }
}
