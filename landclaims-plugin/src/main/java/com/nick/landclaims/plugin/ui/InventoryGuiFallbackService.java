package com.nick.landclaims.plugin.ui;

import com.nick.landclaims.plugin.message.MessageService;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class InventoryGuiFallbackService {
    public void openClaimMenu(Player player, ClaimMenu menu, MessageService messageService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(messageService, "messageService");
        player.sendMessage(messageService.render("claim.menu.fallback-opened", Map.of()));
    }

    public void openFallbackMenu(Player player, String title) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(title, "title");
        player.sendMessage(Component.text(title + " menu coming soon.", NamedTextColor.YELLOW));
    }
}
