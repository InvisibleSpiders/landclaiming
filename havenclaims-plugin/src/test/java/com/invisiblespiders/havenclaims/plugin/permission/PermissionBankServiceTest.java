package com.invisiblespiders.havenclaims.plugin.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PermissionBankServiceTest {
    @Test
    void registersFlagEditGroupsWithPerFlagChildren() {
        PluginManager pluginManager = mock(PluginManager.class);
        when(pluginManager.getPermission(anyString())).thenReturn(null);
        PermissionBankService permissionBankService = new PermissionBankService(pluginManager);
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("havenclaims.flag.edit.basic", java.util.List.of("build", "break"));

        permissionBankService.registerFlagEditGroups(configuration, PermissionDefault.TRUE);

        ArgumentCaptor<Permission> permissions = ArgumentCaptor.forClass(Permission.class);
        verify(pluginManager, org.mockito.Mockito.atLeastOnce()).addPermission(permissions.capture());
        assertThat(permissions.getAllValues())
                .anySatisfy(permission -> {
                    assertThat(permission.getName()).isEqualTo("havenclaims.flag.build");
                    assertThat(permission.getDefault()).isEqualTo(PermissionDefault.TRUE);
                })
                .anySatisfy(permission -> {
                    assertThat(permission.getName()).isEqualTo("havenclaims.flag.edit.basic");
                    assertThat(permission.getChildren()).containsEntry("havenclaims.flag.build", true);
                    assertThat(permission.getChildren()).containsEntry("havenclaims.flag.break", true);
                });
    }
}
