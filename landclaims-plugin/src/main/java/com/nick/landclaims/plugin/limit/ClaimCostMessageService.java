package com.nick.landclaims.plugin.limit;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class ClaimCostMessageService {
    private ClaimCostMessageService() {
    }

    public static List<Component> preview(ClaimCostQuote quote, String formattedCost) {
        String costText = quote.cost() <= 0.0 ? "free" : formattedCost;
        return List.of(
                Component.text("Selection: ", NamedTextColor.GRAY)
                        .append(Component.text(quote.selectedChunks(), NamedTextColor.YELLOW))
                        .append(Component.text(" chunks", NamedTextColor.GRAY)),
                Component.text("Current total after claim: ", NamedTextColor.GRAY)
                        .append(Component.text(quote.proposedTotalChunks(), NamedTextColor.YELLOW))
                        .append(Component.text(" / ", NamedTextColor.GRAY))
                        .append(Component.text(quote.allowedChunks(), NamedTextColor.YELLOW))
                        .append(Component.text(" chunks", NamedTextColor.GRAY)),
                Component.text("Over limit: ", NamedTextColor.GRAY)
                        .append(Component.text(quote.overageChunks(), NamedTextColor.YELLOW))
                        .append(Component.text(" chunks", NamedTextColor.GRAY)),
                Component.text("Cost: ", NamedTextColor.GRAY)
                        .append(Component.text(costText, quote.cost() <= 0.0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
        );
    }
}
