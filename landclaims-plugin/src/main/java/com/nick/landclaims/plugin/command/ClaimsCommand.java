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
    private static final String CLAIM_TOOL_PERMISSION = "landclaims.tool.use";

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
            if (!player.hasPermission(CLAIM_TOOL_PERMISSION)) {
                player.sendMessage(Component.text("You do not have permission to use the claim tool.", NamedTextColor.RED));
                return true;
            }

            player.getInventory().addItem(claimToolService.createClaimTool());
            player.sendMessage(Component.text("Claim tool added to your inventory.", NamedTextColor.GREEN));
            return true;
        }

        player.sendMessage(Component.text("LandClaims commands", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/claims tool", NamedTextColor.YELLOW)
                .append(Component.text(" - gives you the configured claiming tool.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("Right-click two chunks with the tool to preview a selection.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Claim creation menus are planned for the next playable build.", NamedTextColor.GRAY));
        return true;
    }
}
