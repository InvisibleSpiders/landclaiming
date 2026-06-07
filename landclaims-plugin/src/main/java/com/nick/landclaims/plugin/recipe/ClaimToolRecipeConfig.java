package com.nick.landclaims.plugin.recipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record ClaimToolRecipeConfig(
        boolean enabled,
        List<String> shape,
        Map<Character, Material> ingredients,
        int resultCharges
) {
    public ClaimToolRecipeConfig {
        shape = List.copyOf(Objects.requireNonNull(shape, "shape"));
        ingredients = Map.copyOf(Objects.requireNonNull(ingredients, "ingredients"));
        if (resultCharges < 1) {
            throw new IllegalArgumentException("resultCharges must be at least 1");
        }
    }

    public static ClaimToolRecipeConfig from(FileConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");

        boolean enabled = configuration.getBoolean("claim-tool.enabled", true);
        List<String> shape = configuration.getStringList("claim-tool.shape");
        ConfigurationSection ingredientsSection = configuration.getConfigurationSection("claim-tool.ingredients");
        Map<Character, Material> ingredients = readIngredients(ingredientsSection);
        int resultCharges = configuration.getInt("claim-tool.result-charges", 25);

        return new ClaimToolRecipeConfig(enabled, shape, ingredients, resultCharges);
    }

    private static Map<Character, Material> readIngredients(ConfigurationSection ingredientsSection) {
        if (ingredientsSection == null) {
            return Map.of();
        }

        Map<Character, Material> ingredients = new LinkedHashMap<>();
        for (String key : ingredientsSection.getKeys(false)) {
            if (key.length() != 1) {
                throw new IllegalArgumentException("Recipe ingredient keys must be exactly one character: " + key);
            }

            String materialName = ingredientsSection.getString(key);
            Material material = Material.matchMaterial(Objects.requireNonNull(materialName, key));
            if (material == null) {
                throw new IllegalArgumentException("Unknown recipe material for key " + key + ": " + materialName);
            }

            ingredients.put(key.charAt(0), material);
        }
        return ingredients;
    }
}
