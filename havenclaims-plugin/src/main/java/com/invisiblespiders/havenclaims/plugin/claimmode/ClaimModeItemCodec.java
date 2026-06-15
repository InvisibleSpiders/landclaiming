package com.invisiblespiders.havenclaims.plugin.claimmode;

import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public final class ClaimModeItemCodec {
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private ClaimModeItemCodec() {
    }

    public static String serialize(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "";
        }
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public static ItemStack deserialize(String backup) {
        if (backup == null || backup.isBlank()) {
            return null;
        }
        byte[] bytes = Base64.getDecoder().decode(backup);
        return ItemStack.deserializeBytes(bytes);
    }

    public static String summary(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return "empty";
        }

        ItemMeta meta = item.getItemMeta();
        String name = displayName(meta);
        int damage = meta instanceof Damageable damageable ? damageable.getDamage() : 0;
        Map<String, Integer> enchantments = enchantments(meta);

        return "type=" + item.getType()
                + ", amount=" + item.getAmount()
                + ", damage=" + damage
                + ", name=" + name
                + ", enchantments=" + enchantments;
    }

    private static String displayName(ItemMeta meta) {
        if (meta == null || !meta.hasDisplayName()) {
            return "";
        }
        Component displayName = meta.displayName();
        return displayName == null ? "" : PLAIN_TEXT.serialize(displayName);
    }

    private static Map<String, Integer> enchantments(ItemMeta meta) {
        Map<String, Integer> enchantments = new TreeMap<>();
        if (meta == null) {
            return enchantments;
        }
        for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
            enchantments.put(entry.getKey().getKey().toString(), entry.getValue());
        }
        return enchantments;
    }
}
