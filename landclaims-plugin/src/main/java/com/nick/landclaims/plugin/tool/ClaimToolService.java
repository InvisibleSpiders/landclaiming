package com.nick.landclaims.plugin.tool;

import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class ClaimToolService {
    private static final int DEFAULT_MAX_CHARGES = 25;

    private final NamespacedKey currentChargesKey;
    private final NamespacedKey maxChargesKey;

    public ClaimToolService(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        this.currentChargesKey = new NamespacedKey(plugin, "claim_tool_current_charges");
        this.maxChargesKey = new NamespacedKey(plugin, "claim_tool_max_charges");
    }

    public ItemStack createClaimTool() {
        return createClaimTool(DEFAULT_MAX_CHARGES);
    }

    public ItemStack createClaimTool(int maxCharges) {
        if (maxCharges < 1) {
            throw new IllegalArgumentException("maxCharges must be at least 1");
        }

        ItemStack itemStack = new ItemStack(Material.GOLDEN_HOE);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.displayName(Component.text("Claiming Hoe", NamedTextColor.GOLD));
        itemMeta.lore(List.of(
                Component.text("Charges: ", NamedTextColor.GRAY)
                        .append(Component.text(maxCharges, NamedTextColor.YELLOW))
                        .append(Component.text("/", NamedTextColor.GRAY))
                        .append(Component.text(maxCharges, NamedTextColor.YELLOW)),
                Component.text("Right-click two chunks to select land.", NamedTextColor.GRAY)
        ));

        PersistentDataContainer persistentDataContainer = itemMeta.getPersistentDataContainer();
        persistentDataContainer.set(currentChargesKey, PersistentDataType.INTEGER, maxCharges);
        persistentDataContainer.set(maxChargesKey, PersistentDataType.INTEGER, maxCharges);
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    public boolean isClaimTool(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }

        return itemStack.getItemMeta()
                .getPersistentDataContainer()
                .has(currentChargesKey, PersistentDataType.INTEGER);
    }
}
