package com.invisiblespiders.havenclaims.plugin.claimmode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

class ClaimModeServiceTest {
    private static final NamespacedKey TOOL_KEY = new NamespacedKey("havenclaims", "claim_mode_tool");
    private static final Component FALLBACK = Component.text("claim mode fallback");

    @TempDir
    Path tempDir;

    @Test
    void enterStoresHotbarAndOffhandThenPlacesTools() {
        PlayerFixture fixture = playerFixture();
        ItemStack diamond = item(Material.DIAMOND, 3, bytes(1));
        ItemStack shield = item(Material.SHIELD, 1, bytes(2));
        fixture.setStoredItem(0, diamond);
        fixture.setOffhand(shield);
        ClaimModeService service = service(true);

        ClaimModeService.EnterResult result = service.enter(fixture.player());

        assertThat(result).isEqualTo(ClaimModeService.EnterResult.ENTERED);
        assertThat(service.isInClaimMode(fixture.playerId())).isTrue();
        assertThat(fixture.slot(0)).matches(service.toolRegistry()::isClaimModeTool);
        assertThat(fixture.slot(1)).matches(service.toolRegistry()::isClaimModeTool);
        assertThat(fixture.offhand()).isNull();
    }

    @Test
    void enterLeavesArmorAndMainInventoryOutsideHotbarUntouched() {
        PlayerFixture fixture = playerFixture();
        ItemStack armor = item(Material.DIAMOND_CHESTPLATE, 1, bytes(3));
        ItemStack mainInventoryItem = item(Material.EMERALD, 12, bytes(4));
        fixture.setStoredItem(9, mainInventoryItem);
        when(fixture.inventory().getChestplate()).thenReturn(armor);
        ClaimModeService service = service(true);

        ClaimModeService.EnterResult result = service.enter(fixture.player());

        assertThat(result).isEqualTo(ClaimModeService.EnterResult.ENTERED);
        assertThat(fixture.slot(9)).isSameAs(mainInventoryItem);
        verify(fixture.inventory(), never()).setItem(eq(9), any());
        verify(fixture.inventory(), never()).setChestplate(any());
    }

    @Test
    void enterReturnsGuardResultsWithoutChangingInventory() {
        PlayerFixture disabled = playerFixture();
        ClaimModeService disabledService = service(false);

        assertThat(disabledService.enter(disabled.player())).isEqualTo(ClaimModeService.EnterResult.DISABLED);
        assertThat(disabledService.isInClaimMode(disabled.playerId())).isFalse();
        verify(disabled.inventory(), never()).setItem(anyInt(), any());

        PlayerFixture noPermission = playerFixture();
        when(noPermission.player().hasPermission(ClaimModeService.CLAIM_PERMISSION)).thenReturn(false);
        ClaimModeService noPermissionService = service(true);

        assertThat(noPermissionService.enter(noPermission.player())).isEqualTo(ClaimModeService.EnterResult.NO_PERMISSION);
        assertThat(noPermissionService.isInClaimMode(noPermission.playerId())).isFalse();
        verify(noPermission.inventory(), never()).setItem(anyInt(), any());

        PlayerFixture alreadyActive = playerFixture();
        ClaimModeService alreadyActiveService = service(true);
        assertThat(alreadyActiveService.enter(alreadyActive.player())).isEqualTo(ClaimModeService.EnterResult.ENTERED);

        assertThat(alreadyActiveService.enter(alreadyActive.player())).isEqualTo(ClaimModeService.EnterResult.ALREADY_ACTIVE);
        verify(alreadyActive.inventory(), times(2)).setItem(eq(0), any());
    }

    @Test
    void exitRemovesSessionAndRestoresExactSlots() throws Exception {
        PlayerFixture fixture = playerFixture();
        ItemStack diamondBackup = item(Material.DIAMOND, 3, bytes(10));
        ItemStack shieldBackup = item(Material.SHIELD, 1, bytes(11));
        ItemStack restoredDiamond = item(Material.DIAMOND, 3, bytes(12));
        ItemStack restoredShield = item(Material.SHIELD, 1, bytes(13));
        fixture.setStoredItem(0, diamondBackup);
        fixture.setOffhand(shieldBackup);
        ClaimModeService service = service(true);

        service.enter(fixture.player());
        try (MockedStatic<ItemStack> itemStacks = mockStatic(ItemStack.class)) {
            itemStacks.when(() -> ItemStack.deserializeBytes(bytes(10))).thenReturn(restoredDiamond);
            itemStacks.when(() -> ItemStack.deserializeBytes(bytes(11))).thenReturn(restoredShield);

            ClaimModeService.ExitResult result = service.exit(fixture.player(), ClaimModeService.ExitReason.MANUAL);

            assertThat(result).isEqualTo(ClaimModeService.ExitResult.RESTORED);
            assertThat(service.isInClaimMode(fixture.playerId())).isFalse();
            assertThat(fixture.slot(0)).isSameAs(restoredDiamond);
            assertThat(fixture.offhand()).isSameAs(restoredShield);
        }
        String history = Files.readString(tempDir.resolve("logs").resolve("claimmode-history.log"), StandardCharsets.UTF_8);
        assertThat(history).contains("\"event\":\"session-start\"");
        assertThat(history).contains("\"event\":\"session-restore\"");
        assertThat(history).contains("hotbar-0=exact");
        assertThat(history).contains("offhand=exact");
    }

