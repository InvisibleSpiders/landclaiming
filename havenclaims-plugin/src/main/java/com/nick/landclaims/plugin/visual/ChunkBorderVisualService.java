package com.nick.landclaims.plugin.visual;

import com.nick.landclaims.plugin.claim.ClaimChunk;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class ChunkBorderVisualService {
    private final ChunkBorderRenderer renderer;
    private final int durationTicks;
    private final Set<UUID> activePlayers = new HashSet<>();

    public ChunkBorderVisualService(ChunkBorderRenderer renderer, int durationTicks) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        if (durationTicks < 1) {
            throw new IllegalArgumentException("durationTicks must be at least 1");
        }
        this.durationTicks = durationTicks;
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
        renderer.show(player, ChunkBorderPlanner.plan(chunks, player.getY() + 0.15D, color, durationTicks));
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
}
