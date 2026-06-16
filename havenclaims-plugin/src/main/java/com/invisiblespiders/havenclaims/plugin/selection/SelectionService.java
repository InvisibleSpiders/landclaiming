package com.invisiblespiders.havenclaims.plugin.selection;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimRegion;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimService;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class SelectionService {
    private final ClaimService claimService;
    private final Map<UUID, BlockPos> firstCorners = new HashMap<>();
    private final Map<UUID, ClaimRegion> completedSelections = new HashMap<>();

    public SelectionService(ClaimService claimService) {
        this.claimService = Objects.requireNonNull(claimService, "claimService");
    }

    /** Called by ClaimToolListener when a player right-clicks a block. */
    public Optional<ClaimRegion> select(Player player, Block block) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(block, "block");
        UUID worldId = block.getWorld().getUID();
        return select(player.getUniqueId(), new BlockPos(worldId, block.getX(), block.getZ()));
    }

    /** Testable overload — takes UUID and BlockPos directly. */
    public Optional<ClaimRegion> select(UUID playerId, BlockPos pos) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(pos, "pos");

        BlockPos firstCorner = firstCorners.get(playerId);
        if (firstCorner == null) {
            completedSelections.remove(playerId);
            firstCorners.put(playerId, pos);
            return Optional.empty();
        }

        if (!firstCorner.worldId().equals(pos.worldId())) {
            completedSelections.remove(playerId);
            firstCorners.put(playerId, pos);
            return Optional.empty();
        }

        ClaimRegion region = claimService.blockRectangle(firstCorner, pos);
        completedSelections.put(playerId, region);
        firstCorners.remove(playerId);
        return Optional.of(region);
    }

    public Optional<ClaimRegion> pendingSelection(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return Optional.ofNullable(completedSelections.get(playerId));
    }

    public Optional<ClaimRegion> consumeSelection(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        firstCorners.remove(playerId);
        return Optional.ofNullable(completedSelections.remove(playerId));
    }

    public boolean clear(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        boolean hadFirst = firstCorners.remove(playerId) != null;
        boolean hadCompleted = completedSelections.remove(playerId) != null;
        return hadFirst || hadCompleted;
    }

    public boolean clear(Player player) {
        Objects.requireNonNull(player, "player");
        return clear(player.getUniqueId());
    }
}
