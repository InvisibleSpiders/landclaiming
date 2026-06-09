package com.nick.landclaims.plugin.listener;

import com.nick.landclaims.plugin.entity.EntityControlService;
import java.util.Objects;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class EntityControlListener implements Listener {
    private final EntityControlService entityControlService;

    public EntityControlListener(EntityControlService entityControlService) {
        this.entityControlService = Objects.requireNonNull(entityControlService, "entityControlService");
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        LivingEntity entity = event.getEntity();
        if (entityControlService.removeIfBlocked(entity)) {
            event.setCancelled(true);
        }
    }
}
