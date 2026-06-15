package com.invisiblespiders.havenclaims.plugin.claimmode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class ClaimModeItemCodecTest {
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    @TempDir
    Path tempDir;

    @Test
    void serializesAndRestoresBackupBytes() {
        // Plain JVM Paper API tests do not have Bukkit registry access for real ItemStack construction,
        // so this verifies our Base64 boundary around Paper's byte serializer/deserializer.
        byte[] itemBytes = new byte[] {1, 2, 3};
        ItemStack item = item(Material.DIAMOND_SWORD, 1, auditedMeta());
        ItemStack restored = item(Material.DIAMOND_SWORD, 1, auditedMeta());
        when(item.serializeAsBytes()).thenReturn(itemBytes);

        String backup = ClaimModeItemCodec.serialize(item);

        try (MockedStatic<ItemStack> itemStacks = mockStatic(ItemStack.class)) {
            itemStacks.when(() -> ItemStack.deserializeBytes(itemBytes)).thenReturn(restored);

            assertThat(backup).isEqualTo(Base64.getEncoder().encodeToString(itemBytes));
            assertThat(ClaimModeItemCodec.deserialize(backup)).isSameAs(restored);
        }
    }

    @Test
    void treatsEmptyItemsAsAbsent() {
        ItemStack air = item(Material.AIR, 1, null);

        assertThat(ClaimModeItemCodec.serialize(null)).isEmpty();
        assertThat(ClaimModeItemCodec.serialize(air)).isEmpty();
        assertThat(ClaimModeItemCodec.deserialize(null)).isNull();
        assertThat(ClaimModeItemCodec.deserialize("  ")).isNull();
        assertThat(ClaimModeItemCodec.summary(null)).isEqualTo("empty");
        assertThat(ClaimModeItemCodec.summary(air)).isEqualTo("empty");
    }

    @Test
    void createsHumanReadableSummary() {
        String summary = ClaimModeItemCodec.summary(item(Material.DIAMOND_SWORD, 1, auditedMeta()));

        assertThat(summary).contains("type=DIAMOND_SWORD");
        assertThat(summary).contains("amount=1");
        assertThat(summary).contains("damage=12");
        assertThat(summary).contains("Trusty");
        assertThat(summary).contains("lore=[first line, quoted line, third line]");
        assertThat(summary).doesNotContain("fourth line");
    }

    @Test
    void snapshotRestoresFromBackup() {
        byte[] itemBytes = new byte[] {4, 5, 6};
        String backup = Base64.getEncoder().encodeToString(itemBytes);
        ItemStack restored = item(Material.DIAMOND_SWORD, 1, auditedMeta());
        ClaimModeItemSnapshot snapshot = new ClaimModeItemSnapshot("hotbar-0", "type=DIAMOND_SWORD", backup);

        try (MockedStatic<ItemStack> itemStacks = mockStatic(ItemStack.class)) {
            itemStacks.when(() -> ItemStack.deserializeBytes(itemBytes)).thenReturn(restored);

            assertThat(snapshot.slot()).isEqualTo("hotbar-0");
            assertThat(snapshot.empty()).isFalse();
            assertThat(snapshot.summary()).contains("DIAMOND_SWORD");
            assertThat(snapshot.restoreItem()).isSameAs(restored);
        }
    }

    @Test
    void recoveryStoreAppendsEntriesAndTracksPendingByPlayer() throws Exception {
        UUID playerId = UUID.randomUUID();
        UUID otherPlayerId = UUID.randomUUID();
        ClaimModeRecoveryStore store = new ClaimModeRecoveryStore(tempDir);
        ClaimModeRecoveryEntry entry = new ClaimModeRecoveryEntry(
                playerId,
                "Alice \"Admin\"",
                Instant.parse("2026-06-15T10:15:30Z"),
                "hotbar-0",
                "type=DIAMOND\nname=\"Keeper\"",
                "backup-data",
                "restore \"failed\"\nkept"
        );
        ClaimModeRecoveryEntry other = new ClaimModeRecoveryEntry(
                otherPlayerId,
                "Bob",
                Instant.parse("2026-06-15T10:16:30Z"),
                "offhand",
                "type=SHIELD",
                "other-backup",
                "restore failed"
        );

        store.add(entry);
        store.add(other);

        assertThat(store.pendingFor(playerId)).containsExactly(entry);
        String log = Files.readString(tempDir.resolve("claimmode-recovery.log"), StandardCharsets.UTF_8);
        assertThat(log).contains("\"event\":\"recovery-entry\"");
        assertThat(log).contains("\"timestamp\":\"2026-06-15T10:15:30Z\"");
        assertThat(log).contains("\"playerName\":\"Alice \\\"Admin\\\"\"");
        assertThat(log).contains("\"playerId\":\"" + playerId + "\"");
        assertThat(log).contains("\"originalSlot\":\"hotbar-0\"");
        assertThat(log).contains("\"reason\":\"restore \\\"failed\\\"\\nkept\"");
        assertThat(log).contains("\"summary\":\"type=DIAMOND\\nname=\\\"Keeper\\\"\"");
        assertThat(log).contains("\"backup\":\"backup-data\"");
    }

    @Test
    void recoveryStoreDoesNotTrackPendingEntryWhenAppendFails() throws Exception {
        Path fileInsteadOfDirectory = tempDir.resolve("not-a-directory");
        Files.writeString(fileInsteadOfDirectory, "occupied", StandardCharsets.UTF_8);
        UUID playerId = UUID.randomUUID();
        ClaimModeRecoveryStore store = new ClaimModeRecoveryStore(fileInsteadOfDirectory);
        ClaimModeRecoveryEntry entry = new ClaimModeRecoveryEntry(
                playerId,
                "Alice",
                Instant.parse("2026-06-15T10:15:30Z"),
                "hotbar-0",
                "type=DIAMOND",
                "backup-data",
                "restore failed"
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.add(entry))
                .isInstanceOf(IllegalStateException.class);

        assertThat(store.pendingFor(playerId)).isEmpty();
    }

    @Test
    void sessionHistoryTrimsOnlyOldSessionsForSamePlayer() throws Exception {
        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        ClaimModeSessionHistory history = new ClaimModeSessionHistory(tempDir, 2);

        history.append(aliceId, "Alice \"Owner\"", instant(1), instant(2), ClaimModeService.ExitReason.MANUAL,
                List.of(snapshot("hotbar-0", "alice-old")), List.of("restored old"));
        history.append(bobId, "Bob", instant(3), instant(4), ClaimModeService.ExitReason.LOGOUT,
                List.of(snapshot("hotbar-1", "bob \"kept\"\nwith newline")), List.of("restored \"bob\"\ncleanly"));
        history.append(aliceId, "Alice \"Owner\"", instant(5), instant(6), ClaimModeService.ExitReason.DEATH,
                List.of(snapshot("hotbar-2", "alice-kept-1")), List.of("restored alice 1"));
        history.append(aliceId, "Alice \"Owner\"", instant(7), instant(8), ClaimModeService.ExitReason.PLUGIN_DISABLE,
                List.of(snapshot("hotbar-3", "alice-kept-2")), List.of("restored alice 2"));

        String log = Files.readString(tempDir.resolve("logs").resolve("claimmode-history.log"), StandardCharsets.UTF_8);

        assertThat(log).doesNotContain("alice-old");
        assertThat(log).contains("\"event\":\"session-start\"");
        assertThat(log).contains("\"event\":\"session-item\"");
        assertThat(log).contains("\"event\":\"session-restore\"");
        assertThat(log).contains("\"event\":\"session-end\"");
        assertThat(log).contains("\"playerName\":\"Alice \\\"Owner\\\"\"");
        assertThat(log).contains("bob \\\"kept\\\"\\nwith newline");
        assertThat(log).contains("alice-kept-1");
        assertThat(log).contains("alice-kept-2");
        assertThat(log).contains("\"result\":\"restored \\\"bob\\\"\\ncleanly\"");
    }

    private static ItemStack item(Material material, int amount, ItemMeta meta) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(amount);
        when(item.getItemMeta()).thenReturn(meta);
        return item;
    }

    private static ItemMeta auditedMeta() {
        ItemMeta meta = mock(ItemMeta.class, withSettings().extraInterfaces(Damageable.class));
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.displayName()).thenReturn(Component.text("Trusty"));
        when(meta.hasLore()).thenReturn(true);
        when(meta.lore()).thenReturn(List.of(
                Component.text("first line"),
                Component.text("\"quoted\" line"),
                Component.text("third\nline"),
                Component.text("fourth line")
        ));
        when(meta.getEnchants()).thenReturn(Map.of());
        when(((Damageable) meta).getDamage()).thenReturn(12);
        return meta;
    }

    private static ClaimModeItemSnapshot snapshot(String slot, String summary) {
        return new ClaimModeItemSnapshot(slot, summary, "backup-" + summary);
    }

    private static Instant instant(int second) {
        return Instant.parse("2026-06-15T10:15:%02dZ".formatted(second));
    }
}
