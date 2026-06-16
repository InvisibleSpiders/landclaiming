package com.invisiblespiders.havenclaims.plugin.claimmode;

import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class ClaimModeCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ACTION_SUGGESTIONS = List.of("on", "off", "toggle");

    private final ClaimModeService claimModeService;
    private final MessageService messageService;

    public ClaimModeCommand(ClaimModeService claimModeService, MessageService messageService) {
        this.claimModeService = Objects.requireNonNull(claimModeService, "claimModeService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageService.render("command.player-only", Map.of()));
            return true;
        }
        return execute(player, ClaimModeAction.from(args));
    }

    public boolean execute(Player player, ClaimModeAction action) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");
        boolean active = claimModeService.isInClaimMode(player.getUniqueId());
        if (action == ClaimModeAction.ON && active) {
            player.sendMessage(messageService.render("claim-mode.already-active", Map.of()));
            return true;
        }
        if (action == ClaimModeAction.OFF || (action == ClaimModeAction.TOGGLE && active)) {
            sendExitMessage(player, claimModeService.exit(player, ClaimModeService.ExitReason.MANUAL));
            return true;
        }
        sendEnterMessage(player, claimModeService.enter(player));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return ACTION_SUGGESTIONS.stream()
                .filter(option -> option.startsWith(prefix))
                .toList();
    }

    private void sendEnterMessage(Player player, ClaimModeService.EnterResult result) {
        String key = switch (result) {
            case ENTERED -> "claim-mode.entered";
            case DISABLED -> "claim-mode.disabled";
            case NO_PERMISSION -> "command.claim.no-permission";
            case ALREADY_ACTIVE -> "claim-mode.already-active";
        };
        player.sendMessage(messageService.render(key, Map.of()));
    }

    private void sendExitMessage(Player player, ClaimModeService.ExitResult result) {
        String key = switch (result) {
            case RESTORED -> "claim-mode.exited";
            case PARTIAL -> "claim-mode.restore-partial";
            case RECOVERY -> "claim-mode.restore-recovery";
            case NOT_ACTIVE -> "claim-mode.not-active";
        };
        player.sendMessage(messageService.render(key, Map.of()));
    }
}
