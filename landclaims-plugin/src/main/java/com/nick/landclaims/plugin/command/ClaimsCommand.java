package com.nick.landclaims.plugin.command;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimCreationService;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.ClaimValidationResult;
import com.nick.landclaims.plugin.claim.PendingClaimMerge;
import com.nick.landclaims.plugin.claim.PendingClaimMergeService;
import com.nick.landclaims.plugin.economy.ClaimPaymentResult;
import com.nick.landclaims.plugin.economy.ClaimPaymentService;
import com.nick.landclaims.plugin.limit.ClaimCostMessageService;
import com.nick.landclaims.plugin.limit.ClaimCostQuote;
import com.nick.landclaims.plugin.limit.ClaimCostService;
import com.nick.landclaims.plugin.message.MessageService;
import com.nick.landclaims.plugin.selection.SelectionService;
import com.nick.landclaims.plugin.tool.ClaimToolService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;

public class ClaimsCommand implements CommandExecutor {
    private static final String CLAIM_TOOL_PERMISSION = "landclaims.tool.use";

    private final ClaimToolService claimToolService;
    private final SelectionService selectionService;
    private final ClaimCreationService claimCreationService;
    private final ClaimIndex claimIndex;
    private final ClaimCostService claimCostService;
    private final ClaimPaymentService claimPaymentService;
    private final PendingClaimMergeService pendingClaimMergeService;
    private final MessageService messageService;

    public ClaimsCommand(ClaimToolService claimToolService) {
        this(claimToolService, null, null, null, null, null, null, new MessageService(Map.of()));
    }

