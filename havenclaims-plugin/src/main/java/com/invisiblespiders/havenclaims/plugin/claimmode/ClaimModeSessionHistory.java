package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ClaimModeSessionHistory {
    private final Path historyFile;
    private int historyPerPlayer;

    public ClaimModeSessionHistory(Path dataFolder, int historyPerPlayer) {
        this.historyFile = Objects.requireNonNull(dataFolder, "dataFolder").resolve("logs").resolve("claimmode-history.log");
        this.historyPerPlayer = Math.max(1, historyPerPlayer);
    }

    public void reload(int historyPerPlayer) {
        this.historyPerPlayer = Math.max(1, historyPerPlayer);
    }

    public void append(UUID playerId, String playerName, Instant enteredAt, Instant exitedAt,
                       ClaimModeService.ExitReason reason, List<ClaimModeItemSnapshot> snapshots,
                       List<String> restoreResults) {
        String entry = sessionEntry(playerId, playerName, enteredAt, exitedAt, reason, snapshots, restoreResults);
        writeAndTrim(entry, playerId);
    }

    private String sessionEntry(UUID playerId, String playerName, Instant enteredAt, Instant exitedAt,
                                ClaimModeService.ExitReason reason, List<ClaimModeItemSnapshot> snapshots,
                                List<String> restoreResults) {
        StringBuilder builder = new StringBuilder();
        builder.append(jsonLine(
                field("event", "session-start"),
                field("playerName", playerName),
                field("playerId", playerId),
                field("enteredAt", enteredAt),
                field("exitedAt", exitedAt),
                field("reason", reason)
        )).append(System.lineSeparator());
        for (ClaimModeItemSnapshot snapshot : snapshots) {
            builder.append(jsonLine(
                    field("event", "session-item"),
                    field("playerId", playerId),
                    field("slot", snapshot.slot()),
                    field("summary", snapshot.summary()),
                    field("backup", snapshot.backup())
            )).append(System.lineSeparator());
        }
        for (String result : restoreResults) {
            builder.append(jsonLine(
                    field("event", "session-restore"),
                    field("playerId", playerId),
                    field("result", result)
            )).append(System.lineSeparator());
        }
        builder.append(jsonLine(
                field("event", "session-end"),
                field("playerId", playerId)
        )).append(System.lineSeparator());
        return builder.toString();
    }

    private void writeAndTrim(String entry, UUID playerId) {
        try {
            Files.createDirectories(historyFile.getParent());
            Files.writeString(historyFile, entry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            trim(playerId);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write claim mode history", exception);
        }
    }

    private void trim(UUID playerId) throws IOException {
        List<List<String>> sessions = readSessionBlocks();
        int retainedForPlayer = 0;
        List<List<String>> retained = new ArrayList<>();
        for (int i = sessions.size() - 1; i >= 0; i--) {
            List<String> session = sessions.get(i);
            if (!isSessionForPlayer(session, playerId) || retainedForPlayer++ < historyPerPlayer) {
                retained.add(0, session);
            }
        }
        Files.writeString(historyFile, flatten(retained), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private List<List<String>> readSessionBlocks() throws IOException {
        List<List<String>> sessions = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : Files.readAllLines(historyFile, StandardCharsets.UTF_8)) {
            if (isSessionStart(line) && !current.isEmpty()) {
                sessions.add(current);
                current = new ArrayList<>();
            }
            current.add(line);
            if (isSessionEnd(line)) {
                sessions.add(current);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            sessions.add(current);
        }
        return sessions;
    }

    private static boolean isSessionForPlayer(List<String> session, UUID playerId) {
        return !session.isEmpty()
                && isSessionStart(session.get(0))
                && session.get(0).contains("\"playerId\":\"" + playerId + "\"");
    }

    private static String flatten(List<List<String>> sessions) {
        StringBuilder builder = new StringBuilder();
        for (List<String> session : sessions) {
            for (String line : session) {
                builder.append(line).append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    private static boolean isSessionStart(String line) {
        return line.contains("\"event\":\"session-start\"");
    }

    private static boolean isSessionEnd(String line) {
        return line.contains("\"event\":\"session-end\"");
    }

    private static String jsonLine(String... fields) {
        return "{" + String.join(",", fields) + "}";
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
