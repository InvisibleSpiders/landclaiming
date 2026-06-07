package com.nick.landclaims.plugin;

import com.nick.landclaims.plugin.claim.ClaimService;
import com.nick.landclaims.plugin.command.ClaimsCommand;
import com.nick.landclaims.plugin.flag.FlagRegistry;
import com.nick.landclaims.plugin.listener.ClaimToolListener;
import com.nick.landclaims.plugin.listener.ProtectionListener;
import com.nick.landclaims.plugin.protection.ProtectionService;
import com.nick.landclaims.plugin.selection.SelectionService;
import com.nick.landclaims.plugin.tool.ClaimToolService;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

public final class LandClaimsPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();

        ClaimService claimService = new ClaimService();
        ClaimToolService claimToolService = new ClaimToolService(this);
        FlagRegistry flagRegistry = FlagRegistry.createDefault();
        ProtectionService protectionService = new ProtectionService(flagRegistry);
        SelectionService selectionService = new SelectionService(claimService);

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
}
