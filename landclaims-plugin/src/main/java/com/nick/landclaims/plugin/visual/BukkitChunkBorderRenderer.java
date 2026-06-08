package com.nick.landclaims.plugin.visual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class BukkitChunkBorderRenderer implements ChunkBorderRenderer {
    private final JavaPlugin plugin;
    private final double thickness;
    private final float viewRange;
    private final Map<UUID, List<Entity>> activeEntities = new HashMap<>();
    private final Map<UUID, BukkitTask> clearTasks = new HashMap<>();

    public BukkitChunkBorderRenderer(JavaPlugin plugin, double thickness, float viewRange) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.thickness = Math.max(0.01D, thickness);
        this.viewRange = Math.max(1.0F, viewRange);
    }

    @Override
    public void show(Player player, ChunkBorderPlan plan) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(plan, "plan");

        UUID playerId = player.getUniqueId();
        clear(playerId);
        List<Entity> entities = new ArrayList<>();
        for (BorderEdge edge : plan.edges()) {
            World world = Bukkit.getWorld(edge.worldId());
            if (world == null) {
                continue;
            }
            BlockDisplay display = world.spawn(displayLocation(world, edge), BlockDisplay.class, entity -> configureDisplay(entity, edge));
            player.showEntity(plugin, display);
            entities.add(display);
        }
        if (!entities.isEmpty()) {
            activeEntities.put(playerId, entities);
            clearTasks.put(playerId, Bukkit.getScheduler().runTaskLater(plugin, () -> clear(playerId), plan.durationTicks()));
        }
    }

    @Override
    public void clear(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        BukkitTask task = clearTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        List<Entity> entities = activeEntities.remove(playerId);
        if (entities != null) {
            entities.forEach(Entity::remove);
        }
    }

    @Override
    public void clearAll() {
        List<UUID> playerIds = new ArrayList<>(activeEntities.keySet());
        playerIds.forEach(this::clear);
    }

    private void configureDisplay(BlockDisplay display, BorderEdge edge) {
        display.setBlock(edge.color().displayMaterial().createBlockData());
        display.setPersistent(false);
        display.setVisibleByDefault(false);
        display.setGlowing(true);
        display.setGlowColorOverride(edge.color().bukkitColor());
        display.setViewRange(viewRange);
        display.setTransformation(transformation(edge));
    }

    private Location displayLocation(World world, BorderEdge edge) {
        double x = Math.min(edge.x1(), edge.x2());
        double z = Math.min(edge.z1(), edge.z2());
        return new Location(world, x, edge.y(), z);
    }

    private Transformation transformation(BorderEdge edge) {
        float length = Math.max(1, edge.length());
        float lineThickness = (float) thickness;
        Vector3f scale = edge.alongX()
                ? new Vector3f(length, lineThickness, lineThickness)
                : new Vector3f(lineThickness, lineThickness, length);
        return new Transformation(new Vector3f(), new AxisAngle4f(), scale, new AxisAngle4f());
    }
}
