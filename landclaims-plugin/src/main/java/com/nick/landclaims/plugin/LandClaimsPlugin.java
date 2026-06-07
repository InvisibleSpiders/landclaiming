package com.nick.landclaims.plugin;

import org.bukkit.plugin.java.JavaPlugin;

public final class LandClaimsPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("LandClaims enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("LandClaims disabled.");
    }
}
