package com.nick.landclaims.plugin.limit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ClaimCostMessageServiceTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void previewShowsFreeSelectionWhenNoOverageCostApplies() {
        ClaimCostQuote quote = new ClaimCostQuote(10, 2, 3, 5, 0, 0.0);

        List<Component> lines = ClaimCostMessageService.preview(quote, "0.00");

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

        List<Component> lines = ClaimCostMessageService.preview(quote, "$275.50");

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
}
