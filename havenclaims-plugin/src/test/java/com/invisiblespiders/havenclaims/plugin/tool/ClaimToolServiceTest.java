package com.invisiblespiders.havenclaims.plugin.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void createClaimModeToolRequiresConfiguredRegistry() {
        ClaimToolService service = new ClaimToolService("havenclaims");

        assertThatThrownBy(service::createClaimModeTool)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Claim mode tool registry is not configured.");
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
    void claimToolIdentityIsLimitedToClaimModeClaimTool() {
        ClaimToolService service = new ClaimToolService("havenclaims");
        ClaimModeToolRegistry registry = new ClaimModeToolRegistry(
                new NamespacedKey("havenclaims", "claim_mode_tool"),
                List.of(
                        new ClaimModeTool("claim", 0, () -> toolWithClaimModeId("claim"), true, "", (player, event) -> {}),
                        new ClaimModeTool("menu", 7, () -> toolWithClaimModeId("menu"), true, "", (player, event) -> {})
                )
        );
        service.setClaimModeToolRegistry(registry);
        ItemStack legacyChargedTool = legacyChargedTool();
        ItemStack claimModeClaimTool = registry.createItem("claim");
        ItemStack menuTool = registry.createItem("menu");

        assertThat(service.isClaimTool(legacyChargedTool)).isFalse();
        assertThat(service.isClaimTool(claimModeClaimTool)).isTrue();
        assertThat(service.isClaimTool(menuTool)).isFalse();

        assertThat(service.isClaimModeClaimTool(legacyChargedTool)).isFalse();
        assertThat(service.isClaimModeClaimTool(claimModeClaimTool)).isTrue();
        assertThat(service.isClaimModeClaimTool(menuTool)).isFalse();
    }

    private static ItemStack legacyChargedTool() {
        ItemStack itemStack = mock(ItemStack.class);
        ItemMeta itemMeta = mock(ItemMeta.class);
        PersistentDataContainer persistentDataContainer = mock(PersistentDataContainer.class);

        when(itemStack.hasItemMeta()).thenReturn(true);
        when(itemStack.getItemMeta()).thenReturn(itemMeta);
        when(itemMeta.getPersistentDataContainer()).thenReturn(persistentDataContainer);
        when(persistentDataContainer.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                .thenReturn(null);
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
