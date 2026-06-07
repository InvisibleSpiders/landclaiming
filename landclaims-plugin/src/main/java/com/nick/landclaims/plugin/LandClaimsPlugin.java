package com.nick.landclaims.plugin;

import com.nick.landclaims.plugin.claim.ClaimService;
import com.nick.landclaims.plugin.command.ClaimsCommand;
import com.nick.landclaims.plugin.listener.ClaimToolListener;
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
        SelectionService selectionService = new SelectionService(claimService);

        getServer().getPluginManager().registerEvents(
                new ClaimToolListener(claimToolService, selectionService),
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
