package com.invisiblespiders.havenclaims.plugin.visual;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class ChunkBorderVisualService {
    private static final double GROUND_OFFSET = 1.05D;

    private final ChunkBorderRenderer renderer;
    private final int durationTicks;
    private final ChunkGroundHeightProvider heightProvider;
    private final Set<UUID> activePlayers = new HashSet<>();

    public ChunkBorderVisualService(ChunkBorderRenderer renderer, int durationTicks) {
        this(renderer, durationTicks, ChunkBorderVisualService::groundBorderY);
    }

    ChunkBorderVisualService(ChunkBorderRenderer renderer, int durationTicks, ChunkGroundHeightProvider heightProvider) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        if (durationTicks < 0) {
            throw new IllegalArgumentException("durationTicks must be non-negative");
        }
        this.durationTicks = durationTicks;
        this.heightProvider = Objects.requireNonNull(heightProvider, "heightProvider");
    }

    public void showSelection(Player player, Set<ClaimChunk> chunks, BorderColor color) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(color, "color");
        if (chunks.isEmpty()) {
            clear(player.getUniqueId());
            return;
        }

        UUID playerId = player.getUniqueId();
        if (activePlayers.contains(playerId)) {
            renderer.clear(playerId);
        }
        activePlayers.add(playerId);
        renderer.show(player, ChunkBorderPlanner.plan(chunks, heightProvider, color, durationTicks));
    }

    /** Shows a persistent border (no auto-expiry) for use during active selection. */
    public void showPersistentSelection(Player player, Set<ClaimChunk> chunks, BorderColor color) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(color, "color");
        if (chunks.isEmpty()) {
            clear(player.getUniqueId());
            return;
        }
        UUID playerId = player.getUniqueId();
        if (activePlayers.contains(playerId)) {
            renderer.clear(playerId);
        }
        activePlayers.add(playerId);
        renderer.show(player, ChunkBorderPlanner.plan(chunks, heightProvider, color, 0));
    }

    /** Shows a persistent border (no auto-expiry) for use during active selection. */
    public void showPersistentSelection(Player player, ClaimRegion region, BorderColor color) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(color, "color");
        UUID playerId = player.getUniqueId();
        if (activePlayers.contains(playerId)) {
            renderer.clear(playerId);
        }
        activePlayers.add(playerId);
        renderer.show(player, ChunkBorderPlanner.planRectangle(region, heightProvider, color, 0));
    }

    public void showSelection(Player player, ClaimRegion region, BorderColor color) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(color, "color");

        UUID playerId = player.getUniqueId();
        if (activePlayers.contains(playerId)) {
            renderer.clear(playerId);
        }
        activePlayers.add(playerId);
        renderer.show(player, ChunkBorderPlanner.planRectangle(region, heightProvider, color, durationTicks));
    }

    public boolean clear(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!activePlayers.remove(playerId)) {
            return false;
        }
        renderer.clear(playerId);
        return true;
    }

    public void clearAll() {
        activePlayers.clear();
        renderer.clearAll();
    }

    private static double groundBorderY(UUID worldId, int blockX, int blockZ) {
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            return 0.0D;
        }
        // Use MOTION_BLOCKING_NO_LEAVES so the border sits on solid walkable ground
        // rather than on top of leaf canopy, tall grass, or snow layers.
        return world.getHighestBlockYAt(blockX, blockZ, HeightMap.MOTION_BLOCKING_NO_LEAVES) + GROUND_OFFSET;
    }
}
