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
    private final int historyPerPlayer;

    public ClaimModeSessionHistory(Path dataFolder, int historyPerPlayer) {
        this.historyFile = Objects.requireNonNull(dataFolder, "dataFolder").resolve("logs").resolve("claimmode-history.log");
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
        builder.append("session player=").append(playerName)
                .append(" uuid=").append(playerId)
                .append(" entered=").append(enteredAt)
                .append(" exited=").append(exitedAt)
                .append(" reason=").append(reason)
                .append(System.lineSeparator());
        for (ClaimModeItemSnapshot snapshot : snapshots) {
            builder.append("  item slot=").append(snapshot.slot())
                    .append(" summary=\"").append(quote(snapshot.summary())).append("\"")
                    .append(" backup=").append(snapshot.backup())
                    .append(System.lineSeparator());
        }
        for (String result : restoreResults) {
            builder.append("  restore ").append(quote(result)).append(System.lineSeparator());
        }
        builder.append("end-session").append(System.lineSeparator());
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
            if (line.startsWith("session ") && !current.isEmpty()) {
                sessions.add(current);
                current = new ArrayList<>();
            }
            current.add(line);
            if (line.equals("end-session")) {
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
                && session.get(0).startsWith("session ")
                && session.get(0).contains("uuid=" + playerId);
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

    private static String quote(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('"', '\'');
    }
}
