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

    ClaimToolService(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        this.currentChargesKey = new NamespacedKey(namespace, "claim_tool_current_charges");
        this.maxChargesKey = new NamespacedKey(namespace, "claim_tool_max_charges");
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

        PersistentDataContainer persistentDataContainer = itemMeta.getPersistentDataContainer();
        persistentDataContainer.set(currentChargesKey, PersistentDataType.INTEGER, maxCharges);
        persistentDataContainer.set(maxChargesKey, PersistentDataType.INTEGER, maxCharges);
        itemStack.setItemMeta(itemMeta);
        updateLore(itemStack, maxCharges, maxCharges);
        return itemStack;
    }

    public int currentCharges(ItemStack itemStack) {
        if (!isClaimTool(itemStack)) {
            return 0;
        }

        Integer charges = itemStack.getItemMeta()
                .getPersistentDataContainer()
                .get(currentChargesKey, PersistentDataType.INTEGER);
        return charges == null ? 0 : charges;
    }

    public int maxCharges(ItemStack itemStack) {
        if (!isClaimTool(itemStack)) {
            return 0;
        }

        Integer charges = itemStack.getItemMeta()
                .getPersistentDataContainer()
                .get(maxChargesKey, PersistentDataType.INTEGER);
        return charges == null ? 0 : charges;
    }

    public boolean spendCharges(ItemStack itemStack, int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be at least 1");
        }

        int currentCharges = currentCharges(itemStack);
        if (!isClaimTool(itemStack) || currentCharges < amount) {
            return false;
        }

        int remainingCharges = currentCharges - amount;
        int maxCharges = maxCharges(itemStack);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.getPersistentDataContainer().set(currentChargesKey, PersistentDataType.INTEGER, remainingCharges);
        itemStack.setItemMeta(itemMeta);
        updateLore(itemStack, remainingCharges, maxCharges);
        return true;
    }

    public boolean isClaimTool(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }

        return itemStack.getItemMeta()
                .getPersistentDataContainer()
                .has(currentChargesKey, PersistentDataType.INTEGER);
    }

    private void updateLore(ItemStack itemStack, int currentCharges, int maxCharges) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.lore(List.of(
                Component.text("Charges: ", NamedTextColor.GRAY)
                        .append(Component.text(currentCharges, NamedTextColor.YELLOW))
                        .append(Component.text("/", NamedTextColor.GRAY))
                        .append(Component.text(maxCharges, NamedTextColor.YELLOW)),
                Component.text("Right-click two chunks to select land.", NamedTextColor.GRAY)
        ));
        itemStack.setItemMeta(itemMeta);
    }
}
