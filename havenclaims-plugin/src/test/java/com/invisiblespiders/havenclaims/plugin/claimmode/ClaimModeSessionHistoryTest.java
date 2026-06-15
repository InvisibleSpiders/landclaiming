package com.invisiblespiders.havenclaims.plugin.claimmode;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaimModeSessionHistoryTest {
    @TempDir
    Path tempDir;

    @Test
    void reloadUsesNewHistoryRetentionLimit() throws Exception {
        UUID playerId = UUID.randomUUID();
        ClaimModeSessionHistory history = new ClaimModeSessionHistory(tempDir, 2);
        history.append(playerId, "Alice", Instant.parse("2026-06-15T00:00:00Z"),
                Instant.parse("2026-06-15T00:00:01Z"), ClaimModeService.ExitReason.MANUAL, List.of(), List.of());
        history.append(playerId, "Alice", Instant.parse("2026-06-15T00:01:00Z"),
                Instant.parse("2026-06-15T00:01:01Z"), ClaimModeService.ExitReason.LOGOUT, List.of(), List.of());
        assertThat(sessionStarts()).hasSize(2);

        history.reload(1);
        history.append(playerId, "Alice", Instant.parse("2026-06-15T00:02:00Z"),
                Instant.parse("2026-06-15T00:02:01Z"), ClaimModeService.ExitReason.DEATH, List.of(), List.of());

        String log = Files.readString(historyFile(), StandardCharsets.UTF_8);
        assertThat(sessionStarts()).hasSize(1);
        assertThat(log).contains("\"reason\":\"DEATH\"");
        assertThat(log).doesNotContain("\"reason\":\"MANUAL\"");
        assertThat(log).doesNotContain("\"reason\":\"LOGOUT\"");
    }

    private List<String> sessionStarts() throws Exception {
        return Files.readAllLines(historyFile(), StandardCharsets.UTF_8).stream()
                .filter(line -> line.contains("\"event\":\"session-start\""))
                .toList();
    }

    private Path historyFile() {
        return tempDir.resolve("logs").resolve("claimmode-history.log");
    }
}
