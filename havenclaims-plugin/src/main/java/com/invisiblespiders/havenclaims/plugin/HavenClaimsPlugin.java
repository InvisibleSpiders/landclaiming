package com.invisiblespiders.havenclaims.plugin;

import com.invisiblespiders.havenclaims.api.HavenClaimsApi;
import com.invisiblespiders.havenclaims.plugin.api.BukkitHavenClaimsApi;
import com.invisiblespiders.havenclaims.plugin.admin.AdminClaimService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimCreationService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimDenyService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimMemberService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimService;
import com.invisiblespiders.havenclaims.plugin.claim.PendingClaimMergeService;
import com.invisiblespiders.havenclaims.plugin.command.ClaimsCommand;
import com.invisiblespiders.havenclaims.plugin.entity.EntityControlService;
import com.invisiblespiders.havenclaims.plugin.economy.ClaimPaymentService;
import com.invisiblespiders.havenclaims.plugin.economy.HavenEconomyServiceAdapter;
import com.invisiblespiders.havenclaims.plugin.economy.NoopEconomyService;
import com.invisiblespiders.havenclaims.plugin.flag.FlagRegistry;
import com.invisiblespiders.havenclaims.plugin.flag.ClaimFlagService;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimCostConfig;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimCostService;
import com.invisiblespiders.havenclaims.plugin.limit.LimitService;
import com.invisiblespiders.havenclaims.plugin.listener.ClaimToolListener;
import com.invisiblespiders.havenclaims.plugin.listener.ClaimBoundaryNotificationListener;
import com.invisiblespiders.havenclaims.plugin.listener.DeniedClaimAccessListener;
import com.invisiblespiders.havenclaims.plugin.listener.EntityControlListener;
import com.invisiblespiders.havenclaims.plugin.listener.ProtectionListener;
import com.invisiblespiders.havenclaims.plugin.message.MessageConfigurationLoader;
import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import com.invisiblespiders.havenclaims.plugin.permission.PermissionBankService;
import com.invisiblespiders.havenclaims.plugin.protection.ProtectionService;
import com.invisiblespiders.havenclaims.plugin.recipe.ClaimToolRecipeService;
import com.invisiblespiders.havenclaims.plugin.selection.DoubleCrouchClearService;
import com.invisiblespiders.havenclaims.plugin.selection.SelectionService;
import com.invisiblespiders.havenclaims.plugin.storage.ClaimRepository;
import com.invisiblespiders.havenclaims.plugin.storage.sql.SqlClaimRepository;
import com.invisiblespiders.havenclaims.plugin.tool.ClaimToolService;
import com.invisiblespiders.havenclaims.plugin.ui.ClaimFlagEditorService;
import com.invisiblespiders.havenclaims.plugin.ui.ClaimMenuService;
import com.invisiblespiders.havenclaims.plugin.ui.DialogService;
import com.invisiblespiders.havenclaims.plugin.ui.InventoryGuiFallbackService;
import com.invisiblespiders.havenclaims.plugin.visual.BukkitChunkBorderRenderer;
import com.invisiblespiders.havenclaims.plugin.visual.ClaimBorderColorService;
import com.invisiblespiders.havenclaims.plugin.visual.ChunkBorderVisualService;
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
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;

