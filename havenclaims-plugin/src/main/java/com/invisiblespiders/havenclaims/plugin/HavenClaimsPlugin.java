package com.invisiblespiders.havenclaims.plugin;

import com.invisiblespiders.havenclaims.api.HavenClaimsApi;
import com.invisiblespiders.havenclaims.api.limit.HavenClaimsLimitService;
import com.invisiblespiders.havenclaims.plugin.api.BukkitHavenClaimsApi;
import com.invisiblespiders.havenclaims.plugin.admin.AdminClaimService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimCreationService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimDenyService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimMemberService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimService;
import com.invisiblespiders.havenclaims.plugin.claim.PendingClaimMergeService;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeCommand;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeCommandGuard;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeConfig;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeListener;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeRecoveryStore;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeService;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeSessionHistory;
import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeToolRegistry;
import com.invisiblespiders.havenclaims.plugin.claimmode.StandardClaimModeTools;
import com.invisiblespiders.havenclaims.plugin.command.ClaimsCommand;
import com.invisiblespiders.havenclaims.plugin.entity.EntityControlService;
import com.invisiblespiders.havenclaims.plugin.economy.ClaimPaymentService;
import com.invisiblespiders.havenclaims.plugin.economy.HavenEconomyServiceAdapter;
import com.invisiblespiders.havenclaims.plugin.economy.NoopEconomyService;
import com.invisiblespiders.havenclaims.plugin.flag.FlagRegistry;
import com.invisiblespiders.havenclaims.plugin.flag.ClaimFlagService;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimCostConfig;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimCostService;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimLimitRepository;
import com.invisiblespiders.havenclaims.plugin.limit.LimitService;
import com.invisiblespiders.havenclaims.plugin.limit.SqlClaimLimitRepository;
import com.invisiblespiders.havenclaims.plugin.listener.AdminClaimBrowserListener;
import com.invisiblespiders.havenclaims.plugin.listener.ClaimToolListener;
import com.invisiblespiders.havenclaims.plugin.listener.ClaimBoundaryNotificationListener;
import com.invisiblespiders.havenclaims.plugin.listener.DeniedClaimAccessListener;
import com.invisiblespiders.havenclaims.plugin.listener.EntityControlListener;
import com.invisiblespiders.havenclaims.plugin.listener.ProtectionListener;
import com.invisiblespiders.havenclaims.plugin.message.MessageConfigurationLoader;
import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import com.invisiblespiders.havenclaims.plugin.permission.PermissionBankService;
import com.invisiblespiders.havenclaims.plugin.protection.ProtectionService;
import com.invisiblespiders.havenclaims.plugin.selection.DoubleCrouchClearService;
import com.invisiblespiders.havenclaims.plugin.selection.SelectionService;
import com.invisiblespiders.havenclaims.plugin.storage.ClaimRepository;
import com.invisiblespiders.havenclaims.plugin.storage.sql.SqlClaimRepository;
import com.invisiblespiders.havenclaims.plugin.tool.ClaimToolService;
import com.invisiblespiders.havenclaims.plugin.ui.AdminClaimBrowserService;
import com.invisiblespiders.havenclaims.plugin.ui.ClaimFlagEditorService;
import com.invisiblespiders.havenclaims.plugin.ui.ClaimMenuService;
import com.invisiblespiders.havenclaims.plugin.ui.DialogService;
import com.invisiblespiders.havenclaims.plugin.ui.InventoryGuiFallbackService;
import com.invisiblespiders.havenclaims.plugin.visual.BukkitChunkBorderRenderer;
import com.invisiblespiders.havenclaims.plugin.visual.ClaimBorderColorService;
import com.invisiblespiders.havenclaims.plugin.visual.ChunkBorderVisualService;
import dev.invisiblespiders.haven.api.HavenAPI;
import dev.invisiblespiders.haven.api.model.ReloadResult;
import dev.invisiblespiders.haven.api.model.SuiteHealthReport;
import dev.invisiblespiders.haven.api.model.SuiteHealthSeverity;
import dev.invisiblespiders.haven.api.service.HavenDataSource;
import dev.invisiblespiders.haven.api.service.HavenEconomyService;
import dev.invisiblespiders.haven.api.service.HavenSuiteEntry;
import dev.invisiblespiders.haven.api.service.HavenSuiteRegistry;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.sql.DataSource;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.NamespacedKey;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;

