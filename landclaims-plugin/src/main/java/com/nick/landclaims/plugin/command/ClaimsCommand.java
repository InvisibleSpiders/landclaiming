package com.nick.landclaims.plugin.command;

import com.nick.landclaims.plugin.claim.Claim;
import com.nick.landclaims.plugin.claim.ClaimChunk;
import com.nick.landclaims.plugin.claim.ClaimCreationService;
import com.nick.landclaims.plugin.claim.ClaimIndex;
import com.nick.landclaims.plugin.claim.ClaimValidationResult;
import com.nick.landclaims.plugin.economy.ClaimPaymentResult;
import com.nick.landclaims.plugin.economy.ClaimPaymentService;
import com.nick.landclaims.plugin.limit.ClaimCostMessageService;
import com.nick.landclaims.plugin.limit.ClaimCostQuote;
import com.nick.landclaims.plugin.limit.ClaimCostService;
import com.nick.landclaims.plugin.selection.SelectionService;
import com.nick.landclaims.plugin.tool.ClaimToolService;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

    public ClaimsCommand(ClaimToolService claimToolService) {
        this(claimToolService, null, null, null, null, null);
    }

    public ClaimsCommand(
            ClaimToolService claimToolService,
            SelectionService selectionService,
            ClaimCreationService claimCreationService,
            ClaimIndex claimIndex,
            ClaimCostService claimCostService,
            ClaimPaymentService claimPaymentService
    ) {
        this.claimToolService = Objects.requireNonNull(claimToolService, "claimToolService");
        this.selectionService = selectionService;
        this.claimCreationService = claimCreationService;
        this.claimIndex = claimIndex;
        this.claimCostService = claimCostService;
        this.claimPaymentService = claimPaymentService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use LandClaims commands.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("tool")) {
            return giveTool(player);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("create")) {
            return createClaim(player, args);
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
            player.sendMessage(Component.text("Claim cost previews are not available yet.", NamedTextColor.RED));
            return true;
        }

        Optional<Set<ClaimChunk>> pendingSelection = selectionService.pendingSelection(player.getUniqueId());
        if (pendingSelection.isEmpty()) {
            player.sendMessage(Component.text("Select two chunks with the claim tool first.", NamedTextColor.RED));
            return true;
        }

        ClaimCostQuote quote = claimCostService.quotePlayerClaim(
                player.getUniqueId(),
                permissionNodes(player),
                pendingSelection.orElseThrow()
        );
        ClaimCostMessageService.preview(quote, claimPaymentService.format(quote.cost())).forEach(player::sendMessage);
        return true;
    }

    private boolean giveTool(Player player) {
        if (!player.hasPermission(CLAIM_TOOL_PERMISSION)) {
            player.sendMessage(Component.text("You do not have permission to use the claim tool.", NamedTextColor.RED));
            return true;
        }

        player.getInventory().addItem(claimToolService.createClaimTool());
        player.sendMessage(Component.text("Claim tool added to your inventory.", NamedTextColor.GREEN));
        return true;
    }

    private boolean createClaim(Player player, String[] args) {
        if (!isClaimCreationAvailable(player)) {
            return true;
        }

        Optional<Set<ClaimChunk>> pendingSelection = selectionService.pendingSelection(player.getUniqueId());
        if (pendingSelection.isEmpty()) {
            player.sendMessage(Component.text("Select two chunks with the claim tool first.", NamedTextColor.RED));
            return true;
        }

        Set<ClaimChunk> chunks = pendingSelection.orElseThrow();
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (!claimToolService.isClaimTool(mainHandItem)) {
            player.sendMessage(Component.text("Hold your claim tool to create a claim.", NamedTextColor.RED));
            return true;
        }
        if (claimToolService.currentCharges(mainHandItem) < chunks.size()) {
            player.sendMessage(Component.text("Your claim tool does not have enough charges for that selection.", NamedTextColor.RED));
            return true;
        }

        String claimName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        ClaimValidationResult validationResult = claimCreationService.validatePlayerClaim(player.getUniqueId(), claimName, chunks);
        if (!validationResult.isAllowed()) {
            player.sendMessage(Component.text("Claim could not be created: ", NamedTextColor.RED)
                    .append(Component.text(validationResult.messageKey().orElse("claims.denied"), NamedTextColor.YELLOW)));
            return true;
        }

        if (claimCostService != null && claimPaymentService != null) {
            ClaimCostQuote quote = claimCostService.quotePlayerClaim(player.getUniqueId(), permissionNodes(player), chunks);
            ClaimPaymentResult paymentResult = claimPaymentService.charge(player.getUniqueId(), quote);
            if (!paymentResult.allowed()) {
                player.sendMessage(Component.text("Claim could not be created: ", NamedTextColor.RED)
                        .append(Component.text(paymentResult.messageKey(), NamedTextColor.YELLOW)));
                return true;
            }
            if (quote.cost() > 0.0) {
                player.sendMessage(Component.text("Charged ", NamedTextColor.GREEN)
                        .append(Component.text(quote.cost(), NamedTextColor.YELLOW))
                        .append(Component.text(" for over-limit claim chunks.", NamedTextColor.GREEN)));
            }
        }

        ClaimValidationResult result = claimCreationService.createPlayerClaim(player.getUniqueId(), claimName, chunks);
        if (!result.isAllowed()) {
            player.sendMessage(Component.text("Claim could not be created: ", NamedTextColor.RED)
                    .append(Component.text(result.messageKey().orElse("claims.denied"), NamedTextColor.YELLOW)));
            return true;
        }

        claimToolService.spendCharges(mainHandItem, chunks.size());
        selectionService.consumeSelection(player.getUniqueId());
        player.sendMessage(Component.text("Claim created: ", NamedTextColor.GREEN)
                .append(Component.text(claimName.trim(), NamedTextColor.YELLOW)));
        return true;
    }

    private Set<String> permissionNodes(Player player) {
        return player.getEffectivePermissions().stream()
                .filter(PermissionAttachmentInfo::getValue)
                .map(PermissionAttachmentInfo::getPermission)
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean cancelSelection(Player player) {
        if (selectionService == null) {
            player.sendMessage(Component.text("Claim selection is not available yet.", NamedTextColor.RED));
            return true;
        }

        selectionService.clear(player);
        player.sendMessage(Component.text("Claim selection cleared.", NamedTextColor.YELLOW));
        return true;
    }

    private boolean showInfo(Player player) {
        if (claimIndex == null) {
            player.sendMessage(Component.text("Claim info is not available yet.", NamedTextColor.RED));
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();
        ClaimChunk claimChunk = new ClaimChunk(player.getWorld().getUID(), chunk.getX(), chunk.getZ());
        Optional<Claim> claim = claimIndex.findAt(claimChunk);
        if (claim.isEmpty()) {
            player.sendMessage(Component.text("This chunk is not claimed.", NamedTextColor.YELLOW));
            return true;
        }

        Claim foundClaim = claim.orElseThrow();
        player.sendMessage(Component.text("Claim: ", NamedTextColor.GOLD)
                .append(Component.text(foundClaim.name(), NamedTextColor.YELLOW)));
        player.sendMessage(Component.text("Owner type: ", NamedTextColor.GRAY)
                .append(Component.text(foundClaim.owner().name(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Chunks: ", NamedTextColor.GRAY)
                .append(Component.text(foundClaim.claimChunks().size(), NamedTextColor.WHITE)));
        player.sendMessage(Component.text("You are owner: ", NamedTextColor.GRAY)
                .append(Component.text(player.getUniqueId().equals(foundClaim.ownerUuid()), NamedTextColor.WHITE)));
        return true;
    }

    private boolean isClaimCreationAvailable(Player player) {
        if (selectionService == null || claimCreationService == null || claimIndex == null) {
            player.sendMessage(Component.text("Claim creation is not available yet.", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("LandClaims commands", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/claims tool", NamedTextColor.YELLOW)
                .append(Component.text(" - gives you the configured claiming tool.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/claims create <name>", NamedTextColor.YELLOW)
                .append(Component.text(" - creates a claim from your pending selection.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/claims cost", NamedTextColor.YELLOW)
                .append(Component.text(" - previews claim allowance and over-limit cost.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/claims cancel", NamedTextColor.YELLOW)
                .append(Component.text(" - clears your pending selection.", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/claims info", NamedTextColor.YELLOW)
                .append(Component.text(" - shows the claim at your current chunk.", NamedTextColor.GRAY)));
    }
}
