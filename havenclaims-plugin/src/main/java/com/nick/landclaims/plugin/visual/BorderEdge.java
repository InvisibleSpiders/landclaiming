package com.nick.landclaims.plugin.visual;

import java.util.Objects;
import java.util.UUID;

public record BorderEdge(UUID worldId, int x1, int z1, int x2, int z2, double y, BorderColor color) {
    public BorderEdge {
        worldId = Objects.requireNonNull(worldId, "worldId");
        color = Objects.requireNonNull(color, "color");
    }

    public boolean alongX() {
        return z1 == z2;
    }

    public int length() {
        return Math.abs(alongX() ? x2 - x1 : z2 - z1);
    }
}