    public ClaimsCommand(
            ClaimToolService claimToolService,
            SelectionService selectionService,
            ClaimCreationService claimCreationService,
            ClaimIndex claimIndex,
            ClaimCostService claimCostService,
            ClaimPaymentService claimPaymentService,
            PendingClaimMergeService pendingClaimMergeService,
            MessageService messageService
    ) {
        this.claimToolService = Objects.requireNonNull(claimToolService, "claimToolService");
        this.selectionService = selectionService;
        this.claimCreationService = claimCreationService;
        this.claimIndex = claimIndex;
        this.claimCostService = claimCostService;
        this.claimPaymentService = claimPaymentService;
        this.pendingClaimMergeService = pendingClaimMergeService;
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("command.player-only"));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("tool")) {
            return giveTool(player);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("create")) {
            return createClaim(player, args);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("mergeconfirm")) {
            return confirmPendingMerge(player);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("mergecancel")) {
            return cancelPendingMerge(player);
        }
        if (args.length == 1 && (args[0].equalsIgnoreCase("cost") || args[0].equalsIgnoreCase("quote"))) {
            return previewClaimCost(player);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
            return cancelSelection(player);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("info")) {
            return showInfo(player);
        }

        sendHelp(player);
        return true;
    }

    private boolean previewClaimCost(Player player) {
        if (!isClaimCreationAvailable(player)) {
            return true;
        }
        if (claimCostService == null || claimPaymentService == null) {
            player.sendMessage(message("command.unavailable.claim-cost"));
            return true;
        }

        Optional<Set<ClaimChunk>> pendingSelection = selectionService.pendingSelection(player.getUniqueId());
        if (pendingSelection.isEmpty()) {
            player.sendMessage(message("claim.selection-required"));
            return true;
        }

        ClaimCostQuote quote = claimCostService.quotePlayerClaim(
                player.getUniqueId(),
                permissionNodes(player),
                pendingSelection.orElseThrow()
        );
        ClaimCostMessageService.preview(quote, claimPaymentService.format(quote.cost()), messageService)
                .forEach(player::sendMessage);
        return true;
    }

    private boolean giveTool(Player player) {
        if (!player.hasPermission(CLAIM_TOOL_PERMISSION)) {
            player.sendMessage(message("command.tool.no-permission"));
            return true;
        }

        player.getInventory().addItem(claimToolService.createClaimTool());
        player.sendMessage(message("command.tool.given"));
        return true;
    }

    private boolean createClaim(Player player, String[] args) {
        return createClaim(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)), false);
    }

    private boolean createClaim(Player player, String claimName, boolean mergeConfirmed) {
        if (!isClaimCreationAvailable(player)) {
            return true;
        }

        Optional<Set<ClaimChunk>> pendingSelection = selectionService.pendingSelection(player.getUniqueId());
        if (pendingSelection.isEmpty()) {
            player.sendMessage(message("claim.selection-required"));
            return true;
        }

        Set<ClaimChunk> chunks = pendingSelection.orElseThrow();
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (!claimToolService.isClaimTool(mainHandItem)) {
            player.sendMessage(message("claim.hold-tool"));
            return true;
        }
        if (claimToolService.currentCharges(mainHandItem) < chunks.size()) {
            player.sendMessage(message("claim.not-enough-charges", Map.of(
                    "needed", String.valueOf(chunks.size()),
                    "available", String.valueOf(claimToolService.currentCharges(mainHandItem))
            )));
            return true;
        }

        ClaimValidationResult validationResult = claimCreationService.validatePlayerClaim(player.getUniqueId(), claimName, chunks);
        if (!validationResult.isAllowed()) {
            player.sendMessage(claimCreateDenied(validationResult.messageKey().orElse("claims.denied")));
            return true;
        }

        List<Claim> mergeTargets = claimCreationService.findMergeTargets(player.getUniqueId(), claimName, chunks);
        if (!mergeTargets.isEmpty() && !mergeConfirmed) {
            if (pendingClaimMergeService != null) {
                pendingClaimMergeService.put(player.getUniqueId(), claimName, chunks);
            }
            sendMergeConfirmation(player, claimName, mergeTargets.size());
            return true;
        }

        if (claimCostService != null && claimPaymentService != null) {
            ClaimCostQuote quote = claimCostService.quotePlayerClaim(player.getUniqueId(), permissionNodes(player), chunks);
            ClaimPaymentResult paymentResult = claimPaymentService.charge(player.getUniqueId(), quote);
            if (!paymentResult.allowed()) {
                player.sendMessage(claimCreateDenied(paymentResult.messageKey()));
                return true;
            }
            if (quote.cost() > 0.0) {
                player.sendMessage(message("claim.charged", Map.of(
                        "cost", claimPaymentService.format(quote.cost())
                )));
            }
        }

        ClaimValidationResult result = claimCreationService.createPlayerClaim(player.getUniqueId(), claimName, chunks);
        if (!result.isAllowed()) {
            player.sendMessage(claimCreateDenied(result.messageKey().orElse("claims.denied")));
            return true;
        }

        claimToolService.spendCharges(mainHandItem, chunks.size());
        selectionService.consumeSelection(player.getUniqueId());
        player.sendMessage(message("claim.created", Map.of(
                "claim_name", claimName.trim(),
                "chunk_count", String.valueOf(chunks.size())
        )));
        return true;
    }

    private boolean confirmPendingMerge(Player player) {
        if (pendingClaimMergeService == null) {
            player.sendMessage(message("claim.merge.unavailable"));
            return true;
        }

        Optional<PendingClaimMerge> pendingMerge = pendingClaimMergeService.consume(player.getUniqueId());
        if (pendingMerge.isEmpty()) {
            player.sendMessage(message("claim.merge.none-pending"));
            return true;
        }

        Optional<Set<ClaimChunk>> pendingSelection = selectionService.pendingSelection(player.getUniqueId());
        if (pendingSelection.isEmpty() || !pendingSelection.orElseThrow().equals(pendingMerge.orElseThrow().chunks())) {
            player.sendMessage(message("claim.merge.selection-changed"));
            return true;
        }

        return createClaim(player, pendingMerge.orElseThrow().claimName(), true);
    }

    private boolean cancelPendingMerge(Player player) {
        if (pendingClaimMergeService != null) {
            pendingClaimMergeService.clear(player.getUniqueId());
        }
        player.sendMessage(message("claim.merge.cancelled"));
        return true;
    }

    private void sendMergeConfirmation(Player player, String claimName, int mergeCount) {
        player.sendMessage(message("claim.merge.prompt", Map.of(
                "claim_name", claimName.trim(),
                "merge_count", String.valueOf(mergeCount)
        ))
                .append(message("claim.merge.confirm-button")
                        .clickEvent(ClickEvent.runCommand("/claims mergeconfirm")))
                .append(Component.space())
                .append(message("claim.merge.cancel-button")
                        .clickEvent(ClickEvent.runCommand("/claims mergecancel"))));
    }

    private Set<String> permissionNodes(Player player) {
        return player.getEffectivePermissions().stream()
                .filter(PermissionAttachmentInfo::getValue)
                .map(PermissionAttachmentInfo::getPermission)
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean cancelSelection(Player player) {
        if (selectionService == null) {
            player.sendMessage(message("command.unavailable.claim-creation"));
            return true;
        }

        if (pendingClaimMergeService != null) {
            pendingClaimMergeService.clear(player.getUniqueId());
        }
        selectionService.clear(player);
        player.sendMessage(message("command.selection.cleared"));
        return true;
    }

    private boolean showInfo(Player player) {
        if (claimIndex == null) {
            player.sendMessage(message("command.unavailable.claim-info"));
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();
        ClaimChunk claimChunk = new ClaimChunk(player.getWorld().getUID(), chunk.getX(), chunk.getZ());
        Optional<Claim> claim = claimIndex.findAt(claimChunk);
        if (claim.isEmpty()) {
            player.sendMessage(message("claim.info.unclaimed"));
            return true;
        }

        Claim foundClaim = claim.orElseThrow();
        player.sendMessage(message("claim.info.name", Map.of("claim_name", foundClaim.name())));
        player.sendMessage(message("claim.info.owner-type", Map.of("owner_type", foundClaim.owner().name())));
        player.sendMessage(message("claim.info.chunks", Map.of("chunk_count", String.valueOf(foundClaim.claimChunks().size()))));
        player.sendMessage(message("claim.info.you-own", Map.of("is_owner", String.valueOf(player.getUniqueId().equals(foundClaim.ownerUuid())))));
        return true;
    }

    private boolean isClaimCreationAvailable(Player player) {
        if (selectionService == null || claimCreationService == null || claimIndex == null) {
            player.sendMessage(message("command.unavailable.claim-creation"));
            return false;
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(message("command.help.title"));
        player.sendMessage(message("command.help.tool"));
        player.sendMessage(message("command.help.create"));
        player.sendMessage(message("command.help.cost"));
        player.sendMessage(message("command.help.cancel"));
        player.sendMessage(message("command.help.info"));
    }

    private Component claimCreateDenied(String reason) {
        return message("claim.create-denied", Map.of("reason", reason));
    }

    private Component message(String key) {
        return message(key, Map.of());
    }

    private Component message(String key, Map<String, String> placeholders) {
        return messageService.render(key, placeholders);
    }
}
