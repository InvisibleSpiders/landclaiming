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
        pendingEntries.add(recoveryEntry);
        append(recoveryEntry);
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
                    entry.timestamp()
                            + " player=" + entry.playerName()
                            + " uuid=" + entry.playerId()
                            + " slot=" + entry.originalSlot()
                            + " reason=\"" + quote(entry.reason()) + "\""
                            + " summary=\"" + quote(entry.summary()) + "\""
                            + " backup=" + entry.backup()
                            + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write claim mode recovery entry", exception);
        }
    }

    private static String quote(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('"', '\'');
    }
}
