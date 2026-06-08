package com.nick.landclaims.plugin;

import com.nick.landclaims.plugin.claim.ClaimCreationService;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.ClaimService;
import com.nick.landclaims.plugin.command.ClaimsCommand;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import com.nick.landclaims.plugin.listener.ClaimToolListener;
import com.nick.landclaims.plugin.listener.ProtectionListener;
import com.nick.landclaims.plugin.protection.ProtectionService;
import com.nick.landclaims.plugin.recipe.ClaimToolRecipeService;
import com.nick.landclaims.plugin.selection.DoubleCrouchClearService;
import com.nick.landclaims.plugin.selection.SelectionService;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import com.nick.landclaims.plugin.storage.sql.SqlClaimRepository;
import com.nick.landclaims.plugin.tool.ClaimToolService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.sqlite.SQLiteDataSource;

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
        ClaimRepository claimRepository = createClaimRepository(loadYamlResource("storage.yml"));
        claimRepository.initialize();
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.load(claimRepository.findAllClaims());
        ClaimCreationService claimCreationService = new ClaimCreationService(
                claimRepository,
                claimIndex,
                claimService,
                flagRegistry,
                getConfig().getInt("claiming.player-buffer-distance", 3),
                getConfig().getInt("claiming.admin-buffer-distance", 3),
                getConfig().getInt("claiming.max-name-length", 32)
        );
        DoubleCrouchClearService doubleCrouchClearService = new DoubleCrouchClearService(
                getConfig().getInt("selection.double-crouch-clear.window-ticks", 80),
                () -> getServer().getCurrentTick()
        );
        new ClaimToolRecipeService(this, claimToolService).register(loadYamlResource("recipes.yml"));

        getServer().getPluginManager().registerEvents(
                new ClaimToolListener(
                        claimToolService,
                        selectionService,
                        doubleCrouchClearService,
                        getConfig().getBoolean("selection.clear-on-tool-switch", true),
                        getConfig().getBoolean("selection.double-crouch-clear.enabled", true)
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new ProtectionListener(protectionService, claimIndex),
                this
        );
        Objects.requireNonNull(getCommand("claims"), "claims command is not defined in plugin.yml")
                .setExecutor(new ClaimsCommand(claimToolService, selectionService, claimCreationService, claimIndex));

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

    private ClaimRepository createClaimRepository(YamlConfiguration storageConfiguration) {
        String storageType = storageConfiguration.getString("storage.type", "sqlite");
        if (!"sqlite".equalsIgnoreCase(storageType)) {
            throw new IllegalStateException("Only sqlite storage is currently implemented for playable claim creation.");
        }

        String sqliteFile = storageConfiguration.getString("storage.sqlite.file", "landclaims.db");
        Path configuredPath = Path.of(sqliteFile);
        Path databasePath = configuredPath.isAbsolute()
                ? configuredPath
                : getDataFolder().toPath().resolve(configuredPath);
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + databasePath.toAbsolutePath());
        return new SqlClaimRepository(dataSource);
    }
}
