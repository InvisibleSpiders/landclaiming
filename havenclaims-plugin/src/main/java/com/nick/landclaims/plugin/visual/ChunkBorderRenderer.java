package com.nick.landclaims.plugin.visual;

import java.util.UUID;
import org.bukkit.entity.Player;

public interface ChunkBorderRenderer {
    void show(Player player, ChunkBorderPlan plan);

    void clear(UUID playerId);

    void clearAll();
}
