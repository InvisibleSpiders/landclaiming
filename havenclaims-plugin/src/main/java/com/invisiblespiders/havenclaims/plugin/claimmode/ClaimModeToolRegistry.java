package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class ClaimModeToolRegistry {
    private final NamespacedKey toolKey;
    private final Map<String, ClaimModeTool> toolsById;
    private final Map<Integer, ClaimModeTool> toolsBySlot;

    public ClaimModeToolRegistry(NamespacedKey toolKey, List<ClaimModeTool> tools) {
        this.toolKey = toolKey;
        Map<String, ClaimModeTool> byId = new LinkedHashMap<>();
        Map<Integer, ClaimModeTool> bySlot = new LinkedHashMap<>();
        for (ClaimModeTool tool : tools) {
            if (byId.putIfAbsent(tool.id(), tool) != null) {
                throw new IllegalArgumentException("Duplicate claim mode tool id: " + tool.id());
            }
            if (bySlot.putIfAbsent(tool.slot(), tool) != null) {
                throw new IllegalArgumentException("Duplicate claim mode tool slot: " + tool.slot());
            }
        }
        this.toolsById = Map.copyOf(byId);
        this.toolsBySlot = Map.copyOf(bySlot);
    }

    public Map<Integer, ClaimModeTool> toolsBySlot() {
        return toolsBySlot;
    }

    public Optional<ClaimModeTool> toolById(String id) {
        return Optional.ofNullable(toolsById.get(id));
    }

    public ItemStack createItem(String id) {
        ClaimModeTool tool = toolsById.get(id);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown claim mode tool: " + id);
        }
        ItemStack item = tool.itemFactory().get();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    public Optional<ClaimModeTool> resolve(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        return Optional.ofNullable(toolsById.get(id));
    }

    public boolean isClaimModeTool(ItemStack item) {
        return resolve(item).isPresent();
    }
}
