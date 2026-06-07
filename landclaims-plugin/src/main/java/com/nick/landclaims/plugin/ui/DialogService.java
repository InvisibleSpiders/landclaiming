package com.nick.landclaims.plugin.ui;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class DialogService {
    public void openClaimSetup(Player player) {
        Objects.requireNonNull(player, "player");
        player.sendMessage(Component.text("Claim setup dialog coming soon.", NamedTextColor.YELLOW));
    }

    public void openAdminBrowser(Player player) {
        Objects.requireNonNull(player, "player");
        player.sendMessage(Component.text("Admin claim browser coming soon.", NamedTextColor.YELLOW));
    }
}
