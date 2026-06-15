package com.invisiblespiders.havenclaims.plugin.visual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ChunkBorderVisualServiceTest {
    @Test
    void showSelectionReplacesPreviousVisualForPlayer() {
        RecordingRenderer renderer = new RecordingRenderer();
        ChunkBorderVisualService service = new ChunkBorderVisualService(renderer, 160, (worldId, blockX, blockZ) -> 64.0D);
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        Player player = player(playerId, 72.0);

        service.showSelection(player, Set.of(new ClaimChunk(worldId, 0, 0)), BorderColor.GREEN);
        service.showSelection(player, Set.of(new ClaimChunk(worldId, 1, 0)), BorderColor.RED);

        assertThat(renderer.clearCalls()).isEqualTo(1);
        assertThat(renderer.lastPlan().edges()).allSatisfy(edge -> assertThat(edge.color()).isEqualTo(BorderColor.RED));
    }

    @Test
    void clearRemovesActiveVisualForPlayerOnlyWhenPresent() {
        RecordingRenderer renderer = new RecordingRenderer();
        ChunkBorderVisualService service = new ChunkBorderVisualService(renderer, 160, (worldId, blockX, blockZ) -> 64.0D);
        UUID playerId = UUID.randomUUID();

        assertThat(service.clear(playerId)).isFalse();

        service.showSelection(player(playerId, 64.0), Set.of(new ClaimChunk(UUID.randomUUID(), 0, 0)), BorderColor.GREEN);

        assertThat(service.clear(playerId)).isTrue();
        assertThat(service.clear(playerId)).isFalse();
        assertThat(renderer.clearCalls()).isEqualTo(1);
    }

    @Test
    void showSelectionUsesGroundHeightProviderInsteadOfPlayerY() {
        RecordingRenderer renderer = new RecordingRenderer();
        ChunkBorderVisualService service = new ChunkBorderVisualService(
                renderer,
                100,
                (worldId, blockX, blockZ) -> 80.0D + blockX + blockZ
        );
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        service.showSelection(player(playerId, 20.0), Set.of(new ClaimChunk(worldId, 0, 0)), BorderColor.GREEN);

        assertThat(renderer.lastPlan().edges()).hasSize(64);
        assertThat(renderer.lastPlan().edges())
                .noneMatch(edge -> edge.y() == 20.15D)
                .anySatisfy(edge -> {
                    assertThat(edge.x1()).isEqualTo(0);
                    assertThat(edge.z1()).isEqualTo(0);
                    assertThat(edge.y()).isEqualTo(80.0D);
                });
    }

    private static Player player(UUID playerId, double y) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getY()).thenReturn(y);
        return player;
    }

    private static final class RecordingRenderer implements ChunkBorderRenderer {
        private int clearCalls;
        private ChunkBorderPlan lastPlan;

        @Override
        public void show(Player player, ChunkBorderPlan plan) {
            this.lastPlan = plan;
        }

        @Override
        public void clear(UUID playerId) {
            clearCalls++;
        }

        @Override
        public void clearAll() {
        }

        private int clearCalls() {
            return clearCalls;
        }

        private ChunkBorderPlan lastPlan() {
            return lastPlan;
        }
    }
}
