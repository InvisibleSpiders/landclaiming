package com.nick.landclaims.plugin.permission;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
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

    public void registerFlagEditGroups(ConfigurationSection flagEditSection, PermissionDefault permissionDefault) {
        if (flagEditSection == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : flagEditSection.getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof java.util.List<?>)) {
                continue;
            }
            String groupNode = entry.getKey();
            java.util.List<String> flags = flagEditSection.getStringList(groupNode);
            Map<String, Boolean> children = flags.stream()
                    .map(flag -> "landclaims.flag." + flag)
                    .collect(Collectors.toMap(
                            node -> node,
                            node -> true,
                            (first, second) -> first,
                            java.util.LinkedHashMap::new));
            for (String childNode : children.keySet()) {
                register(
                        childNode,
                        "Allows editing the " + childNode.substring("landclaims.flag.".length()) + " claim flag.",
                        permissionDefault);
            }
            register(groupNode, "LandClaims flag edit group.", permissionDefault, children);
        }
    }

    public void register(String node, String description, PermissionDefault permissionDefault) {
        register(node, description, permissionDefault, Map.of());
    }

    private void register(
            String node,
            String description,
            PermissionDefault permissionDefault,
            Map<String, Boolean> children
    ) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(permissionDefault, "permissionDefault");
        Objects.requireNonNull(children, "children");

        if (pluginManager.getPermission(node) != null) {
            return;
        }

        pluginManager.addPermission(new Permission(node, description, permissionDefault, children));
    }
}