public final class HavenClaimsPlugin extends JavaPlugin
        implements HavenSuiteEntry {
    private HavenClaimsApi havenClaimsApi;
    private LimitService limitService;
    private ChunkBorderVisualService chunkBorderVisualService;
    private EntityControlService entityControlService;
    private MessageService messageService;
    private ClaimCostService claimCostService;
    private ClaimCreationService claimCreationService;
    private DoubleCrouchClearService doubleCrouchClearService;
    private ClaimToolListener claimToolListener;
    private DeniedClaimAccessListener deniedClaimAccessListener;
    private ClaimBoundaryNotificationListener claimBoundaryNotificationListener;
    private ClaimIndex claimIndex;
    private ClaimModeService claimModeService;
    private ClaimModeCommandGuard claimModeCommandGuard;
    private ClaimModeSessionHistory claimModeSessionHistory;
    private HavenDataSource havenDataSource;
    private DialogService dialogService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("permissions.yml");

        ClaimService claimService = new ClaimService();
        ClaimToolService claimToolService = new ClaimToolService(this);
        messageService = new MessageService(MessageConfigurationLoader.load(loadYamlResource("messages.yml")));
        ClaimModeConfig claimModeConfig = ClaimModeConfig.from(getConfig());
        claimModeCommandGuard = new ClaimModeCommandGuard(claimModeConfig);
        ClaimModeRecoveryStore claimModeRecoveryStore = new ClaimModeRecoveryStore(getDataFolder().toPath());
        claimModeSessionHistory = new ClaimModeSessionHistory(
                getDataFolder().toPath(), claimModeConfig.historyPerPlayer());
        FlagRegistry flagRegistry = FlagRegistry.createDefault();
        ProtectionService protectionService = new ProtectionService(flagRegistry);
        SelectionService selectionService = new SelectionService(claimService);
        havenDataSource = HavenAPI.get(HavenDataSource.class);
        havenDataSource.registerMigrations(
                "havenclaims",
                "db/migrations/havenclaims",
                getClass().getClassLoader()
        );
        DataSource dataSource = havenDataSource.getDataSource();
        ClaimRepository claimRepository = new SqlClaimRepository(dataSource);
        claimIndex = new ClaimIndex();
        claimIndex.load(claimRepository.findAllClaims());
        PermissionBankService permissionBankService = new PermissionBankService(getServer().getPluginManager());
        YamlConfiguration permissionsConfiguration = loadYamlResource("permissions.yml");
        permissionBankService.registerFlagEditGroups(
                permissionsConfiguration.getConfigurationSection("flag-edit"),
                PermissionDefault.TRUE);
        registerConfiguredPermissions(permissionBankService, permissionsConfiguration, "commands", PermissionDefault.TRUE);
        registerConfiguredPermissions(permissionBankService, permissionsConfiguration, "admin", PermissionDefault.OP);
        registerConfiguredPermissions(permissionBankService, permissionsConfiguration, "bypass", PermissionDefault.OP);
        ClaimLimitRepository claimLimitRepository = new SqlClaimLimitRepository(dataSource);
        limitService = new LimitService(
                getConfig().getInt("limits.default-claim-limit", 10),
                claimLimitRepository
        );
        getServer().getServicesManager().register(
                HavenClaimsLimitService.class, limitService, this, ServicePriority.Normal);
        claimCostService = new ClaimCostService(
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
        claimCreationService = new ClaimCreationService(
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
        doubleCrouchClearService = new DoubleCrouchClearService(
                getConfig().getInt("selection.double-crouch-clear.window-ticks", 80),
                () -> getServer().getCurrentTick()
        );
        chunkBorderVisualService = createChunkBorderVisualService();
        entityControlService = createEntityControlService(claimIndex);
        havenClaimsApi = new BukkitHavenClaimsApi(claimRepository, claimIndex, protectionService);
        getServer().getServicesManager().register(HavenClaimsApi.class, havenClaimsApi, this, ServicePriority.Normal);

        claimToolListener = new ClaimToolListener(
                claimToolService,
                selectionService,
                null,
                doubleCrouchClearService,
                chunkBorderVisualService,
                claimBorderColorService,
                claimIndex,
                messageService,
                getConfig().getBoolean("selection.clear-on-tool-switch", true),
                getConfig().getBoolean("selection.double-crouch-clear.enabled", true)
        );
        ClaimModeToolRegistry claimModeToolRegistry = StandardClaimModeTools.createRegistry(
                new NamespacedKey(this, "claim_mode_tool"),
                (player, event) -> claimToolListener.handleClaimToolSelection(event));
        claimModeService = new ClaimModeService(
                claimModeConfig,
                claimModeToolRegistry,
                claimModeSessionHistory,
                claimModeRecoveryStore,
                Component.text("Claim mode")
        );
        claimToolListener.setClaimModeService(claimModeService);
        claimToolService.setClaimModeToolRegistry(claimModeToolRegistry);
        getServer().getPluginManager().registerEvents(claimToolListener, this);
        getServer().getPluginManager().registerEvents(
                new ClaimModeListener(claimModeService, claimModeCommandGuard, messageService), this);
        getServer().getPluginManager().registerEvents(
                new ProtectionListener(protectionService, claimIndex, messageService),
                this
        );
        deniedClaimAccessListener = new DeniedClaimAccessListener(
                claimIndex,
                messageService,
                getConfig().getBoolean("access-denial.enabled", true),
                getConfig().getBoolean("access-denial.knockback.enabled", true),
                getConfig().getDouble("access-denial.knockback.strength", 0.65D)
        );
        getServer().getPluginManager().registerEvents(deniedClaimAccessListener, this);
        claimBoundaryNotificationListener = new ClaimBoundaryNotificationListener(
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
        );
        getServer().getPluginManager().registerEvents(claimBoundaryNotificationListener, this);
        if (entityControlService != null) {
            getServer().getPluginManager().registerEvents(new EntityControlListener(entityControlService), this);
            entityControlService.start(getConfig().getLong("advanced.entity-control.cleanup-interval-ticks", 200L));
        }
        AdminClaimService adminClaimService = new AdminClaimService(
                claimRepository,
                claimIndex,
                flagRegistry,
                getConfig().getInt("claiming.max-name-length", 32)
        );
        AdminClaimBrowserService adminClaimBrowserService = new AdminClaimBrowserService(
                adminClaimService, claimCostService, claimPaymentService, messageService);
        getServer().getPluginManager().registerEvents(
                new AdminClaimBrowserListener(adminClaimBrowserService), this);
        dialogService = new DialogService(getConfig().getBoolean("ui.prefer-dialogs", true));
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
                        new ClaimFlagEditorService(messageService),
                        new ClaimMenuService(messageService),
                        dialogService,
                        new InventoryGuiFallbackService(),
                        chunkBorderVisualService,
                        claimBorderColorService,
                        adminClaimService,
                        limitService,
                        this::performReload,
                        adminClaimBrowserService,
                        null
                );
        var claimCommand = Objects.requireNonNull(getCommand("claim"), "claim command is not defined in plugin.yml");
        claimCommand.setExecutor(claimsCommand);
        claimCommand.setTabCompleter(claimsCommand);
        ClaimModeCommand claimModeCommand = new ClaimModeCommand(claimModeService, messageService);
        var claimModePluginCommand = Objects.requireNonNull(
                getCommand("claimmode"), "claimmode command is not defined in plugin.yml");
        claimModePluginCommand.setExecutor(claimModeCommand);
        claimModePluginCommand.setTabCompleter(claimModeCommand);
        claimsCommand.setClaimModeCommand(claimModeCommand);

        getLogger().info("HavenClaims enabled.");
        HavenSuiteRegistry suiteRegistry = HavenAPI.get(HavenSuiteRegistry.class);
        if (suiteRegistry != null) {
            suiteRegistry.register(this);
        }
    }

    public ReloadResult performReload() {
        try {
            reloadConfig();
            messageService.reload(
                    MessageConfigurationLoader.load(
                            loadYamlResource("messages.yml")));
            limitService.reload(
                    getConfig().getInt("limits.default-claim-limit", 10));
            claimCostService.reload(
                    ClaimCostConfig.from(getConfig()));
            ClaimModeConfig claimModeConfig = ClaimModeConfig.from(getConfig());
            claimModeService.reload(claimModeConfig);
            claimModeCommandGuard.reload(claimModeConfig);
            claimModeSessionHistory.reload(claimModeConfig.historyPerPlayer());
            claimCreationService.reload(
                    getConfig().getInt("claiming.player-buffer-distance", 3),
                    getConfig().getInt("claiming.admin-buffer-distance", 3),
                    getConfig().getInt("claiming.max-name-length", 32));
            doubleCrouchClearService.reload(
                    getConfig().getInt("selection.double-crouch-clear.window-ticks", 80));
            claimToolListener.reload(
                    getConfig().getBoolean("selection.clear-on-tool-switch", true),
                    getConfig().getBoolean("selection.double-crouch-clear.enabled", true));
            deniedClaimAccessListener.reload(
                    getConfig().getBoolean("access-denial.enabled", true),
                    getConfig().getBoolean("access-denial.knockback.enabled", true),
                    getConfig().getDouble("access-denial.knockback.strength", 0.65D));
            dialogService.reload(
                    getConfig().getBoolean("ui.prefer-dialogs", true));
            String defaultDel = getConfig().getString("notifications.claim-boundary.delivery", "action_bar");
            claimBoundaryNotificationListener.reload(
                    getConfig().getBoolean("notifications.claim-boundary.enabled", true),
                    getConfig().getBoolean("notifications.claim-boundary.enter.enabled", true),
                    getConfig().getBoolean("notifications.claim-boundary.exit.enabled", true),
                    defaultDel,
                    getConfig().getString("notifications.claim-boundary.enter.delivery", defaultDel),
                    getConfig().getString("notifications.claim-boundary.exit.delivery", defaultDel));
            return ReloadResult.ok("HavenClaims reloaded successfully.");
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Config reload failed", e);
            return ReloadResult.fail("Reload failed: " + e.getMessage());
        }
    }

    @Override
    public String pluginName() {
        return getName();
    }

    @Override
    public String pluginVersion() {
        return getDescription().getVersion();
    }

    @Override
    public List<SuiteHealthReport> health() {
        List<SuiteHealthReport> reports = new ArrayList<>();
        if (havenDataSource != null) {
            try (Connection connection = havenDataSource.getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT 1")) {
                statement.execute();
                reports.add(new SuiteHealthReport(SuiteHealthSeverity.PASS, "database", "connected"));
            } catch (SQLException e) {
                reports.add(new SuiteHealthReport(SuiteHealthSeverity.FAIL, "database", e.getMessage()));
            }
        }
        int claimCount = claimIndex != null ? claimIndex.findAll().size() : 0;
        reports.add(new SuiteHealthReport(SuiteHealthSeverity.PASS, "claims",
                claimCount + " loaded"));
        return reports;
    }

    @Override
    public ReloadResult reload() {
        return performReload();
    }

    @Override
    public void onDisable() {
        HavenSuiteRegistry suiteRegistry = HavenAPI.get(HavenSuiteRegistry.class);
        if (suiteRegistry != null) {
            suiteRegistry.unregister(getName());
        }
        if (claimModeService != null) {
            claimModeService.restoreAll(
                    getServer().getOnlinePlayers(), ClaimModeService.ExitReason.PLUGIN_DISABLE);
            claimModeService.flushActiveSessionsToRecovery(ClaimModeService.ExitReason.PLUGIN_DISABLE);
            claimModeService = null;
        }
        claimModeCommandGuard = null;
        claimModeSessionHistory = null;
        if (chunkBorderVisualService != null) {
            chunkBorderVisualService.clearAll();
            chunkBorderVisualService = null;
        }
        if (entityControlService != null) {
            entityControlService.stop();
            entityControlService = null;
        }
        if (limitService != null) {
            getServer().getServicesManager().unregister(HavenClaimsLimitService.class, limitService);
            limitService = null;
        }
        if (havenClaimsApi != null) {
            getServer().getServicesManager().unregister(HavenClaimsApi.class, havenClaimsApi);
            havenClaimsApi = null;
        }
        messageService = null;
        limitService = null;
        claimCostService = null;
        claimCreationService = null;
        doubleCrouchClearService = null;
        claimToolListener = null;
        deniedClaimAccessListener = null;
        claimBoundaryNotificationListener = null;
        dialogService = null;
        claimIndex = null;
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
                getConfig().getInt("visuals.border.duration-ticks", 100)
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

}
