package com.invisiblespiders.havenclaims.plugin.ui;

import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import java.util.Map;
import java.util.Objects;
import org.bukkit.entity.Player;

public final class InventoryGuiFallbackService {
    public void openClaimMenu(Player player, ClaimMenu menu, MessageService messageService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(messageService, "messageService");
        player.sendMessage(messageService.render("claim.menu.fallback-opened", Map.of()));
    }

}
