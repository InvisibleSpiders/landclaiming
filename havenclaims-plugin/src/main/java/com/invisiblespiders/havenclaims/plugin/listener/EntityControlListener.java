package com.invisiblespiders.havenclaims.plugin.listener;

import com.invisiblespiders.havenclaims.plugin.entity.EntityControlService;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof LivingEntity livingEntity) || clicked instanceof Player) {
            return;
        }

        ItemStack itemStack = itemInHand(event.getPlayer(), event.getHand());
        if (itemStack == null || itemStack.getType() != Material.NAME_TAG || !itemStack.hasItemMeta()) {
            return;
        }

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null && itemMeta.hasDisplayName()) {
            entityControlService.markPlayerNamedEntity(livingEntity);
        }
    }

    private ItemStack itemInHand(Player player, EquipmentSlot hand) {
        if (hand == null) {
            return null;
        }
        return player.getInventory().getItem(hand);
    }
}
