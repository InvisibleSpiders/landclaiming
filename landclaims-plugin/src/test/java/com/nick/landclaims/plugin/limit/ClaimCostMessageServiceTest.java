package com.nick.landclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import com.nick.landclaims.plugin.message.MessageService;
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
                "Selection: 3 chunks",
                "Current total after claim: 5 / 10 chunks",
                "Over limit: 0 chunks",
                "Cost: free"
        );
    }

    @Test
    void previewShowsFormattedOverLimitCost() {
        ClaimCostQuote quote = new ClaimCostQuote(10, 9, 3, 12, 2, 275.5);

        List<Component> lines = ClaimCostMessageService.preview(quote, "$275.50", messageService());

        assertThat(plain(lines)).containsExactly(
                "Selection: 3 chunks",
                "Current total after claim: 12 / 10 chunks",
                "Over limit: 2 chunks",
                "Cost: $275.50"
        );
    }

    private static List<String> plain(List<Component> lines) {
        return lines.stream().map(PLAIN::serialize).toList();
    }

    private static MessageService messageService() {
        return new MessageService(Map.of(
                "claim.cost-preview.selection", "<gray>Selection: <yellow><selected_chunks></yellow> chunks",
                "claim.cost-preview.current-total", "<gray>Current total after claim: <yellow><proposed_total_chunks></yellow> / <yellow><allowed_chunks></yellow> chunks",
                "claim.cost-preview.over-limit", "<gray>Over limit: <yellow><overage_chunks></yellow> chunks",
                "claim.cost-preview.cost", "<gray>Cost: <green><cost></green>",
                "claim.cost-preview.free", "free"
        ));
    }
}
