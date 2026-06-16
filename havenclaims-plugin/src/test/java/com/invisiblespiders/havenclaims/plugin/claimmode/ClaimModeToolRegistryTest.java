package com.invisiblespiders.havenclaims.plugin.claimmode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
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
    void looksUpRegisteredToolsBySlot() {
        ClaimModeTool claimTool = new ClaimModeTool(
                "claim",
                0,
                ClaimModeToolRegistryTest::claimModeItem,
                true,
                "",
                (player, event) -> {}
        );
        ClaimModeToolRegistry registry = new ClaimModeToolRegistry(TOOL_KEY, List.of(claimTool));

        assertThat(registry.toolBySlot(0)).contains(claimTool);
        assertThat(registry.toolBySlot(1)).isEmpty();
    }

    @Test
    void clonesFactoryItemsBeforeTagging() {
        ItemStack template = mock(ItemStack.class);
        ItemStack firstItem = claimModeItem();
        ItemStack secondItem = claimModeItem();
        when(template.clone()).thenReturn(firstItem, secondItem);
        ClaimModeTool claimTool = new ClaimModeTool(
                "claim",
                0,
                () -> template,
                true,
                "",
                (player, event) -> {}
        );
        ClaimModeToolRegistry registry = new ClaimModeToolRegistry(TOOL_KEY, List.of(claimTool));

        ItemStack first = registry.createItem("claim");
        ItemStack second = registry.createItem("claim");

        assertThat(first).isSameAs(firstItem);
        assertThat(second).isSameAs(secondItem);
        assertThat(first).isNotSameAs(second);
        assertThat(registry.resolve(first)).contains(claimTool);
        assertThat(registry.resolve(second)).contains(claimTool);
        verify(template, times(2)).clone();
    }

    @Test
    void rejectsInvalidRegistryInputs() {
        ClaimModeTool claimTool = new ClaimModeTool(
                "claim",
                0,
                ClaimModeToolRegistryTest::claimModeItem,
                true,
                "",
                (player, event) -> {}
        );

        assertThatThrownBy(() -> new ClaimModeToolRegistry(null, List.of(claimTool)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("toolKey");
        assertThatThrownBy(() -> new ClaimModeToolRegistry(TOOL_KEY, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tools");
        assertThatThrownBy(() -> new ClaimModeToolRegistry(TOOL_KEY, Collections.singletonList(null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tool");
    }

    @Test
    void rejectsInvalidFactoryOutput() {
        ClaimModeTool nullFactoryTool = new ClaimModeTool(
                "claim",
                0,
                () -> null,
                true,
                "",
                (player, event) -> {}
        );
        ClaimModeTool metaLessTool = new ClaimModeTool(
                "metaless",
                1,
                ClaimModeToolRegistryTest::metaLessItem,
                true,
                "",
                (player, event) -> {}
        );
        ClaimModeToolRegistry registry = new ClaimModeToolRegistry(TOOL_KEY, List.of(nullFactoryTool, metaLessTool));

        assertThatThrownBy(() -> registry.createItem("claim"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("itemFactory")
                .hasMessageContaining("claim");
        assertThatThrownBy(() -> registry.createItem("metaless"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("item meta")
                .hasMessageContaining("metaless");
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
        ItemStack template = mock(ItemStack.class);
        ItemStack itemStack = mock(ItemStack.class);
        ItemMeta itemMeta = mock(ItemMeta.class);
        PersistentDataContainer persistentDataContainer = mock(PersistentDataContainer.class);
        Map<NamespacedKey, String> values = new HashMap<>();

        when(template.clone()).thenReturn(itemStack);
        when(template.hasItemMeta()).thenReturn(true);
        when(template.getItemMeta()).thenReturn(itemMeta);
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

    private static ItemStack metaLessItem() {
        ItemStack template = mock(ItemStack.class);
        ItemStack itemStack = mock(ItemStack.class);
        when(template.clone()).thenReturn(itemStack);
        when(itemStack.hasItemMeta()).thenReturn(false);
        when(itemStack.getItemMeta()).thenReturn(null);
        return template;
    }
}