public final class HavenClaimsPlugin extends JavaPlugin {
    private HavenClaimsApi havenClaimsApi;
    private ChunkBorderVisualService chunkBorderVisualService;
    private EntityControlService entityControlService;

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
        PermissionBankService permissionBankService = new PermissionBankService(getServer().getPluginManager());
        permissionBankService.registerLimitPermissions(limitPermissions);
        registerConfiguredPermissions(permissionBankService, loadYamlResource("permissions.yml"), "commands", PermissionDefault.TRUE);
        registerConfiguredPermissions(permissionBankService, loadYamlResource("permissions.yml"), "admin", PermissionDefault.OP);
        registerConfiguredPermissions(permissionBankService, loadYamlResource("permissions.yml"), "bypass", PermissionDefault.OP);
        LimitService limitService = new LimitService(
                getConfig().getInt("limits.default-claim-limit", limitPermissions.getOrDefault("havenclaims.limit.default", 10)),
                limitPermissions
        );
        ClaimCostService claimCostService = new ClaimCostService(
                claimIndex,
                limitService,
                ClaimCostConfig.from(getConfig())
        );
        HavenEconomyService havenEconomy = HavenAPI.get(HavenEconomyService.class);
        ClaimPaymentService claimPaymentService = new ClaimPaymentService(
                havenEconomy != null
                        ? new HavenEconomyServiceAdapter(havenEconomy)
                        : new NoopEconomyService()
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
        entityControlService = createEntityControlService(claimIndex);
        havenClaimsApi = new BukkitHavenClaimsApi(claimRepository, claimIndex, protectionService);
        getServer().getServicesManager().register(HavenClaimsApi.class, havenClaimsApi, this, ServicePriority.Normal);
        new ClaimToolRecipeService(this, claimToolService).register(loadYamlResource("recipes.yml"));

        getServer().getPluginManager().registerEvents(
                new ClaimToolListener(
                        claimToolService,
                        selectionService,
                        doubleCrouchClearService,
                        chunkBorderVisualService,
                        claimBorderColorService,
                        claimIndex,
                        messageService,
                        getConfig().getBoolean("selection.clear-on-tool-switch", true),
                        getConfig().getBoolean("selection.double-crouch-clear.enabled", true)
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new ProtectionListener(protectionService, claimIndex, messageService),
                this
        );
        getServer().getPluginManager().registerEvents(
                new DeniedClaimAccessListener(
                        claimIndex,
                        messageService,
                        getConfig().getBoolean("access-denial.enabled", true),
                        getConfig().getBoolean("access-denial.knockback.enabled", true),
                        getConfig().getDouble("access-denial.knockback.strength", 0.65D)
                ),
                this
        );
        getServer().getPluginManager().registerEvents(
                new ClaimBoundaryNotificationListener(
                        claimIndex,
                        messageService,
                        getConfig().getBoolean("notifications.claim-boundary.enabled", true),
                        getConfig().getBoolean("notifications.claim-boundary.enter.enabled", true),
                        getConfig().getBoolean("notifications.claim-boundary.exit.enabled", true),
                        getConfig().getString("notifications.claim-boundary.delivery", "action_bar"),
                        getConfig().getString("notifications.claim-boundary.enter.delivery",
                                getConfig().getString("notifications.claim-boundary.delivery", "action_bar")),
                        getConfig().getString("notifications.claim-boundary.exit.delivery",
                                getConfig().getString("notifications.claim-boundary.delivery", "action_bar"))
                ),
                this
        );
        if (entityControlService != null) {
            getServer().getPluginManager().registerEvents(new EntityControlListener(entityControlService), this);
            entityControlService.start(getConfig().getLong("advanced.entity-control.cleanup-interval-ticks", 200L));
        }
        ClaimsCommand claimsCommand = new ClaimsCommand(
                        claimToolService,
                        selectionService,
                        claimCreationService,
                        claimIndex,
                        claimCostService,
                        claimPaymentService,
                        new PendingClaimMergeService(),
                        messageService,
                        new ClaimMemberService(claimRepository, claimIndex),
                        new ClaimDenyService(claimRepository, claimIndex),
                        new ClaimFlagService(claimRepository, claimIndex, flagRegistry),
                        new ClaimFlagEditorService(),
                        new ClaimMenuService(),
                        new DialogService(),
                        new InventoryGuiFallbackService(),
                        chunkBorderVisualService,
                        claimBorderColorService,
                        new AdminClaimService(
                                claimRepository,
                                claimIndex,
                                flagRegistry,
                                getConfig().getInt("claiming.max-name-length", 32)
                        )
                );
        Objects.requireNonNull(getCommand("claim"), "claim command is not defined in plugin.yml")
                .setExecutor(claimsCommand);
        Objects.requireNonNull(getCommand("claim"), "claim command is not defined in plugin.yml")
                .setTabCompleter(claimsCommand);

        getLogger().info("HavenClaims enabled.");
    }

    @Override
    public void onDisable() {
        if (chunkBorderVisualService != null) {
            chunkBorderVisualService.clearAll();
            chunkBorderVisualService = null;
        }
        if (entityControlService != null) {
            entityControlService.stop();
            entityControlService = null;
        }
        if (havenClaimsApi != null) {
            getServer().getServicesManager().unregister(HavenClaimsApi.class, havenClaimsApi);
            havenClaimsApi = null;
        }
        getLogger().info("HavenClaims disabled.");
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

    private void registerConfiguredPermissions(
            PermissionBankService permissionBankService,
            YamlConfiguration permissionsConfiguration,
            String sectionPath,
            PermissionDefault permissionDefault
    ) {
        ConfigurationSection section = permissionsConfiguration.getConfigurationSection(sectionPath);
        if (section == null) {
            return;
        }
        for (String permissionNode : section.getKeys(false)) {
            permissionBankService.register(permissionNode, section.getString(permissionNode, "HavenClaims permission."), permissionDefault);
        }
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
                getConfig().getInt("visuals.border.duration-ticks", 0)
        );
    }

    private EntityControlService createEntityControlService(ClaimIndex claimIndex) {
        if (!getConfig().getBoolean("advanced.entity-control.enabled", true)) {
            return null;
        }
        return new EntityControlService(
                this,
                claimIndex,
                getConfig().getBoolean("advanced.entity-control.preserve-named-entities", true),
                getConfig().getBoolean("advanced.entity-control.preserve-tamed-entities", true)
        );
    }

    private ClaimRepository createClaimRepository() {
        HavenDataSource havenDataSource = HavenAPI.get(HavenDataSource.class);
        havenDataSource.registerMigrations(
                "havenclaims",
                "db/migrations/havenclaims",
                getClass().getClassLoader()
        );
        return new SqlClaimRepository(havenDataSource.getDataSource());
    }
}
