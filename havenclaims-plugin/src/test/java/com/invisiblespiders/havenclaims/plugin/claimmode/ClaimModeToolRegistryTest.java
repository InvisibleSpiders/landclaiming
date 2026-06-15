package com.invisiblespiders.havenclaims.plugin.claimmode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class ClaimModeToolRegistryTest {
    private static final NamespacedKey TOOL_KEY = new NamespacedKey("havenclaims", "claim_mode_tool");

    @Test
    void tagsAndResolvesRegisteredToolItems() {
        ClaimModeTool claimTool = new ClaimModeTool(
                "claim",
                0,
                ClaimModeToolRegistryTest::claimModeItem,
                true,
                "",
                (player, event) -> {}
        );
        ClaimModeToolRegistry registry = new ClaimModeToolRegistry(TOOL_KEY, List.of(claimTool));

        ItemStack item = registry.createItem("claim");

        assertThat(registry.resolve(item)).contains(claimTool);
        assertThat(registry.isClaimModeTool(item)).isTrue();
    }

    @Test
    void looksUpRegisteredToolsById() {
        ClaimModeTool claimTool = new ClaimModeTool(
                "claim",
                0,
                ClaimModeToolRegistryTest::claimModeItem,
                true,
                "",
                (player, event) -> {}
        );
        ClaimModeToolRegistry registry = new ClaimModeToolRegistry(TOOL_KEY, List.of(claimTool));

        assertThat(registry.toolById("claim")).contains(claimTool);
        assertThat(registry.toolById("missing")).isEmpty();
    }

    @Test
    void rejectsDuplicateToolIds() {
        ClaimModeTool first = new ClaimModeTool("same", 0, () -> new ItemStack(Material.STICK), true, "", (player, event) -> {});
        ClaimModeTool second = new ClaimModeTool("same", 1, () -> new ItemStack(Material.BLAZE_ROD), true, "", (player, event) -> {});

        assertThatThrownBy(() -> new ClaimModeToolRegistry(TOOL_KEY, List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void rejectsDuplicateToolSlots() {
        ClaimModeTool first = new ClaimModeTool("first", 0, () -> new ItemStack(Material.STICK), true, "", (player, event) -> {});
        ClaimModeTool second = new ClaimModeTool("second", 0, () -> new ItemStack(Material.BLAZE_ROD), true, "", (player, event) -> {});

        assertThatThrownBy(() -> new ClaimModeToolRegistry(TOOL_KEY, List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slot");
    }

    @Test
    void standardToolsUseExpectedSlots() {
        ClaimModeToolRegistry registry = StandardClaimModeTools.createRegistry(TOOL_KEY);

        assertThat(registry.toolsBySlot().keySet()).containsExactlyInAnyOrder(0, 1, 7, 8);
        assertThat(registry.toolsBySlot().get(1).enabled()).isFalse();
    }

    private static ItemStack claimModeItem() {
        ItemStack itemStack = mock(ItemStack.class);
        ItemMeta itemMeta = mock(ItemMeta.class);
        PersistentDataContainer persistentDataContainer = mock(PersistentDataContainer.class);
        Map<NamespacedKey, String> values = new HashMap<>();

        when(itemStack.hasItemMeta()).thenReturn(true);
        when(itemStack.getItemMeta()).thenReturn(itemMeta);
        when(itemMeta.getPersistentDataContainer()).thenReturn(persistentDataContainer);
        when(persistentDataContainer.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                .thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(2));
            return null;
        }).when(persistentDataContainer).set(any(NamespacedKey.class), eq(PersistentDataType.STRING), any(String.class));
        return itemStack;
    }
}
