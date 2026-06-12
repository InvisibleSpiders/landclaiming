package com.nick.landclaims.plugin.entity;

import com.nick.landclaims.api.flag.FlagState;
import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Golem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WaterMob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class EntityControlService {
    private static final String REMOVE_HOSTILE_FLAG = "remove_hostile_entities";
    private static final String REMOVE_PASSIVE_FLAG = "remove_passive_entities";
    private static final String PLAYER_NAMED_ENTITY_KEY = "player_named_entity";

    private final Plugin plugin;
    private final ClaimIndex claimIndex;
    private final NamespacedKey playerNamedEntityKey;
    private final boolean preserveNamedEntities;
    private final boolean preserveTamedEntities;
    private BukkitTask cleanupTask;

    public EntityControlService(
            Plugin plugin,
            ClaimIndex claimIndex,
            boolean preserveNamedEntities,
            boolean preserveTamedEntities
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.claimIndex = Objects.requireNonNull(claimIndex, "claimIndex");
        this.playerNamedEntityKey = new NamespacedKey(plugin.getName().toLowerCase(Locale.ROOT), PLAYER_NAMED_ENTITY_KEY);
        this.preserveNamedEntities = preserveNamedEntities;
        this.preserveTamedEntities = preserveTamedEntities;
    }

    public void start(long intervalTicks) {
        if (intervalTicks < 1) {
            return;
        }
        stop();
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupAllWorlds, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    public void markPlayerNamedEntity(LivingEntity entity) {
        Objects.requireNonNull(entity, "entity");
        entity.getPersistentDataContainer().set(playerNamedEntityKey, PersistentDataType.BYTE, (byte) 1);
    }

    public boolean removeIfBlocked(LivingEntity entity) {
        Objects.requireNonNull(entity, "entity");
        if (entity instanceof Player || shouldPreserve(entity)) {
            return false;
        }

        ClaimChunk chunk = new ClaimChunk(
                entity.getWorld().getUID(),
                entity.getLocation().getChunk().getX(),
                entity.getLocation().getChunk().getZ()
        );
        return claimIndex.findAt(chunk)
                .filter(claim -> shouldRemove(entity, claim))
                .map(claim -> {
                    entity.remove();
                    return true;
                })
                .orElse(false);
    }

    private void cleanupAllWorlds() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                removeIfBlocked(entity);
            }
        }
    }

    private boolean shouldRemove(LivingEntity entity, Claim claim) {
        if (isHostile(entity)) {
            return claim.flags().getOrDefault(REMOVE_HOSTILE_FLAG, FlagState.OFF) != FlagState.OFF;
        }
        if (isPassive(entity)) {
            return claim.flags().getOrDefault(REMOVE_PASSIVE_FLAG, FlagState.OFF) != FlagState.OFF;
        }
        return false;
    }

    private boolean shouldPreserve(LivingEntity entity) {
        if (preserveNamedEntities
                && entity.getPersistentDataContainer().has(playerNamedEntityKey, PersistentDataType.BYTE)) {
            return true;
        }
        return preserveTamedEntities && entity instanceof Tameable tameable && tameable.isTamed();
    }

    private boolean isHostile(Entity entity) {
        return entity instanceof Enemy;
    }

    private boolean isPassive(Entity entity) {
        return entity instanceof Animals
                || entity instanceof Ambient
                || entity instanceof Golem
                || entity instanceof Villager
                || entity instanceof WaterMob;
    }
}
