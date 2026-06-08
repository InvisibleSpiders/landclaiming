package com.nick.landclaims.plugin;

import com.nick.landclaims.api.LandClaimsApi;
import com.nick.landclaims.plugin.api.BukkitLandClaimsApi;
import com.nick.landclaims.plugin.claim.ClaimCreationService;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.ClaimMemberService;
import com.nick.landclaims.plugin.claim.ClaimService;
import com.nick.landclaims.plugin.claim.PendingClaimMergeService;
import com.nick.landclaims.plugin.command.ClaimsCommand;
import com.nick.landclaims.plugin.economy.ClaimPaymentService;
import com.nick.landclaims.plugin.economy.HavenEconomyServiceAdapter;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import com.nick.landclaims.plugin.flag.ClaimFlagService;
import com.nick.landclaims.plugin.limit.ClaimCostConfig;
import com.nick.landclaims.plugin.limit.ClaimCostService;
import com.nick.landclaims.plugin.limit.LimitService;
import com.nick.landclaims.plugin.listener.ClaimToolListener;
import com.nick.landclaims.plugin.listener.ProtectionListener;
import com.nick.landclaims.plugin.message.MessageConfigurationLoader;
import com.nick.landclaims.plugin.message.MessageService;
import com.nick.landclaims.plugin.permission.PermissionBankService;
import com.nick.landclaims.plugin.protection.ProtectionService;
import com.nick.landclaims.plugin.recipe.ClaimToolRecipeService;
import com.nick.landclaims.plugin.selection.DoubleCrouchClearService;
import com.nick.landclaims.plugin.selection.SelectionService;
import com.nick.landclaims.plugin.storage.ClaimRepository;
import com.nick.landclaims.plugin.storage.sql.SqlClaimRepository;
import com.nick.landclaims.plugin.tool.ClaimToolService;
import com.nick.landclaims.plugin.ui.ClaimFlagEditorService;
import com.nick.landclaims.plugin.ui.ClaimMenuService;
import com.nick.landclaims.plugin.ui.DialogService;
import com.nick.landclaims.plugin.ui.InventoryGuiFallbackService;
import com.nick.landclaims.plugin.visual.BukkitChunkBorderRenderer;
import com.nick.landclaims.plugin.visual.ClaimBorderColorService;
import com.nick.landclaims.plugin.visual.ChunkBorderVisualService;
import dev.invisiblespiders.haven.api.HavenAPI;
import dev.invisiblespiders.haven.api.service.HavenDataSource;
import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;

public final class LandClaimsPlugin extends JavaPlugin {
    private LandClaimsApi landClaimsApi;
    private ChunkBorderVisualService chunkBorderVisualService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("permissions.yml");
        saveResourceIfMissing("tool.yml");
        saveResourceIfMissing("recipes.yml");

