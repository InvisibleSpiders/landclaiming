package com.invisiblespiders.havenclaims.plugin.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.invisiblespiders.havenclaims.api.flag.FlagState;
import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.OwnerType;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class EntityControlServiceTest {
    @Test
    void removesHostileEntityWithCustomNameWhenNotNamedByPlayerNametag() {
        UUID worldId = UUID.randomUUID();
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.add(claim(worldId, Map.of("remove_hostile_entities", FlagState.ALL)));
        EntityControlService service = new EntityControlService(plugin(), claimIndex, true, false);
        LivingEntity entity = hostileEntity(worldId, 0, 0);
        when(entity.customName()).thenReturn(Component.text("Rare Zombie"));

        boolean removed = service.removeIfBlocked(entity);

        assertThat(removed).isTrue();
        verify(entity).remove();
    }

    @Test
    void preservesHostileEntityMarkedByPlayerNametag() {
        UUID worldId = UUID.randomUUID();
        ClaimIndex claimIndex = new ClaimIndex();
        claimIndex.add(claim(worldId, Map.of("remove_hostile_entities", FlagState.ALL)));
        EntityControlService service = new EntityControlService(plugin(), claimIndex, true, false);
        LivingEntity entity = hostileEntity(worldId, 0, 0);
        PersistentDataContainer persistentDataContainer = mock(PersistentDataContainer.class);
        when(entity.getPersistentDataContainer()).thenReturn(persistentDataContainer);
        when(persistentDataContainer.has(any(NamespacedKey.class), eq(PersistentDataType.BYTE))).thenReturn(true);

        boolean removed = service.removeIfBlocked(entity);

        assertThat(removed).isFalse();
    }

    private static Claim claim(UUID worldId, Map<String, FlagState> flags) {
        Instant now = Instant.parse("2026-06-10T00:00:00Z");
        return new Claim(
                UUID.randomUUID(),
                "Home",
                OwnerType.PLAYER,
                UUID.randomUUID(),
                worldId,
                Set.of(new ClaimChunk(worldId, 0, 0)),
                flags,
                now,
                now
        );
    }

    private static LivingEntity hostileEntity(UUID worldId, int chunkX, int chunkZ) {
        LivingEntity entity = mock(LivingEntity.class, withSettings().extraInterfaces(Enemy.class));
        World world = mock(World.class);
        Location location = mock(Location.class);
        Chunk chunk = mock(Chunk.class);
        PersistentDataContainer persistentDataContainer = mock(PersistentDataContainer.class);
        when(entity.getWorld()).thenReturn(world);
        when(entity.getLocation()).thenReturn(location);
        when(entity.getPersistentDataContainer()).thenReturn(persistentDataContainer);
        when(world.getUID()).thenReturn(worldId);
        when(location.getChunk()).thenReturn(chunk);
        when(chunk.getX()).thenReturn(chunkX);
        when(chunk.getZ()).thenReturn(chunkZ);
        return entity;
    }

    private static Plugin plugin() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("HavenClaims");
        return plugin;
    }
}
