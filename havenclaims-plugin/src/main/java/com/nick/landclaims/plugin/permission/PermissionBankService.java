package com.nick.landclaims.plugin.permission;

import java.util.Map;
import java.util.Objects;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

public final class PermissionBankService {
    private final PluginManager pluginManager;

    public PermissionBankService(PluginManager pluginManager) {
        this.pluginManager = Objects.requireNonNull(pluginManager, "pluginManager");
    }

    public void registerLimitPermissions(Map<String, Integer> limits) {
        Objects.requireNonNull(limits, "limits");

        for (Map.Entry<String, Integer> limit : limits.entrySet()) {
            register(
                    limit.getKey(),
                    "LandClaims claim limit: " + limit.getValue() + " chunks.",
                    PermissionDefault.FALSE
            );
        }
    }

    public void register(String node, String description, PermissionDefault permissionDefault) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(permissionDefault, "permissionDefault");

        if (pluginManager.getPermission(node) != null) {
            return;
        }

        pluginManager.addPermission(new Permission(node, description, permissionDefault));
    }
}
