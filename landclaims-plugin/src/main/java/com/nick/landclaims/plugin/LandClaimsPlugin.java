package com.nick.landclaims.plugin;

import com.nick.landclaims.plugin.claim.ClaimService;
import com.nick.landclaims.plugin.command.ClaimsCommand;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import com.nick.landclaims.plugin.listener.ClaimToolListener;
import com.nick.landclaims.plugin.listener.ProtectionListener;
import com.nick.landclaims.plugin.protection.ProtectionService;
import com.nick.landclaims.plugin.recipe.ClaimToolRecipeService;
import com.nick.landclaims.plugin.selection.SelectionService;
import com.nick.landclaims.plugin.tool.ClaimToolService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class LandClaimsPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("storage.yml");
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("permissions.yml");
        saveResourceIfMissing("tool.yml");
        saveResourceIfMissing("recipes.yml");

        ClaimService claimService = new ClaimService();
        ClaimToolService claimToolService = new ClaimToolService(this);
        FlagRegistry flagRegistry = FlagRegistry.createDefault();
        ProtectionService protectionService = new ProtectionService(flagRegistry);
        SelectionService selectionService = new SelectionService(claimService);
        new ClaimToolRecipeService(this, claimToolService).register(loadYamlResource("recipes.yml"));

        getServer().getPluginManager().registerEvents(
                new ClaimToolListener(claimToolService, selectionService),
                this
        );
        getServer().getPluginManager().registerEvents(
                new ProtectionListener(protectionService),
                this
        );
        Objects.requireNonNull(getCommand("claims"), "claims command is not defined in plugin.yml")
                .setExecutor(new ClaimsCommand(claimToolService));

        getLogger().info("LandClaims enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("LandClaims disabled.");
    }

    private void saveResourceIfMissing(String resourceName) {
        Path dataFolder = getDataFolder().toPath();
        Path resourcePath = dataFolder.resolve(resourceName);
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create plugin data folder.", exception);
        }

        if (Files.notExists(resourcePath)) {
            saveResource(resourceName, false);
        }
    }

    private YamlConfiguration loadYamlResource(String resourceName) {
        File resourceFile = getDataFolder().toPath().resolve(resourceName).toFile();
        return YamlConfiguration.loadConfiguration(resourceFile);
    }
}
