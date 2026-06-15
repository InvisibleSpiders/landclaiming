package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ClaimModeRecoveryStore {
    private final Path recoveryFile;
    private final List<ClaimModeRecoveryEntry> pendingEntries = new ArrayList<>();

    public ClaimModeRecoveryStore(Path dataFolder) {
        this.recoveryFile = Objects.requireNonNull(dataFolder, "dataFolder").resolve("claimmode-recovery.log");
    }

    public void add(ClaimModeRecoveryEntry entry) {
        ClaimModeRecoveryEntry recoveryEntry = Objects.requireNonNull(entry, "entry");
        append(recoveryEntry);
        pendingEntries.add(recoveryEntry);
    }

    public List<ClaimModeRecoveryEntry> pendingFor(UUID playerId) {
        return pendingEntries.stream()
                .filter(entry -> entry.playerId().equals(playerId))
                .toList();
    }

    private void append(ClaimModeRecoveryEntry entry) {
        try {
            Files.createDirectories(recoveryFile.getParent());
            Files.writeString(
                    recoveryFile,
                    recoveryJson(entry) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write claim mode recovery entry", exception);
        }
    }

    private static String recoveryJson(ClaimModeRecoveryEntry entry) {
        return "{"
                + field("event", "recovery-entry") + ","
                + field("timestamp", entry.timestamp()) + ","
                + field("playerName", entry.playerName()) + ","
                + field("playerId", entry.playerId()) + ","
                + field("originalSlot", entry.originalSlot()) + ","
                + field("reason", entry.reason()) + ","
                + field("summary", entry.summary()) + ","
                + field("backup", entry.backup())
                + "}";
    }

    private static String field(String name, Object value) {
        return "\"" + name + "\":\"" + escape(String.valueOf(value)) + "\"";
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append("\\u%04x".formatted((int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }
}
