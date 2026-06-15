package com.invisiblespiders.havenclaims.plugin.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeTool;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeToolRegistry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class ClaimToolServiceTest {
    @Test
    void spendsChargesWhenEnoughRemain() {
        ClaimToolService service = new ClaimToolService("havenclaims");
        ItemStack tool = toolWithCharges(5, 5);

        assertThat(service.currentCharges(tool)).isEqualTo(5);
        assertThat(service.spendCharges(tool, 3)).isTrue();
        assertThat(service.currentCharges(tool)).isEqualTo(2);
    }

    @Test
    void doesNotSpendChargesWhenNotEnoughRemain() {
        ClaimToolService service = new ClaimToolService("havenclaims");
        ItemStack tool = toolWithCharges(2, 2);

        assertThat(service.spendCharges(tool, 3)).isFalse();

        assertThat(service.currentCharges(tool)).isEqualTo(2);
    }

    @Test
    void recognizesClaimModeClaimTool() {
        ClaimToolService service = new ClaimToolService("havenclaims");
        ClaimModeToolRegistry registry = new ClaimModeToolRegistry(
                new NamespacedKey("havenclaims", "claim_mode_tool"),
                List.of(
                        new ClaimModeTool("claim", 0, () -> toolWithClaimModeId("claim"), true, "", (player, event) -> {}),
                        new ClaimModeTool("menu", 7, () -> toolWithClaimModeId("menu"), true, "", (player, event) -> {})
                )
        );
        service.setClaimModeToolRegistry(registry);

        assertThat(service.isClaimTool(registry.createItem("claim"))).isTrue();
        assertThat(service.isClaimTool(registry.createItem("menu"))).isFalse();
    }

    @Test
    void distinguishesChargedAndClaimModeClaimTools() {
        ClaimToolService service = new ClaimToolService("havenclaims");
        ClaimModeToolRegistry registry = new ClaimModeToolRegistry(
                new NamespacedKey("havenclaims", "claim_mode_tool"),
                List.of(
                        new ClaimModeTool("claim", 0, () -> toolWithClaimModeId("claim"), true, "", (player, event) -> {}),
                        new ClaimModeTool("menu", 7, () -> toolWithClaimModeId("menu"), true, "", (player, event) -> {})
                )
        );
        service.setClaimModeToolRegistry(registry);
        ItemStack chargedTool = toolWithCharges(5, 5);
        ItemStack claimModeClaimTool = registry.createItem("claim");
        ItemStack menuTool = registry.createItem("menu");

        assertThat(service.isClaimTool(chargedTool)).isTrue();
        assertThat(service.isClaimTool(claimModeClaimTool)).isTrue();
        assertThat(service.isClaimTool(menuTool)).isFalse();

        assertThat(service.isChargedClaimTool(chargedTool)).isTrue();
        assertThat(service.isChargedClaimTool(claimModeClaimTool)).isFalse();
        assertThat(service.isChargedClaimTool(menuTool)).isFalse();

        assertThat(service.isClaimModeClaimTool(chargedTool)).isFalse();
        assertThat(service.isClaimModeClaimTool(claimModeClaimTool)).isTrue();
        assertThat(service.isClaimModeClaimTool(menuTool)).isFalse();
    }

    private static ItemStack toolWithCharges(int currentCharges, int maxCharges) {
        ItemStack itemStack = mock(ItemStack.class);
        ItemMeta itemMeta = mock(ItemMeta.class);
        PersistentDataContainer persistentDataContainer = mock(PersistentDataContainer.class);
        Map<NamespacedKey, Integer> values = new HashMap<>();
        values.put(new NamespacedKey("havenclaims", "claim_tool_current_charges"), currentCharges);
        values.put(new NamespacedKey("havenclaims", "claim_tool_max_charges"), maxCharges);

        when(itemStack.hasItemMeta()).thenReturn(true);
        when(itemStack.getItemMeta()).thenReturn(itemMeta);
        when(itemMeta.getPersistentDataContainer()).thenReturn(persistentDataContainer);
        when(persistentDataContainer.has(any(NamespacedKey.class), eq(PersistentDataType.INTEGER)))
                .thenAnswer(invocation -> values.containsKey(invocation.getArgument(0)));
        when(persistentDataContainer.get(any(NamespacedKey.class), eq(PersistentDataType.INTEGER)))
                .thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(2));
            return null;
        }).when(persistentDataContainer).set(any(NamespacedKey.class), eq(PersistentDataType.INTEGER), any(Integer.class));
        return itemStack;
    }

    private static ItemStack toolWithClaimModeId(String id) {
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
        }).when(persistentDataContainer).set(any(NamespacedKey.class), eq(PersistentDataType.STRING), eq(id));
        return template;
    }
}
