package com.nick.landclaims.plugin.recipe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ClaimToolRecipeConfigTest {
    @Test
    void readsEnabledClaimToolRecipeFromConfiguration() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("claim-tool.enabled", true);
        configuration.set("claim-tool.shape", java.util.List.of(" G ", " S ", " S "));
        configuration.set("claim-tool.ingredients.G", "GOLD_INGOT");
        configuration.set("claim-tool.ingredients.S", "STICK");
        configuration.set("claim-tool.result-charges", 17);

        ClaimToolRecipeConfig recipeConfig = ClaimToolRecipeConfig.from(configuration);

        assertThat(recipeConfig.enabled()).isTrue();
        assertThat(recipeConfig.shape()).containsExactly(" G ", " S ", " S ");
        assertThat(recipeConfig.ingredients()).isEqualTo(Map.of(
                'G', Material.GOLD_INGOT,
                'S', Material.STICK
        ));
        assertThat(recipeConfig.resultCharges()).isEqualTo(17);
    }
}
