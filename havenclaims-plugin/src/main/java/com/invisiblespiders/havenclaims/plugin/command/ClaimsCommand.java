package com.invisiblespiders.havenclaims.plugin.command;

import com.invisiblespiders.havenclaims.plugin.admin.AdminClaimResult;
import com.invisiblespiders.havenclaims.plugin.admin.AdminClaimService;
import com.invisiblespiders.havenclaims.plugin.claim.Claim;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimChunk;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimCreationService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimDenyResult;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimDenyService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimIndex;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimMember;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimMemberResult;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimMemberService;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimRole;
import com.invisiblespiders.havenclaims.plugin.claim.ClaimValidationResult;
import com.invisiblespiders.havenclaims.plugin.claim.PendingClaimMerge;
import com.invisiblespiders.havenclaims.plugin.claim.PendingClaimMergeService;
import com.invisiblespiders.havenclaims.plugin.economy.ClaimPaymentResult;
import com.invisiblespiders.havenclaims.plugin.economy.ClaimPaymentService;
import com.invisiblespiders.havenclaims.plugin.flag.ClaimFlagResult;
import com.invisiblespiders.havenclaims.plugin.flag.ClaimFlagRow;
import com.invisiblespiders.havenclaims.plugin.flag.ClaimFlagService;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimCostMessageService;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimCostQuote;
import com.invisiblespiders.havenclaims.plugin.limit.ClaimCostService;
import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import com.invisiblespiders.havenclaims.plugin.selection.SelectionService;
import com.invisiblespiders.havenclaims.plugin.tool.ClaimToolService;
import com.invisiblespiders.havenclaims.plugin.ui.ClaimFlagEditor;
import com.invisiblespiders.havenclaims.plugin.ui.ClaimFlagEditorService;
import com.invisiblespiders.havenclaims.plugin.ui.ClaimMenu;
import com.invisiblespiders.havenclaims.plugin.ui.ClaimMenuService;
import com.invisiblespiders.havenclaims.plugin.ui.DialogService;
import com.invisiblespiders.havenclaims.plugin.ui.InventoryGuiFallbackService;
import com.invisiblespiders.havenclaims.plugin.visual.BorderColor;
import com.invisiblespiders.havenclaims.plugin.visual.ClaimBorderColorService;
import com.invisiblespiders.havenclaims.plugin.visual.ChunkBorderVisualService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;

public class ClaimsCommand implements CommandExecutor, TabCompleter {
    private static final String CLAIM_TOOL_PERMISSION = "havenclaims.tool.use";
    private static final String CLAIM_MENU_PERMISSION = "havenclaims.gui";
    private static final String CLAIM_DENY_PERMISSION = "havenclaims.deny.manage";
    private static final List<String> ROOT_SUGGESTIONS = List.of(
            "tool",
            "create",
            "cost",
            "quote",
            "menu",
            "flags",
            "viewborder",
            "flag",
            "member",
            "deny",
            "undeny",
            "denied",
            "admin",
            "cancel",
            "info"
    );
    private static final List<String> ADMIN_SUGGESTIONS = List.of("create", "list", "delete", "teleport", "userclaims");
    private static final List<String> ADMIN_USERCLAIMS_SUGGESTIONS = List.of("list", "view", "delete", "teleport", "transfer", "flag", "member");
    private static final List<String> ADMIN_USERCLAIM_FLAG_SUGGESTIONS = List.of("list", "set", "toggle");
    private static final List<String> ADMIN_USERCLAIM_MEMBER_SUGGESTIONS = List.of("list", "add", "remove");

    private final ClaimToolService claimToolService;
    private final SelectionService selectionService;
    private final ClaimCreationService claimCreationService;
    private final ClaimIndex claimIndex;
    private final ClaimCostService claimCostService;
    private final ClaimPaymentService claimPaymentService;
    private final PendingClaimMergeService pendingClaimMergeService;
    private final MessageService messageService;
    private final ClaimMemberService claimMemberService;
    private final ClaimDenyService claimDenyService;
    private final ClaimFlagService claimFlagService;
    private final ClaimFlagEditorService claimFlagEditorService;
    private final ClaimMenuService claimMenuService;
    private final DialogService dialogService;
    private final InventoryGuiFallbackService inventoryGuiFallbackService;
    private final ChunkBorderVisualService chunkBorderVisualService;
    private final ClaimBorderColorService claimBorderColorService;
    private final AdminClaimService adminClaimService;

    public ClaimsCommand(ClaimToolService claimToolService) {
        this(claimToolService, null, null, null, null, null, null, new MessageService(Map.of()), null, null, null, null, null, null, null, null, null, null);
    }

    public ClaimsCommand(
            ClaimToolService claimToolService,
            SelectionService selectionService,
            ClaimCreationService claimCreationService,
            ClaimIndex claimIndex,
            ClaimCostService claimCostService,
            ClaimPaymentService claimPaymentService,
            PendingClaimMergeService pendingClaimMergeService,
            MessageService messageService,
            ClaimMemberService claimMemberService,
            ClaimDenyService claimDenyService,
            ClaimFlagService claimFlagService,
            ClaimFlagEditorService claimFlagEditorService,
            ClaimMenuService claimMenuService,
            DialogService dialogService,
            InventoryGuiFallbackService inventoryGuiFallbackService,
            ChunkBorderVisualService chunkBorderVisualService,
            ClaimBorderColorService claimBorderColorService,
            AdminClaimService adminClaimService
    ) {
        this.claimToolService = Objects.requireNonNull(claimToolService, "claimToolService");
        this.selectionService = selectionService;
        this.claimCreationService = claimCreationService;
        this.claimIndex = claimIndex;
        this.claimCostService = claimCostService;
        this.claimPaymentService = claimPaymentService;
        this.pendingClaimMergeService = pendingClaimMergeService;
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.claimMemberService = claimMemberService;
        this.claimDenyService = claimDenyService;
        this.claimFlagService = claimFlagService;
        this.claimFlagEditorService = claimFlagEditorService;
        this.claimMenuService = claimMenuService;
        this.dialogService = dialogService;
        this.inventoryGuiFallbackService = inventoryGuiFallbackService;
        this.chunkBorderVisualService = chunkBorderVisualService;
        this.claimBorderColorService = claimBorderColorService;
        this.adminClaimService = adminClaimService;
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
        if (args.length == 1 && args[0].equalsIgnoreCase("menu")) {
            return openClaimMenu(player);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("flags")) {
            return openFlagEditor(player);
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("viewborder")) {
            return viewBorder(player);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("create")) {
            return createClaim(player, args);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("member")) {
            return manageMembers(player, args);
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("deny")
                || args[0].equalsIgnoreCase("undeny")
                || args[0].equalsIgnoreCase("denied"))) {
            return manageDeniedPlayers(player, args);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("flag")) {
            return manageFlags(player, args);
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            return manageAdminClaims(player, args);
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return ROOT_SUGGESTIONS;
        }
        if (args.length == 1) {
            return matching(ROOT_SUGGESTIONS, args[0]);
        }

