package com.invisiblespiders.havenclaims.plugin.tool;

import com.invisiblespiders.havenclaims.plugin.claimmode.ClaimModeToolRegistry;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class ClaimToolService {
    private ClaimModeToolRegistry claimModeToolRegistry;

    public ClaimToolService(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
    }

    ClaimToolService(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
    }

    public void setClaimModeToolRegistry(ClaimModeToolRegistry claimModeToolRegistry) {
        this.claimModeToolRegistry = claimModeToolRegistry;
    }

    public ItemStack createClaimModeTool() {
        if (claimModeToolRegistry == null) {
            throw new IllegalStateException("Claim mode tool registry is not configured.");
        }
        return claimModeToolRegistry.createItem("claim");
    }

    public boolean isClaimTool(ItemStack itemStack) {
        return isClaimModeClaimTool(itemStack);
    }

    public boolean isClaimModeClaimTool(ItemStack itemStack) {
        return claimModeToolRegistry != null && claimModeToolRegistry.resolve(itemStack)
                .map(tool -> tool.id().equals("claim"))
                .orElse(false);
    }
}
