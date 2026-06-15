package com.invisiblespiders.havenclaims.plugin.visual;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkBorderPlannerTest {
    @Test
    void singleChunkHasFourOuterEdges() {
        UUID worldId = UUID.randomUUID();
        ChunkBorderPlan plan = ChunkBorderPlanner.plan(
                Set.of(new ClaimChunk(worldId, 2, -1)),
                64.5,
                BorderColor.GREEN,
                160
        );

        assertThat(plan.edges()).hasSize(4);
        assertThat(plan.edges()).allSatisfy(edge -> {
            assertThat(edge.worldId()).isEqualTo(worldId);
            assertThat(edge.y()).isEqualTo(64.5);
            assertThat(edge.color()).isEqualTo(BorderColor.GREEN);
        });
        assertThat(plan.durationTicks()).isEqualTo(160);
    }

    @Test
    void adjacentChunksOmitSharedInternalEdge() {
        UUID worldId = UUID.randomUUID();
        ChunkBorderPlan plan = ChunkBorderPlanner.plan(
                Set.of(
                        new ClaimChunk(worldId, 0, 0),
                        new ClaimChunk(worldId, 1, 0)
                ),
                70.0,
                BorderColor.YELLOW,
                80
        );

        assertThat(plan.edges()).hasSize(6);
        assertThat(plan.edges())
                .noneMatch(edge -> edge.x1() == 16 && edge.x2() == 16 && edge.z1() == 0 && edge.z2() == 16);
    }
}