        ClaimService claimService = new ClaimService();
        ClaimToolService claimToolService = new ClaimToolService(this);
        MessageService messageService = new MessageService(MessageConfigurationLoader.load(loadYamlResource("messages.yml")));
        FlagRegistry flagRegistry = FlagRegistry.createDefault();
        ProtectionService protectionService = new ProtectionService(flagRegistry);
        SelectionService selectionService = new SelectionService(claimService);
        ClaimRepository claimRepository = createClaimRepository();
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.load(claimRepository.findAllClaims());
        Map<String, Integer> limitPermissions = loadLimitPermissions(loadYamlResource("permissions.yml"));
        new PermissionBankService(getServer().getPluginManager()).registerLimitPermissions(limitPermissions);
        LimitService limitService = new LimitService(
                getConfig().getInt("limits.default-claim-limit", limitPermissions.getOrDefault("landclaims.limit.default", 10)),
                limitPermissions
        );
        ClaimCostService claimCostService = new ClaimCostService(
                claimRepository,
                limitService,
                ClaimCostConfig.from(getConfig())
        );
        ClaimPaymentService claimPaymentService = new ClaimPaymentService(
                new HavenEconomyServiceAdapter(HavenAPI.get(HavenEconomyService.class))
        );
        ClaimCreationService claimCreationService = new ClaimCreationService(
                claimRepository,
                claimIndex,
                claimService,
                flagRegistry,
                getConfig().getInt("claiming.player-buffer-distance", 3),
                getConfig().getInt("claiming.admin-buffer-distance", 3),
                getConfig().getInt("claiming.max-name-length", 32)
        );
        ClaimBorderColorService claimBorderColorService = new ClaimBorderColorService(
                claimCreationService,
                claimIndex,
                claimCostService
        );
        DoubleCrouchClearService doubleCrouchClearService = new DoubleCrouchClearService(
                getConfig().getInt("selection.double-crouch-clear.window-ticks", 80),
                () -> getServer().getCurrentTick()
        );
        chunkBorderVisualService = createChunkBorderVisualService();
        landClaimsApi = new BukkitLandClaimsApi(claimRepository, claimIndex, protectionService);
        getServer().getServicesManager().register(LandClaimsApi.class, landClaimsApi, this, ServicePriority.Normal);
        new ClaimToolRecipeService(this, claimToolService).register(loadYamlResource("recipes.yml"));

        getServer().getPluginManager().registerEvents(
                new ClaimToolListener(
                        claimToolService,
                        selectionService,
                        doubleCrouchClearService,
                        chunkBorderVisualService,
                        claimBorderColorService,
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
                .setExecutor(new ClaimsCommand(
                        claimToolService,
                        selectionService,
                        claimCreationService,
                        claimIndex,
                        claimCostService,
                        claimPaymentService,
                        new PendingClaimMergeService(),
                        messageService,
                        new ClaimMemberService(claimRepository, claimIndex),
                        new ClaimFlagService(claimRepository, claimIndex, flagRegistry),
                        new ClaimFlagEditorService(),
                        new ClaimMenuService(),
                        new DialogService(),
                        new InventoryGuiFallbackService(),
                        chunkBorderVisualService,
                        claimBorderColorService
                ));

        getLogger().info("LandClaims enabled.");
    }

    @Override
    public void onDisable() {
        if (chunkBorderVisualService != null) {
            chunkBorderVisualService.clearAll();
            chunkBorderVisualService = null;
        }
        if (landClaimsApi != null) {
            getServer().getServicesManager().unregister(LandClaimsApi.class, landClaimsApi);
            landClaimsApi = null;
        }
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

    private Map<String, Integer> loadLimitPermissions(YamlConfiguration permissionsConfiguration) {
        ConfigurationSection limitsSection = permissionsConfiguration.getConfigurationSection("limits");
        if (limitsSection == null) {
            return Map.of();
        }

        Map<String, Integer> limitPermissions = new HashMap<>();
        for (String permissionNode : limitsSection.getKeys(false)) {
            limitPermissions.put(permissionNode, limitsSection.getInt(permissionNode));
        }
        return Map.copyOf(limitPermissions);
    }

    private ChunkBorderVisualService createChunkBorderVisualService() {
        if (!getConfig().getBoolean("visuals.border.enabled", true)) {
            return null;
        }
        return new ChunkBorderVisualService(
                new BukkitChunkBorderRenderer(
                        this,
                        getConfig().getDouble("visuals.border.thickness", 0.08D),
                        (float) getConfig().getDouble("visuals.border.view-range", 96.0D)
                ),
                getConfig().getInt("visuals.border.duration-ticks", 160)
        );
    }

    private ClaimRepository createClaimRepository() {
        HavenDataSource havenDataSource = HavenAPI.get(HavenDataSource.class);
        havenDataSource.registerMigrations(
                "landclaims",
                "db/migrations/landclaims",
                getClass().getClassLoader()
        );
        return new SqlClaimRepository(havenDataSource.getDataSource());
    }
}