    @Test
    void exitAddsItemElsewhereWhenOriginalSlotHasUnrelatedItem() {
        PlayerFixture fixture = playerFixture();
        ItemStack diamondBackup = item(Material.DIAMOND, 3, bytes(20));
        ItemStack restoredDiamond = item(Material.DIAMOND, 3, bytes(21));
        ItemStack unrelated = item(Material.STONE, 1, bytes(22));
        fixture.setStoredItem(0, diamondBackup);
        ClaimModeService service = service(true);

        service.enter(fixture.player());
        fixture.setStoredItem(0, unrelated);
        fixture.addItemResult(Map.of());
        try (MockedStatic<ItemStack> itemStacks = mockStatic(ItemStack.class)) {
            itemStacks.when(() -> ItemStack.deserializeBytes(bytes(20))).thenReturn(restoredDiamond);

            ClaimModeService.ExitResult result = service.exit(fixture.player(), ClaimModeService.ExitReason.LOGOUT);

            assertThat(result).isEqualTo(ClaimModeService.ExitResult.PARTIAL);
            assertThat(fixture.slot(0)).isSameAs(unrelated);
            assertThat(fixture.addedItems()).containsExactly(restoredDiamond);
        }
    }

    @Test
    void exitWritesRecoveryEntryWhenInventoryCannotAcceptLeftover() {
        PlayerFixture fixture = playerFixture();
        ItemStack diamondBackup = item(Material.DIAMOND, 3, bytes(30));
        ItemStack restoredDiamond = item(Material.DIAMOND, 3, bytes(31));
        ItemStack leftover = item(Material.DIAMOND, 3, bytes(32));
        ItemStack unrelated = item(Material.STONE, 1, bytes(33));
        fixture.setStoredItem(0, diamondBackup);
        ClaimModeRecoveryStore recoveryStore = new ClaimModeRecoveryStore(tempDir);
        ClaimModeService service = service(true, recoveryStore);

        service.enter(fixture.player());
        fixture.setStoredItem(0, unrelated);
        fixture.addItemResult(Map.of(0, leftover));
        try (MockedStatic<ItemStack> itemStacks = mockStatic(ItemStack.class)) {
            itemStacks.when(() -> ItemStack.deserializeBytes(bytes(30))).thenReturn(restoredDiamond);

            ClaimModeService.ExitResult result = service.exit(fixture.player(), ClaimModeService.ExitReason.DEATH);

            assertThat(result).isEqualTo(ClaimModeService.ExitResult.RECOVERY);
        }
        assertThat(recoveryStore.pendingFor(fixture.playerId()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.playerName()).isEqualTo("Alice");
                    assertThat(entry.originalSlot()).isEqualTo("hotbar-0");
                    assertThat(entry.reason()).isEqualTo("inventory-full");
                    assertThat(entry.summary()).contains("type=DIAMOND");
                });
    }

    @Test
    void restoreAllExitsOnlyActivePlayers() {
        PlayerFixture active = playerFixture("Alice");
        PlayerFixture inactive = playerFixture("Bob");
        ClaimModeService service = service(true);
        service.enter(active.player());

        service.restoreAll(List.of(active.player(), inactive.player()), ClaimModeService.ExitReason.PLUGIN_DISABLE);

        assertThat(service.isInClaimMode(active.playerId())).isFalse();
        assertThat(service.isInClaimMode(inactive.playerId())).isFalse();
        verify(active.inventory(), atLeast(2)).setItem(anyInt(), any());
        verify(inactive.inventory(), never()).setItem(anyInt(), any());
    }

    private ClaimModeService service(boolean enabled) {
        return service(enabled, new ClaimModeRecoveryStore(tempDir));
    }

    private ClaimModeService service(boolean enabled, ClaimModeRecoveryStore recoveryStore) {
        return new ClaimModeService(
                new ClaimModeConfig(enabled, 5, List.of("storage"), List.of("claimmode", "cm", "claim")),
                registry(),
                new ClaimModeSessionHistory(tempDir, 5),
                recoveryStore,
                FALLBACK
        );
    }

    private ClaimModeToolRegistry registry() {
        return new ClaimModeToolRegistry(TOOL_KEY, List.of(
                new ClaimModeTool("claim", 0, () -> claimModeItem(Material.GOLDEN_HOE), true, "", (player, event) -> {}),
                new ClaimModeTool("exit", 1, () -> claimModeItem(Material.BARRIER), true, "", (player, event) -> {})
        ));
    }

    private PlayerFixture playerFixture() {
        return playerFixture("Alice");
    }

    private PlayerFixture playerFixture(String playerName) {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        InventoryState inventoryState = new InventoryState(inventory);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn(playerName);
        when(player.getInventory()).thenReturn(inventory);
        when(player.hasPermission(ClaimModeService.CLAIM_PERMISSION)).thenReturn(true);
        inventoryState.installDefaultAnswers();
        return new PlayerFixture(player, inventory, inventoryState, playerId);
    }

    private static ItemStack item(Material material, int amount, byte[] backupBytes) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(amount);
        when(item.getItemMeta()).thenReturn(meta);
        when(item.serializeAsBytes()).thenReturn(backupBytes);
        when(meta.hasDisplayName()).thenReturn(false);
        when(meta.hasLore()).thenReturn(false);
        when(meta.getEnchants()).thenReturn(Map.of());
        return item;
    }

    private static ItemStack claimModeItem(Material material) {
        ItemStack template = mock(ItemStack.class);
        ItemStack itemStack = mock(ItemStack.class);
        ItemMeta itemMeta = mock(ItemMeta.class);
        PersistentDataContainer persistentDataContainer = mock(PersistentDataContainer.class);
        Map<NamespacedKey, String> values = new HashMap<>();

        when(template.clone()).thenReturn(itemStack);
        when(itemStack.getType()).thenReturn(material);
        when(itemStack.hasItemMeta()).thenReturn(true);
        when(itemStack.getItemMeta()).thenReturn(itemMeta);
        when(itemMeta.getPersistentDataContainer()).thenReturn(persistentDataContainer);
        when(persistentDataContainer.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                .thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(2));
            return null;
        }).when(persistentDataContainer).set(any(NamespacedKey.class), eq(PersistentDataType.STRING), any(String.class));
        return template;
    }

    private static byte[] bytes(int value) {
        return new byte[] {(byte) value};
    }

    private record PlayerFixture(Player player, PlayerInventory inventory, InventoryState inventoryState, UUID playerId) {
        void setStoredItem(int slot, ItemStack item) {
            inventoryState.setStoredItem(slot, item);
        }

        ItemStack slot(int slot) {
            return inventoryState.slot(slot);
        }

        void setOffhand(ItemStack item) {
            inventoryState.setOffhand(item);
        }

        ItemStack offhand() {
            return inventoryState.offhand();
        }

        void addItemResult(Map<Integer, ItemStack> result) {
            inventoryState.addItemResult(result);
        }

        List<ItemStack> addedItems() {
            return inventoryState.addedItems();
        }
    }

    private static final class InventoryState {
        private final PlayerInventory inventory;
        private final Map<Integer, ItemStack> slots = new HashMap<>();
        private final List<ItemStack> addedItems = new java.util.ArrayList<>();
        private ItemStack offhand;
        private HashMap<Integer, ItemStack> addItemResult = new HashMap<>();

        private InventoryState(PlayerInventory inventory) {
            this.inventory = inventory;
        }

        void installDefaultAnswers() {
            when(inventory.getItem(anyInt())).thenAnswer(invocation -> slots.get(invocation.getArgument(0)));
            doAnswer(invocation -> {
                slots.put(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(inventory).setItem(anyInt(), any());
            when(inventory.getItemInOffHand()).thenAnswer(invocation -> offhand);
            doAnswer(invocation -> {
                offhand = invocation.getArgument(0);
                return null;
            }).when(inventory).setItemInOffHand(any());
            when(inventory.addItem(any(ItemStack.class))).thenAnswer(invocation -> {
                addedItems.add(invocation.getArgument(0));
                return addItemResult;
            });
        }

        void setStoredItem(int slot, ItemStack item) {
            slots.put(slot, item);
        }

        ItemStack slot(int slot) {
            return slots.get(slot);
        }

        void setOffhand(ItemStack item) {
            offhand = item;
        }

        ItemStack offhand() {
            return offhand;
        }

        void addItemResult(Map<Integer, ItemStack> result) {
            addItemResult = new HashMap<>(result);
        }

        List<ItemStack> addedItems() {
            return addedItems;
        }
    }
}