        String subcommand = args[0].toLowerCase();
        if (args.length == 2 && subcommand.equals("member")) {
            return matching(List.of("add", "remove", "list"), args[1]);
        }
        if (args.length == 2 && (subcommand.equals("deny") || subcommand.equals("undeny"))) {
            return null;
        }
        if (args.length == 2 && subcommand.equals("flag")) {
            return matching(List.of("list", "set", "toggle"), args[1]);
        }
        if (args.length == 2 && subcommand.equals("admin")) {
            return matching(ADMIN_SUGGESTIONS, args[1]);
        }
        if (args.length == 3 && subcommand.equals("admin") && args[1].equalsIgnoreCase("userclaims")) {
            return matching(ADMIN_USERCLAIMS_SUGGESTIONS, args[2]);
        }
        if (args.length == 4
                && subcommand.equals("admin")
                && args[1].equalsIgnoreCase("userclaims")
                && args[2].equalsIgnoreCase("flag")) {
            return matching(ADMIN_USERCLAIM_FLAG_SUGGESTIONS, args[3]);
        }
        if (args.length == 4
                && subcommand.equals("admin")
                && args[1].equalsIgnoreCase("userclaims")
                && args[2].equalsIgnoreCase("member")) {
            return matching(ADMIN_USERCLAIM_MEMBER_SUGGESTIONS, args[3]);
        }
        if (args.length == 7
                && subcommand.equals("admin")
                && args[1].equalsIgnoreCase("userclaims")
                && args[2].equalsIgnoreCase("flag")
                && args[3].equalsIgnoreCase("set")) {
            return matching(List.of("true", "false", "on", "off", "yes", "no"), args[6]);
        }
        if (args.length == 7
                && subcommand.equals("admin")
                && args[1].equalsIgnoreCase("userclaims")
                && args[2].equalsIgnoreCase("member")
                && args[3].equalsIgnoreCase("add")) {
            return matching(Arrays.stream(ClaimRole.values())
                    .map(role -> role.name().toLowerCase())
                    .toList(), args[6]);
        }
        if (args.length == 4 && subcommand.equals("flag") && args[1].equalsIgnoreCase("set")) {
            return matching(List.of("true", "false", "on", "off", "yes", "no"), args[3]);
        }
        if (args.length == 4 && subcommand.equals("member") && args[1].equalsIgnoreCase("add")) {
            return matching(Arrays.stream(ClaimRole.values())
                    .map(role -> role.name().toLowerCase())
                    .toList(), args[3]);
        }
        return List.of();
    }

    private boolean manageAdminClaims(Player player, String[] args) {
        if (adminClaimService == null) {
            player.sendMessage(message("admin.claim.unavailable"));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(message("admin.claim.usage"));
            return true;
        }

        String action = args[1].toLowerCase();
        if (action.equals("create")) {
            return createAdminClaim(player, args);
        }
        if (action.equals("list")) {
            return listAdminClaims(player);
        }
        if (action.equals("delete")) {
            return deleteAdminClaim(player, args);
        }
        if (action.equals("teleport")) {
            return teleportToAdminClaim(player, args);
        }
        if (action.equals("userclaims")) {
            return manageAdminUserClaims(player, args);
        }

        player.sendMessage(message("admin.claim.usage"));
        return true;
    }

    private boolean manageAdminUserClaims(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(message("admin.userclaims.usage"));
            return true;
        }

        String action = args[2].toLowerCase();
        if (action.equals("list")) {
            return listPlayerClaims(player, args);
        }
        if (action.equals("view")) {
            return viewPlayerClaim(player, args);
        }
        if (action.equals("delete")) {
            return deletePlayerClaim(player, args);
        }
        if (action.equals("teleport")) {
            return teleportToPlayerClaim(player, args);
        }
        if (action.equals("transfer")) {
            return transferPlayerClaim(player, args);
        }
        if (action.equals("flag")) {
            return manageAdminUserClaimFlags(player, args);
        }
        if (action.equals("member")) {
            return manageAdminUserClaimMembers(player, args);
        }

        player.sendMessage(message("admin.userclaims.usage"));
        return true;
    }

    private boolean listPlayerClaims(Player player, String[] args) {
        if (!player.hasPermission("havenclaims.admin.userclaims.view")) {
            player.sendMessage(message("admin.userclaims.no-permission"));
            return true;
        }
        if (args.length != 4) {
            player.sendMessage(message("admin.userclaims.list-usage"));
            return true;
        }

        OfflinePlayer target = resolveOfflinePlayer(player, args[3]);
        List<Claim> claims = adminClaimService.listPlayerClaims(target.getUniqueId());
        if (claims.isEmpty()) {
            player.sendMessage(message("admin.userclaims.list-empty", Map.of("player", memberName(target))));
            return true;
        }

        player.sendMessage(message("admin.userclaims.list-header", Map.of("player", memberName(target))));
        claims.forEach(claim -> player.sendMessage(message("admin.userclaims.list-entry", Map.of(
                "claim_name", claim.name(),
                "claim_id", claim.id().toString(),
                "chunk_count", String.valueOf(claim.claimChunks().size())
        ))));
        return true;
    }

    private boolean viewPlayerClaim(Player player, String[] args) {
        if (!player.hasPermission("havenclaims.admin.userclaims.view")) {
            player.sendMessage(message("admin.userclaims.no-permission"));
            return true;
        }
        Optional<Claim> claim = playerClaimFromArgs(player, args, "admin.userclaims.view-usage");
        if (claim.isEmpty()) {
            return true;
        }
        Claim foundClaim = claim.orElseThrow();
        player.sendMessage(message("admin.userclaims.view-name", Map.of(
                "claim_name", foundClaim.name(),
                "claim_id", foundClaim.id().toString()
        )));
        player.sendMessage(message("admin.userclaims.view-owner", Map.of(
                "owner", foundClaim.ownerUuid() == null ? "unknown" : playerName(player, foundClaim.ownerUuid())
        )));
        player.sendMessage(message("admin.userclaims.view-chunks", Map.of(
                "chunk_count", String.valueOf(foundClaim.claimChunks().size())
        )));
        return true;
    }

    private boolean deletePlayerClaim(Player player, String[] args) {
        if (!player.hasPermission("havenclaims.admin.userclaims.delete")) {
            player.sendMessage(message("admin.userclaims.no-permission"));
            return true;
        }
        if (args.length != 4) {
            player.sendMessage(message("admin.userclaims.delete-usage"));
            return true;
        }
        Optional<UUID> claimId = parseUuid(args[3]);
        if (claimId.isEmpty()) {
            player.sendMessage(message("admin.claim.invalid-id"));
            return true;
        }

        AdminClaimResult result = adminClaimService.deletePlayerClaim(claimId.orElseThrow());
        if (!result.allowed()) {
            player.sendMessage(message(result.messageKey()));
            return true;
        }
        player.sendMessage(message("admin.userclaims.deleted", Map.of(
                "claim_name", result.claim().name(),
                "claim_id", result.claim().id().toString()
        )));
        return true;
    }

    private boolean teleportToPlayerClaim(Player player, String[] args) {
        if (!player.hasPermission("havenclaims.admin.userclaims.teleport")) {
            player.sendMessage(message("admin.userclaims.no-permission"));
            return true;
        }
        Optional<Claim> claim = playerClaimFromArgs(player, args, "admin.userclaims.teleport-usage");
        if (claim.isEmpty()) {
            return true;
        }

        Optional<Location> target = teleportTarget(player, claim.orElseThrow());
        if (target.isEmpty()) {
            player.sendMessage(message("admin.claim.world-not-found"));
            return true;
        }

        player.teleport(target.orElseThrow());
        player.sendMessage(message("admin.userclaims.teleported", Map.of("claim_name", claim.orElseThrow().name())));
        return true;
    }

    private boolean transferPlayerClaim(Player player, String[] args) {
        if (!player.hasPermission("havenclaims.admin.userclaims.transfer")) {
            player.sendMessage(message("admin.userclaims.no-permission"));
            return true;
        }
        if (args.length != 5) {
            player.sendMessage(message("admin.userclaims.transfer-usage"));
            return true;
        }
        Optional<UUID> claimId = parseUuid(args[3]);
        if (claimId.isEmpty()) {
            player.sendMessage(message("admin.claim.invalid-id"));
            return true;
        }
        Optional<UUID> newOwnerId = resolveOnlinePlayerOrUuid(player, args[4]);
        if (newOwnerId.isEmpty()) {
            player.sendMessage(message("admin.userclaims.transfer-player-not-found", Map.of("player", args[4])));
            return true;
        }

        AdminClaimResult result = adminClaimService.transferPlayerClaim(claimId.orElseThrow(), newOwnerId.orElseThrow());
        if (!result.allowed()) {
            player.sendMessage(message(result.messageKey()));
            return true;
        }
        player.sendMessage(message("admin.userclaims.transferred", Map.of(
                "claim_name", result.claim().name(),
                "claim_id", result.claim().id().toString(),
                "player", transferTargetName(player, args[4], newOwnerId.orElseThrow())
        )));
        return true;
    }

    private boolean manageAdminUserClaimFlags(Player player, String[] args) {
        if (!player.hasPermission("havenclaims.admin.userclaims.edit")) {
            player.sendMessage(message("admin.userclaims.edit-no-permission"));
            return true;
        }
        if (claimFlagService == null) {
            player.sendMessage(message("command.unavailable.claim-info"));
            return true;
        }
        if (args.length < 5) {
            player.sendMessage(message("admin.userclaims.flag-usage"));
            return true;
        }

        String action = args[3].toLowerCase();
        if (action.equals("list")) {
            return listPlayerClaimFlags(player, args);
        }
        if (action.equals("set")) {
            return setPlayerClaimFlag(player, args);
        }
        if (action.equals("toggle")) {
            return togglePlayerClaimFlag(player, args);
        }

        player.sendMessage(message("admin.userclaims.flag-usage"));
        return true;
    }

    private boolean listPlayerClaimFlags(Player player, String[] args) {
        Optional<Claim> claim = playerClaimFromArgs(player, args, "admin.userclaims.flag-list-usage", 4);
        if (claim.isEmpty()) {
            return true;
        }
        Claim foundClaim = claim.orElseThrow();
        player.sendMessage(message("admin.userclaims.flag-list-header", Map.of(
                "claim_name", foundClaim.name(),
                "claim_id", foundClaim.id().toString()
        )));
        for (ClaimFlagRow row : claimFlagService.listFlags(foundClaim)) {
            player.sendMessage(message("admin.userclaims.flag-list-entry", Map.of(
                    "flag", row.key(),
                    "label", row.label(),
                    "category", row.category(),
                    "description", row.description(),
                    "value", String.valueOf(row.enabled())
            )));
        }
        return true;
    }

    private boolean setPlayerClaimFlag(Player player, String[] args) {
        if (args.length != 7) {
            player.sendMessage(message("admin.userclaims.flag-set-usage"));
            return true;
        }
        Optional<Boolean> enabled = parseBoolean(args[6]);
        if (enabled.isEmpty()) {
            player.sendMessage(message("claim.flag.invalid-value"));
            return true;
        }
        Optional<Claim> claim = playerClaimFromArgs(player, args, "admin.userclaims.flag-set-usage", 4);
        if (claim.isEmpty()) {
            return true;
        }

        Claim foundClaim = claim.orElseThrow();
        ClaimFlagResult result = claimFlagService.setFlag(
                foundClaim.ownerUuid(),
                foundClaim,
                args[5],
                enabled.orElseThrow(),
                permission -> true
        );
        if (!result.allowed()) {
            player.sendMessage(message(result.messageKey(), Map.of("flag", args[5])));
            return true;
        }
        player.sendMessage(message("admin.userclaims.flag-set", Map.of(
                "claim_name", foundClaim.name(),
                "claim_id", foundClaim.id().toString(),
                "flag", args[5],
                "value", String.valueOf(enabled.orElseThrow())
        )));
        return true;
    }

    private boolean togglePlayerClaimFlag(Player player, String[] args) {
        if (args.length != 6) {
            player.sendMessage(message("admin.userclaims.flag-toggle-usage"));
            return true;
        }
        Optional<Claim> claim = playerClaimFromArgs(player, args, "admin.userclaims.flag-toggle-usage", 4);
        if (claim.isEmpty()) {
            return true;
        }

        Claim foundClaim = claim.orElseThrow();
        ClaimFlagResult result = claimFlagService.toggleFlag(
                foundClaim.ownerUuid(),
                foundClaim,
                args[5],
                permission -> true
        );
        if (!result.allowed()) {
            player.sendMessage(message(result.messageKey(), Map.of("flag", args[5])));
            return true;
        }
        player.sendMessage(message("admin.userclaims.flag-toggled", Map.of(
                "claim_name", foundClaim.name(),
                "claim_id", foundClaim.id().toString(),
                "flag", args[5]
        )));
        return true;
    }

    private boolean manageAdminUserClaimMembers(Player player, String[] args) {
        if (!player.hasPermission("havenclaims.admin.userclaims.edit")) {
            player.sendMessage(message("admin.userclaims.edit-no-permission"));
            return true;
        }
        if (claimMemberService == null) {
            player.sendMessage(message("command.unavailable.claim-info"));
            return true;
        }
        if (args.length < 5) {
            player.sendMessage(message("admin.userclaims.member-usage"));
            return true;
        }

        String action = args[3].toLowerCase();
        if (action.equals("list")) {
            return listPlayerClaimMembers(player, args);
        }
        if (action.equals("add")) {
            return addPlayerClaimMember(player, args);
        }
        if (action.equals("remove")) {
            return removePlayerClaimMember(player, args);
        }

        player.sendMessage(message("admin.userclaims.member-usage"));
        return true;
    }

    private boolean listPlayerClaimMembers(Player player, String[] args) {
        Optional<Claim> claim = playerClaimFromArgs(player, args, "admin.userclaims.member-list-usage", 4);
        if (claim.isEmpty()) {
            return true;
        }

        Claim foundClaim = claim.orElseThrow();
        if (foundClaim.members().isEmpty()) {
            player.sendMessage(message("admin.userclaims.member-list-empty", Map.of(
                    "claim_name", foundClaim.name(),
                    "claim_id", foundClaim.id().toString()
            )));
            return true;
        }

        player.sendMessage(message("admin.userclaims.member-list-header", Map.of(
                "claim_name", foundClaim.name(),
                "claim_id", foundClaim.id().toString()
        )));
        foundClaim.members().stream()
                .sorted(java.util.Comparator.comparing(member -> member.memberUuid().toString()))
                .forEach(member -> player.sendMessage(message("admin.userclaims.member-list-entry", Map.of(
                        "player", playerName(player, member.memberUuid()),
                        "role", member.role().name().toLowerCase()
                ))));
        return true;
    }

    private boolean addPlayerClaimMember(Player player, String[] args) {
        if (args.length != 6 && args.length != 7) {
            player.sendMessage(message("admin.userclaims.member-add-usage"));
            return true;
        }
        Optional<Claim> claim = playerClaimFromArgs(player, args, "admin.userclaims.member-add-usage", 4);
        if (claim.isEmpty()) {
            return true;
        }
        Optional<UUID> targetId = resolveOnlinePlayerOrUuid(player, args[5]);
        if (targetId.isEmpty()) {
            player.sendMessage(message("admin.userclaims.member-player-not-found", Map.of("player", args[5])));
            return true;
        }
        ClaimRole role = args.length == 7 ? parseRole(args[6]).orElse(null) : ClaimRole.MEMBER;
        if (role == null) {
            player.sendMessage(message("claim.member.invalid-role"));
            return true;
        }

        Claim foundClaim = claim.orElseThrow();
        ClaimMemberResult result = claimMemberService.addMember(
                foundClaim.ownerUuid(),
                foundClaim,
                targetId.orElseThrow(),
                role
        );
        if (!result.allowed()) {
            player.sendMessage(message(result.messageKey()));
            return true;
        }
        player.sendMessage(message("admin.userclaims.member-added", Map.of(
                "claim_name", foundClaim.name(),
                "claim_id", foundClaim.id().toString(),
                "player", adminTargetName(player, args[5], targetId.orElseThrow()),
                "role", role.name().toLowerCase()
        )));
        return true;
    }

    private boolean removePlayerClaimMember(Player player, String[] args) {
        if (args.length != 6) {
            player.sendMessage(message("admin.userclaims.member-remove-usage"));
            return true;
        }
        Optional<Claim> claim = playerClaimFromArgs(player, args, "admin.userclaims.member-remove-usage", 4);
        if (claim.isEmpty()) {
            return true;
        }
        Claim foundClaim = claim.orElseThrow();
        Optional<ClaimMember> member = findExistingMember(foundClaim, player, args[5]);
        if (member.isEmpty()) {
            player.sendMessage(message("admin.userclaims.member-not-found", Map.of(
                    "claim_name", foundClaim.name(),
                    "claim_id", foundClaim.id().toString(),
                    "player", args[5]
            )));
            return true;
        }

        ClaimMemberResult result = claimMemberService.removeMember(
                foundClaim.ownerUuid(),
                foundClaim,
                member.orElseThrow().memberUuid()
        );
        if (!result.allowed()) {
            player.sendMessage(message(result.messageKey()));
            return true;
        }
        player.sendMessage(message("admin.userclaims.member-removed", Map.of(
                "claim_name", foundClaim.name(),
                "claim_id", foundClaim.id().toString(),
                "player", adminTargetName(player, args[5], member.orElseThrow().memberUuid())
        )));
        return true;
    }

    private Optional<Claim> playerClaimFromArgs(Player player, String[] args, String usageMessageKey) {
        if (args.length != 4) {
            player.sendMessage(message(usageMessageKey));
            return Optional.empty();
        }
        return playerClaimFromArgs(player, args, usageMessageKey, 3);
    }

    private Optional<Claim> playerClaimFromArgs(Player player, String[] args, String usageMessageKey, int claimIdIndex) {
        if (args.length <= claimIdIndex) {
            player.sendMessage(message(usageMessageKey));
            return Optional.empty();
        }
        Optional<UUID> claimId = parseUuid(args[claimIdIndex]);
        if (claimId.isEmpty()) {
            player.sendMessage(message("admin.claim.invalid-id"));
            return Optional.empty();
        }
        Optional<Claim> claim = adminClaimService.findPlayerClaim(claimId.orElseThrow());
        if (claim.isEmpty()) {
            player.sendMessage(message("admin.userclaims.not-found"));
        }
        return claim;
    }

    private OfflinePlayer resolveOfflinePlayer(Player actor, String input) {
        Optional<UUID> uuid = parseUuid(input);
        if (uuid.isPresent()) {
            return actor.getServer().getOfflinePlayer(uuid.orElseThrow());
        }
        Player onlinePlayer = actor.getServer().getPlayerExact(input);
        return onlinePlayer == null ? actor.getServer().getOfflinePlayer(input) : onlinePlayer;
    }

    private boolean createAdminClaim(Player player, String[] args) {
        if (!player.hasPermission("havenclaims.admin.claim.create")) {
            player.sendMessage(message("admin.claim.no-permission"));
            return true;
        }
        if (selectionService == null) {
            player.sendMessage(message("command.unavailable.claim-creation"));
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(message("admin.claim.create-usage"));
            return true;
        }

        Optional<Set<ClaimChunk>> pendingSelection = selectionService.pendingSelection(player.getUniqueId());
        if (pendingSelection.isEmpty()) {
            player.sendMessage(message("claim.selection-required"));
            return true;
        }

        String claimName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        Set<ClaimChunk> chunks = pendingSelection.orElseThrow();
        AdminClaimResult result = adminClaimService.createAdminClaim(claimName, chunks);
        if (!result.allowed()) {
            showBorder(player, chunks, BorderColor.RED);
            player.sendMessage(message(result.messageKey()));
            return true;
        }

        selectionService.consumeSelection(player.getUniqueId());
        if (chunkBorderVisualService != null) {
            chunkBorderVisualService.showSelection(player, chunks, BorderColor.GOLD);
        }
        player.sendMessage(message("admin.claim.created", Map.of(
                "claim_name", result.claim().name(),
                "claim_id", result.claim().id().toString(),
                "chunk_count", String.valueOf(result.claim().claimChunks().size())
        )));
        return true;
    }

    private boolean listAdminClaims(Player player) {
        if (!player.hasPermission("havenclaims.admin.claim.list")) {
            player.sendMessage(message("admin.claim.no-permission"));
            return true;
        }

        List<Claim> adminClaims = adminClaimService.listAdminClaims();
        if (adminClaims.isEmpty()) {
            player.sendMessage(message("admin.claim.list-empty"));
            return true;
        }

        player.sendMessage(message("admin.claim.list-header"));
        adminClaims.forEach(claim -> player.sendMessage(message("admin.claim.list-entry", Map.of(
                "claim_name", claim.name(),
                "claim_id", claim.id().toString(),
                "chunk_count", String.valueOf(claim.claimChunks().size())
        ))));
        return true;
    }

    private boolean deleteAdminClaim(Player player, String[] args) {
        if (!player.hasPermission("havenclaims.admin.claim.delete")) {
            player.sendMessage(message("admin.claim.no-permission"));
            return true;
        }
        if (args.length != 3) {
            player.sendMessage(message("admin.claim.delete-usage"));
            return true;
        }

        Optional<UUID> claimId = parseUuid(args[2]);
        if (claimId.isEmpty()) {
            player.sendMessage(message("admin.claim.invalid-id"));
            return true;
        }

        AdminClaimResult result = adminClaimService.deleteAdminClaim(claimId.orElseThrow());
        if (!result.allowed()) {
            player.sendMessage(message(result.messageKey()));
            return true;
        }
        player.sendMessage(message("admin.claim.deleted", Map.of(
                "claim_name", result.claim().name(),
                "claim_id", result.claim().id().toString()
        )));
        return true;
    }

    private boolean teleportToAdminClaim(Player player, String[] args) {
        if (!player.hasPermission("havenclaims.admin.claim.teleport")) {
            player.sendMessage(message("admin.claim.no-permission"));
            return true;
        }
        if (args.length != 3) {
            player.sendMessage(message("admin.claim.teleport-usage"));
            return true;
        }

        Optional<UUID> claimId = parseUuid(args[2]);
        if (claimId.isEmpty()) {
            player.sendMessage(message("admin.claim.invalid-id"));
            return true;
        }
        Optional<Claim> claim = adminClaimService.findAdminClaim(claimId.orElseThrow());
        if (claim.isEmpty()) {
            player.sendMessage(message("admin.claim.not-found"));
            return true;
        }

        Optional<Location> target = teleportTarget(player, claim.orElseThrow());
        if (target.isEmpty()) {
            player.sendMessage(message("admin.claim.world-not-found"));
            return true;
        }

        player.teleport(target.orElseThrow());
        player.sendMessage(message("admin.teleported", Map.of("claim_name", claim.orElseThrow().name())));
        return true;
    }

    private Optional<Location> teleportTarget(Player player, Claim claim) {
        return claim.claimChunks().stream()
                .sorted(java.util.Comparator
                        .comparingInt(ClaimChunk::chunkX)
                        .thenComparingInt(ClaimChunk::chunkZ))
                .findFirst()
                .flatMap(chunk -> {
                    World world = player.getServer().getWorld(chunk.worldId());
                    if (world == null) {
                        return Optional.empty();
                    }
                    int x = (chunk.chunkX() << 4) + 8;
                    int z = (chunk.chunkZ() << 4) + 8;
                    int y = world.getHighestBlockYAt(x, z) + 1;
                    return Optional.of(new Location(world, x + 0.5D, y, z + 0.5D));
                });
    }

    private List<String> matching(List<String> options, String prefix) {
        String normalizedPrefix = prefix.toLowerCase();
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(normalizedPrefix))
                .toList();
    }

    private boolean viewBorder(Player player) {
        if (claimIndex == null || chunkBorderVisualService == null) {
            player.sendMessage(message("command.unavailable.claim-info"));
            return true;
        }

        Optional<Set<ClaimChunk>> pendingSelection = selectionService == null
                ? Optional.empty()
                : selectionService.pendingSelection(player.getUniqueId());
        if (pendingSelection.isPresent()) {
            BorderColor color = previewColor(player, pendingSelection.orElseThrow(), Optional.empty());
            chunkBorderVisualService.showSelection(player, pendingSelection.orElseThrow(), color);
            player.sendMessage(message("claim.visual.border-selection", Map.of("color", color.messageName())));
            return true;
        }

        Optional<Claim> claim = claimAtPlayer(player);
        if (claim.isPresent()) {
            chunkBorderVisualService.showSelection(player, claim.orElseThrow().claimChunks(), BorderColor.GOLD);
            player.sendMessage(message("claim.visual.border-claim"));
            return true;
        }

        Chunk chunk = player.getLocation().getChunk();
        Set<ClaimChunk> currentChunk = Set.of(new ClaimChunk(player.getWorld().getUID(), chunk.getX(), chunk.getZ()));
        BorderColor color = previewColor(player, currentChunk, Optional.empty());
        chunkBorderVisualService.showSelection(
                player,
                currentChunk,
                color
        );
        player.sendMessage(message("claim.visual.border-current-chunk", Map.of("color", color.messageName())));
        return true;
    }

    private boolean openFlagEditor(Player player) {
        if (claimFlagService == null || claimFlagEditorService == null || claimIndex == null || dialogService == null) {
            player.sendMessage(message("command.unavailable.claim-info"));
            return true;
        }
        if (!player.hasPermission(CLAIM_MENU_PERMISSION)) {
            player.sendMessage(message("claim.menu.no-permission"));
            return true;
        }

        Optional<Claim> claim = claimAtPlayer(player);
        if (claim.isEmpty()) {
            player.sendMessage(message("claim.info.unclaimed"));
            return true;
        }

        Claim foundClaim = claim.orElseThrow();
        ClaimFlagEditor editor = claimFlagEditorService.buildEditor(foundClaim.name(), claimFlagService.listFlags(foundClaim));
        dialogService.openFlagEditor(player, editor, messageService);
        return true;
    }

    private boolean openClaimMenu(Player player) {
        if (claimMenuService == null || claimIndex == null || dialogService == null || inventoryGuiFallbackService == null) {
            player.sendMessage(message("command.unavailable.claim-info"));
            return true;
        }
        if (!player.hasPermission(CLAIM_MENU_PERMISSION)) {
            player.sendMessage(message("claim.menu.no-permission"));
            return true;
        }

        Optional<Claim> claim = claimAtPlayer(player);
        if (claim.isEmpty()) {
            player.sendMessage(message("claim.info.unclaimed"));
            return true;
        }

        ClaimMenu menu = claimMenuService.buildMenu(claim.orElseThrow(), player.getUniqueId());
        dialogService.openClaimMenu(player, menu, messageService);
        return true;
    }

    private boolean manageFlags(Player player, String[] args) {
        if (claimFlagService == null || claimIndex == null) {
            player.sendMessage(message("command.unavailable.claim-info"));
            return true;
        }

        Optional<Claim> claim = claimAtPlayer(player);
        if (claim.isEmpty()) {
            player.sendMessage(message("claim.info.unclaimed"));
            return true;
        }

        if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
            return openFlagEditor(player);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("toggle")) {
            ClaimFlagResult result = claimFlagService.toggleFlag(
                    player.getUniqueId(),
                    claim.orElseThrow(),
                    args[2],
                    player::hasPermission
            );
            if (!result.allowed()) {
                player.sendMessage(message(result.messageKey(), Map.of("flag", args[2])));
                return true;
            }
            player.sendMessage(message("claim.flag.toggled", Map.of("flag", args[2])));
            return openFlagEditor(player);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("set")) {
            Optional<Boolean> enabled = parseBoolean(args[3]);
            if (enabled.isEmpty()) {
                player.sendMessage(message("claim.flag.invalid-value"));
                return true;
            }
            ClaimFlagResult result = claimFlagService.setFlag(
                    player.getUniqueId(),
                    claim.orElseThrow(),
                    args[2],
                    enabled.orElseThrow(),
                    player::hasPermission
            );
            if (!result.allowed()) {
                player.sendMessage(message(result.messageKey(), Map.of("flag", args[2])));
                return true;
            }
            player.sendMessage(message("claim.flag.set", Map.of(
                    "flag", args[2],
                    "value", String.valueOf(enabled.orElseThrow())
            )));
            return true;
        }

        player.sendMessage(message("claim.flag.usage"));
        return true;
    }

    private boolean manageDeniedPlayers(Player player, String[] args) {
        if (claimDenyService == null || claimIndex == null) {
            player.sendMessage(message("command.unavailable.claim-info"));
            return true;
        }
        if (!player.hasPermission(CLAIM_DENY_PERMISSION)) {
            player.sendMessage(message("claim.deny.no-permission"));
            return true;
        }

        Optional<Claim> claim = claimAtPlayer(player);
        if (claim.isEmpty()) {
            player.sendMessage(message("claim.info.unclaimed"));
            return true;
        }

        if (args[0].equalsIgnoreCase("denied")) {
            return listDeniedPlayers(player, claim.orElseThrow());
        }
        if (args.length < 2) {
            player.sendMessage(message("claim.deny.usage"));
            return true;
        }

        if (args[0].equalsIgnoreCase("deny")) {
            Optional<UUID> targetId = resolveOnlinePlayerOrUuid(player, args[1]);
            if (targetId.isEmpty()) {
                player.sendMessage(message("claim.deny.player-not-found", Map.of("player", args[1])));
                return true;
            }
            ClaimDenyResult result = claimDenyService.denyPlayer(
                    player.getUniqueId(),
                    claim.orElseThrow(),
                    targetId.orElseThrow(),
                    player::hasPermission
            );
            if (!result.allowed()) {
                player.sendMessage(message(result.messageKey()));
                return true;
            }
            player.sendMessage(message("claim.deny.added", Map.of("player", playerName(player, targetId.orElseThrow()))));
            return true;
        }
        if (args[0].equalsIgnoreCase("undeny")) {
            Optional<UUID> targetId = findExistingDeniedPlayer(claim.orElseThrow(), player, args[1]);
            if (targetId.isEmpty()) {
                player.sendMessage(message("claim.deny.not-denied", Map.of("player", args[1])));
                return true;
            }
            ClaimDenyResult result = claimDenyService.allowPlayer(
                    player.getUniqueId(),
                    claim.orElseThrow(),
                    targetId.orElseThrow(),
                    player::hasPermission
            );
            if (!result.allowed()) {
                player.sendMessage(message(result.messageKey()));
                return true;
            }
            player.sendMessage(message("claim.deny.removed", Map.of("player", playerName(player, targetId.orElseThrow()))));
            return true;
        }

        player.sendMessage(message("claim.deny.usage"));
        return true;
    }

    private boolean listDeniedPlayers(Player player, Claim claim) {
        if (claim.deniedPlayers().isEmpty()) {
            player.sendMessage(message("claim.deny.list-empty"));
            return true;
        }

        player.sendMessage(message("claim.deny.list-header"));
        claim.deniedPlayers().stream()
                .sorted(java.util.Comparator.comparing(UUID::toString))
                .forEach(deniedPlayer -> player.sendMessage(message("claim.deny.list-entry", Map.of(
                        "player", playerName(player, deniedPlayer)
                ))));
        return true;
    }

    private boolean listFlags(Player player, Claim claim) {
        player.sendMessage(message("claim.flag.list-header"));
        for (ClaimFlagRow row : claimFlagService.listFlags(claim)) {
            player.sendMessage(message("claim.flag.list-entry", Map.of(
                    "flag", row.key(),
                    "label", row.label(),
                    "category", row.category(),
                    "description", row.description(),
                    "value", String.valueOf(row.enabled())
            )));
        }
        return true;
    }

    private Optional<Boolean> parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value)) {
            return Optional.of(true);
        }
        if ("false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value)) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    private boolean manageMembers(Player player, String[] args) {
        if (claimMemberService == null || claimIndex == null) {
            player.sendMessage(message("command.unavailable.claim-info"));
            return true;
        }

        Optional<Claim> claim = claimAtPlayer(player);
        if (claim.isEmpty()) {
            player.sendMessage(message("claim.info.unclaimed"));
            return true;
        }

        if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
            return listMembers(player, claim.orElseThrow());
        }
        if (args.length < 3) {
            player.sendMessage(message("claim.member.usage"));
            return true;
        }

        if (args[1].equalsIgnoreCase("add")) {
            // Require the target to be currently online to guarantee we get the correct Mojang UUID.
            // getOfflinePlayer(String) returns a name-derived UUID for players who have never joined,
            // which would persist the wrong UUID as a member on online-mode servers.
            Player target = player.getServer().getPlayerExact(args[2]);
            if (target == null) {
                player.sendMessage(message("claim.member.player-not-found", Map.of("player", args[2])));
                return true;
            }
            ClaimRole role = args.length >= 4 ? parseRole(args[3]).orElse(null) : ClaimRole.MEMBER;
            if (role == null) {
                player.sendMessage(message("claim.member.invalid-role"));
                return true;
            }
            ClaimMemberResult result = claimMemberService.addMember(
                    player.getUniqueId(),
                    claim.orElseThrow(),
                    target.getUniqueId(),
                    role
            );
            if (!result.allowed()) {
                player.sendMessage(message(result.messageKey()));
                return true;
            }
            player.sendMessage(message("claim.member.added", Map.of(
                    "player", target.getName(),
                    "role", role.name().toLowerCase()
            )));
            return true;
        }
        if (args[1].equalsIgnoreCase("remove")) {
            Optional<ClaimMember> targetMember = findExistingMember(claim.orElseThrow(), player, args[2]);
            if (targetMember.isEmpty()) {
                player.sendMessage(message("claim.member.not-found", Map.of("player", args[2])));
                return true;
            }
            ClaimMemberResult result = claimMemberService.removeMember(
                    player.getUniqueId(),
                    claim.orElseThrow(),
                    targetMember.orElseThrow().memberUuid()
            );
            if (!result.allowed()) {
                player.sendMessage(message(result.messageKey()));
                return true;
            }
            player.sendMessage(message("claim.member.removed", Map.of(
                    "player", memberName(player.getServer().getOfflinePlayer(targetMember.orElseThrow().memberUuid()))
            )));
            return true;
        }

        player.sendMessage(message("claim.member.usage"));
        return true;
    }

    private boolean listMembers(Player player, Claim claim) {
        if (claim.members().isEmpty()) {
            player.sendMessage(message("claim.member.list-empty"));
            return true;
        }

        player.sendMessage(message("claim.member.list-header"));
        claim.members().stream()
                .sorted(java.util.Comparator.comparing(member -> member.memberUuid().toString()))
                .forEach(member -> {
                    OfflinePlayer offlinePlayer = player.getServer().getOfflinePlayer(member.memberUuid());
                    player.sendMessage(message("claim.member.list-entry", Map.of(
                            "player", memberName(offlinePlayer),
                            "role", member.role().name().toLowerCase()
                    )));
                });
        return true;
    }

    private Optional<ClaimRole> parseRole(String value) {
        if ("member".equalsIgnoreCase(value)) {
            return Optional.of(ClaimRole.MEMBER);
        }
        if ("manager".equalsIgnoreCase(value)) {
            return Optional.of(ClaimRole.MANAGER);
        }
        return Optional.empty();
    }

    private String memberName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    private String playerName(Player viewer, UUID playerId) {
        return memberName(viewer.getServer().getOfflinePlayer(playerId));
    }

    private String transferTargetName(Player viewer, String input, UUID playerId) {
        return parseUuid(input).isPresent() ? playerName(viewer, playerId) : input;
    }

    private String adminTargetName(Player viewer, String input, UUID playerId) {
        return parseUuid(input).isPresent() ? input : playerName(viewer, playerId);
    }

    private Optional<UUID> resolveOnlinePlayerOrUuid(Player actor, String input) {
        Optional<UUID> inputUuid = parseUuid(input);
        if (inputUuid.isPresent()) {
            return inputUuid;
        }
        Player onlinePlayer = actor.getServer().getPlayerExact(input);
        return onlinePlayer == null ? Optional.empty() : Optional.of(onlinePlayer.getUniqueId());
    }

    private Optional<UUID> findExistingDeniedPlayer(Claim claim, Player actor, String input) {
        Optional<UUID> inputUuid = parseUuid(input);
        if (inputUuid.isPresent() && claim.deniedPlayers().contains(inputUuid.orElseThrow())) {
            return inputUuid;
        }

        Player onlinePlayer = actor.getServer().getPlayerExact(input);
        if (onlinePlayer != null && claim.deniedPlayers().contains(onlinePlayer.getUniqueId())) {
            return Optional.of(onlinePlayer.getUniqueId());
        }

        return claim.deniedPlayers().stream()
                .filter(deniedPlayer -> {
                    OfflinePlayer offlinePlayer = actor.getServer().getOfflinePlayer(deniedPlayer);
                    return offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(input);
                })
                .findFirst();
    }

    private Optional<ClaimMember> findExistingMember(Claim claim, Player actor, String input) {
        Optional<UUID> inputUuid = parseUuid(input);
        if (inputUuid.isPresent()) {
            return claim.members().stream()
                    .filter(member -> member.memberUuid().equals(inputUuid.orElseThrow()))
                    .findFirst();
        }

        Player onlinePlayer = actor.getServer().getPlayerExact(input);
        if (onlinePlayer != null) {
            Optional<ClaimMember> onlineMember = claim.members().stream()
                    .filter(member -> member.memberUuid().equals(onlinePlayer.getUniqueId()))
                    .findFirst();
            if (onlineMember.isPresent()) {
                return onlineMember;
            }
        }

        return claim.members().stream()
                .filter(member -> {
                    OfflinePlayer offlinePlayer = actor.getServer().getOfflinePlayer(member.memberUuid());
                    return offlinePlayer.getName() != null && offlinePlayer.getName().equalsIgnoreCase(input);
                })
                .findFirst();
    }

    private Optional<UUID> parseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
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
            showBorder(player, chunks, BorderColor.RED);
            player.sendMessage(claimCreateDenied(validationResult.messageKey().orElse("claims.denied")));
            return true;
        }

        List<Claim> mergeTargets = claimCreationService.findMergeTargets(player.getUniqueId(), claimName, chunks);
        if (!mergeTargets.isEmpty() && !mergeConfirmed) {
            if (pendingClaimMergeService != null) {
                pendingClaimMergeService.put(player.getUniqueId(), claimName, chunks);
            }
            showBorder(player, chunks, BorderColor.YELLOW);
            sendMergeConfirmation(player, claimName, mergeTargets.size());
            return true;
        }

        ClaimCostQuote quote = null;
        if (claimCostService != null && claimPaymentService != null) {
            quote = claimCostService.quotePlayerClaim(player.getUniqueId(), permissionNodes(player), chunks);
            ClaimPaymentResult paymentResult = claimPaymentService.charge(player.getUniqueId(), quote);
            if (!paymentResult.allowed()) {
                showBorder(player, chunks, BorderColor.AQUA);
                player.sendMessage(claimCreateDenied(paymentResult.messageKey()));
                return true;
            }
            if (quote.cost() > 0.0) {
                player.sendMessage(message("claim.charged", Map.of(
                        "cost", claimPaymentService.format(quote.cost())
                )));
            }
        }

        // Pass the already-computed mergeTargets to avoid a redundant index scan inside createPlayerClaim.
        ClaimValidationResult result = claimCreationService.createPlayerClaim(
                player.getUniqueId(), claimName, chunks, mergeTargets);
        if (!result.isAllowed()) {
            // Refund the payment — the creation failed (e.g. concurrent overlap) after we charged.
            if (quote != null && claimPaymentService != null) {
                claimPaymentService.refund(player.getUniqueId(), quote);
            }
            player.sendMessage(claimCreateDenied(result.messageKey().orElse("claims.denied")));
            return true;
        }

        claimToolService.spendCharges(mainHandItem, chunks.size());
        selectionService.consumeSelection(player.getUniqueId());
        if (chunkBorderVisualService != null) {
            chunkBorderVisualService.showSelection(player, chunks, BorderColor.GOLD);
        }
        player.sendMessage(message("claim.created", Map.of(
                "claim_name", claimName.trim(),
                "chunk_count", String.valueOf(chunks.size())
        )));
        return true;
    }

    private void showBorder(Player player, Set<ClaimChunk> chunks, BorderColor color) {
        if (chunkBorderVisualService != null) {
            chunkBorderVisualService.showSelection(player, chunks, color);
        }
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
                        .clickEvent(ClickEvent.runCommand("/claim mergeconfirm")))
                .append(Component.space())
                .append(message("claim.merge.cancel-button")
                        .clickEvent(ClickEvent.runCommand("/claim mergecancel"))));
    }

    private Set<String> permissionNodes(Player player) {
        return player.getEffectivePermissions().stream()
                .filter(PermissionAttachmentInfo::getValue)
                .map(PermissionAttachmentInfo::getPermission)
                .collect(Collectors.toUnmodifiableSet());
    }

    private BorderColor previewColor(Player player, Set<ClaimChunk> chunks, Optional<String> claimName) {
        if (claimBorderColorService == null) {
            return BorderColor.GREEN;
        }
        return claimBorderColorService.colorForPlayerSelection(
                player.getUniqueId(),
                claimName,
                chunks,
                permissionNodes(player)
        );
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
        if (chunkBorderVisualService != null) {
            chunkBorderVisualService.clear(player.getUniqueId());
        }
        player.sendMessage(message("command.selection.cleared"));
        return true;
    }

    private boolean showInfo(Player player) {
        if (claimIndex == null) {
            player.sendMessage(message("command.unavailable.claim-info"));
            return true;
        }

        Optional<Claim> claim = claimAtPlayer(player);
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

    private Optional<Claim> claimAtPlayer(Player player) {
        Chunk chunk = player.getLocation().getChunk();
        ClaimChunk claimChunk = new ClaimChunk(player.getWorld().getUID(), chunk.getX(), chunk.getZ());
        return claimIndex.findAt(claimChunk);
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
        player.sendMessage(message("command.help.menu"));
        player.sendMessage(message("command.help.flags"));
        player.sendMessage(message("command.help.viewborder"));
        player.sendMessage(message("command.help.tool"));
        player.sendMessage(message("command.help.create"));
        player.sendMessage(message("command.help.member"));
        player.sendMessage(message("command.help.deny"));
        player.sendMessage(message("command.help.flag"));
        player.sendMessage(message("command.help.cost"));
        player.sendMessage(message("command.help.cancel"));
        player.sendMessage(message("command.help.info"));
        player.sendMessage(message("command.help.admin"));
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
