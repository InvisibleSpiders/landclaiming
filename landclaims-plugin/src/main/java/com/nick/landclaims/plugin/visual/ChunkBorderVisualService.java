package com.nick.landclaims.plugin.visual;

import com.nick.landclaims.plugin.claim.ClaimChunk;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
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
        return world.getHighestBlockYAt(blockX, blockZ) + GROUND_OFFSET;
    }
}
