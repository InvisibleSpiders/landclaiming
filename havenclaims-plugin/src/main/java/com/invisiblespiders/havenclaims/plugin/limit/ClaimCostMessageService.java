package com.invisiblespiders.havenclaims.plugin.limit;

import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;

public final class ClaimCostMessageService {
    private ClaimCostMessageService() {
    }

    public static List<Component> preview(ClaimCostQuote quote, String formattedCost, MessageService messageService) {
        String costText = quote.cost() <= 0.0
                ? messageService.renderPlain("claim.cost-preview.free", Map.of())
                : formattedCost;
        Map<String, String> placeholders = Map.of(
                "selected_chunks", String.valueOf(quote.selectedChunks()),
                "proposed_total_chunks", String.valueOf(quote.proposedTotalChunks()),
                "allowed_chunks", String.valueOf(quote.allowedChunks()),
                "overage_chunks", String.valueOf(quote.overageChunks()),
                "cost", costText
        );
        return List.of(
                messageService.render("claim.cost-preview.selection", placeholders),
                messageService.render("claim.cost-preview.current-total", placeholders),
                messageService.render("claim.cost-preview.over-limit", placeholders),
                messageService.render("claim.cost-preview.cost", placeholders)
        );
    }
}
