package com.nick.landclaims.plugin.visual;

import java.util.List;
import java.util.Objects;

public record ChunkBorderPlan(List<BorderEdge> edges, int durationTicks) {
    public ChunkBorderPlan {
        edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
        if (durationTicks < 0) {
            throw new IllegalArgumentException("durationTicks must be non-negative");
        }
    }
}
