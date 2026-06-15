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
                "Alice",
                Instant.parse("2026-06-15T10:15:30Z"),
                "hotbar-0",
                "type=DIAMOND",
                "backup-data",
                "restore failed"
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
        assertThat(log).contains("2026-06-15T10:15:30Z");
        assertThat(log).contains("player=Alice");
        assertThat(log).contains("uuid=" + playerId);
        assertThat(log).contains("slot=hotbar-0");
        assertThat(log).contains("reason=\"restore failed\"");
        assertThat(log).contains("summary=\"type=DIAMOND\"");
        assertThat(log).contains("backup=backup-data");
    }

    @Test
    void sessionHistoryTrimsOnlyOldSessionsForSamePlayer() throws Exception {
        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        ClaimModeSessionHistory history = new ClaimModeSessionHistory(tempDir, 2);

        history.append(aliceId, "Alice", instant(1), instant(2), ClaimModeService.ExitReason.MANUAL,
                List.of(snapshot("hotbar-0", "alice-old")), List.of("restored old"));
        history.append(bobId, "Bob", instant(3), instant(4), ClaimModeService.ExitReason.LOGOUT,
                List.of(snapshot("hotbar-1", "bob-kept")), List.of("restored bob"));
        history.append(aliceId, "Alice", instant(5), instant(6), ClaimModeService.ExitReason.DEATH,
                List.of(snapshot("hotbar-2", "alice-kept-1")), List.of("restored alice 1"));
        history.append(aliceId, "Alice", instant(7), instant(8), ClaimModeService.ExitReason.PLUGIN_DISABLE,
                List.of(snapshot("hotbar-3", "alice-kept-2")), List.of("restored alice 2"));

        String log = Files.readString(tempDir.resolve("logs").resolve("claimmode-history.log"), StandardCharsets.UTF_8);

        assertThat(log).doesNotContain("alice-old");
        assertThat(log).contains("bob-kept");
        assertThat(log).contains("alice-kept-1");
        assertThat(log).contains("alice-kept-2");
        assertThat(log).contains("restore restored bob");
        assertThat(log).contains("end-session");
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
        when(meta.lore()).thenReturn(List.of(Component.text("audit me")));
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
