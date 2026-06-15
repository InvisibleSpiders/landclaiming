package com.invisiblespiders.havenclaims.plugin.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invisiblespiders.havenclaims.plugin.entity.EntityControlService;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

class EntityControlListenerTest {
    @Test
    void marksLivingEntityWhenPlayerUsesNamedNameTag() {
        EntityControlService entityControlService = mock(EntityControlService.class);
        EntityControlListener listener = new EntityControlListener(entityControlService);
        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        LivingEntity entity = mock(LivingEntity.class);
        ItemStack nameTag = mock(ItemStack.class);
        ItemMeta itemMeta = mock(ItemMeta.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getRightClicked()).thenReturn(entity);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItem(EquipmentSlot.HAND)).thenReturn(nameTag);
        when(nameTag.getType()).thenReturn(Material.NAME_TAG);
        when(nameTag.hasItemMeta()).thenReturn(true);
        when(nameTag.getItemMeta()).thenReturn(itemMeta);
        when(itemMeta.hasDisplayName()).thenReturn(true);

        listener.onPlayerInteractEntity(event);

        verify(entityControlService).markPlayerNamedEntity(entity);
    }
}
