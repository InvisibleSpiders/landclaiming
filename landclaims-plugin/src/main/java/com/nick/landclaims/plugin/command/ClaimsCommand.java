package com.nick.landclaims.plugin.command;

import com.nick.landclaims.plugin.tool.ClaimToolService;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ClaimsCommand implements CommandExecutor {
    private final ClaimToolService claimToolService;

    public ClaimsCommand(ClaimToolService claimToolService) {
        this.claimToolService = Objects.requireNonNull(claimToolService, "claimToolService");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use LandClaims commands.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("tool")) {
            player.getInventory().addItem(claimToolService.createClaimTool());
            player.sendMessage(Component.text("Claim tool added to your inventory.", NamedTextColor.GREEN));
            return true;
        }

        player.sendMessage(Component.text("LandClaims menu coming soon.", NamedTextColor.YELLOW));
        return true;
    }
}
