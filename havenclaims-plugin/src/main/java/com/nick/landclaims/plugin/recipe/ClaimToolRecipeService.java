package com.nick.landclaims.plugin.recipe;

import com.nick.landclaims.plugin.tool.ClaimToolService;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

public final class ClaimToolRecipeService {
    private final Plugin plugin;
    private final ClaimToolService claimToolService;
    private final NamespacedKey recipeKey;

    public ClaimToolRecipeService(Plugin plugin, ClaimToolService claimToolService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.claimToolService = Objects.requireNonNull(claimToolService, "claimToolService");
        this.recipeKey = new NamespacedKey(plugin, "claim_tool");
    }

    public void register(FileConfiguration configuration) {
        ClaimToolRecipeConfig recipeConfig = ClaimToolRecipeConfig.from(configuration);
        plugin.getServer().removeRecipe(recipeKey);

        if (!recipeConfig.enabled()) {
            plugin.getLogger().info("Claim tool recipe is disabled.");
            return;
        }

        ShapedRecipe shapedRecipe = new ShapedRecipe(
                recipeKey,
                claimToolService.createClaimTool(recipeConfig.resultCharges())
        );
        shapedRecipe.shape(recipeConfig.shape().toArray(String[]::new));
        recipeConfig.ingredients().forEach(shapedRecipe::setIngredient);
        plugin.getServer().addRecipe(shapedRecipe);
        plugin.getLogger().info("Registered claim tool recipe.");
    }
}
