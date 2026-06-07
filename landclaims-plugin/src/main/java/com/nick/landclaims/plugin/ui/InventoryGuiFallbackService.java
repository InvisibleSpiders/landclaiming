package com.nick.landclaims.plugin.ui;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class InventoryGuiFallbackService {
    public void openFallbackMenu(Player player, String title) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(title, "title");
        player.sendMessage(Component.text(title + " menu coming soon.", NamedTextColor.YELLOW));
    }
}
