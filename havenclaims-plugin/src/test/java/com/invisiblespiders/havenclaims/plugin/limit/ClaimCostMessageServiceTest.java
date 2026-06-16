package com.invisiblespiders.havenclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ClaimCostMessageServiceTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void previewShowsFreeSelectionWhenNoOverageCostApplies() {
        ClaimCostQuote quote = new ClaimCostQuote(10, 2, 3, 5, 0, 0.0);

        List<Component> lines = ClaimCostMessageService.preview(quote, "0.00", messageService());

        assertThat(plain(lines)).containsExactly(
                "Selection: 3 blocks",
                "Current total after claim: 5 / 10 blocks",
                "Over limit: 0 blocks",
                "Cost: free"
        );
    }

    @Test
    void previewShowsFormattedOverLimitCost() {
        ClaimCostQuote quote = new ClaimCostQuote(10, 9, 3, 12, 2, 275.5);

        List<Component> lines = ClaimCostMessageService.preview(quote, "$275.50", messageService());

        assertThat(plain(lines)).containsExactly(
                "Selection: 3 blocks",
                "Current total after claim: 12 / 10 blocks",
                "Over limit: 2 blocks",
                "Cost: $275.50"
        );
    }

    private static List<String> plain(List<Component> lines) {
        return lines.stream().map(PLAIN::serialize).toList();
    }

    private static MessageService messageService() {
        return new MessageService(Map.of(
                "claim.cost-preview.selection", "<gray>Selection: <yellow><selected_blocks></yellow> blocks",
                "claim.cost-preview.current-total", "<gray>Current total after claim: <yellow><proposed_total_blocks></yellow> / <yellow><allowed_blocks></yellow> blocks",
                "claim.cost-preview.over-limit", "<gray>Over limit: <yellow><overage_blocks></yellow> blocks",
                "claim.cost-preview.cost", "<gray>Cost: <green><cost></green>",
                "claim.cost-preview.free", "free"
        ));
    }
}
