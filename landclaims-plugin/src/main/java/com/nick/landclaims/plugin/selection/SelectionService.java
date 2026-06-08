package com.nick.landclaims.plugin.selection;

import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class SelectionService {
    private final ClaimService claimService;
    private final Map<UUID, ClaimChunk> firstCorners = new HashMap<>();
    private final Map<UUID, Set<ClaimChunk>> completedSelections = new HashMap<>();

    public SelectionService(ClaimService claimService) {
        this.claimService = Objects.requireNonNull(claimService, "claimService");
    }

    public Optional<Set<ClaimChunk>> select(Player player, Chunk chunk) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(chunk, "chunk");

        World world = Objects.requireNonNull(chunk.getWorld(), "chunk world");
        return select(player.getUniqueId(), new ClaimChunk(world.getUID(), chunk.getX(), chunk.getZ()));
    }

    public Optional<Set<ClaimChunk>> select(UUID playerId, ClaimChunk chunk) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(chunk, "chunk");

        ClaimChunk firstCorner = firstCorners.remove(playerId);
        if (firstCorner == null) {
            firstCorners.put(playerId, chunk);
            return Optional.empty();
        }

        if (!firstCorner.worldId().equals(chunk.worldId())) {
            firstCorners.put(playerId, chunk);
            return Optional.empty();
        }

        Set<ClaimChunk> chunks = claimService.expandRectangle(
                firstCorner.worldId(),
                firstCorner.chunkX(),
                firstCorner.chunkZ(),
                chunk.chunkX(),
                chunk.chunkZ()
        );
        completedSelections.put(playerId, chunks);
        return Optional.of(chunks);
    }

    public Optional<Set<ClaimChunk>> pendingSelection(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(completedSelections.get(playerId));
    }

    public Optional<Set<ClaimChunk>> consumeSelection(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(completedSelections.remove(playerId));
    }

    public boolean clear(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        boolean hadFirstCorner = firstCorners.remove(playerId) != null;
        boolean hadCompletedSelection = completedSelections.remove(playerId) != null;
        return hadFirstCorner || hadCompletedSelection;
    }

    public boolean clear(Player player) {
        Objects.requireNonNull(player, "player");
        return clear(player.getUniqueId());
    }
}
