package com.invisiblespiders.havenclaims.plugin.ui;

import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class DialogService {
    private static final Logger LOGGER = Logger.getLogger(DialogService.class.getName());
    private boolean preferDialogs;
    private final DialogRenderer dialogRenderer;

    public DialogService(boolean preferDialogs) {
        this(preferDialogs, new PaperDialogRenderer());
    }

    DialogService(boolean preferDialogs, DialogRenderer dialogRenderer) {
        this.preferDialogs = preferDialogs;
        this.dialogRenderer = Objects.requireNonNull(dialogRenderer, "dialogRenderer");
    }

    public boolean prefersDialogs() {
        return preferDialogs;
    }

    public void reload(boolean preferDialogs) {
        this.preferDialogs = preferDialogs;
    }

    public void openClaimMenu(Player player, ClaimMenu menu, MessageService messageService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(messageService, "messageService");

        if (preferDialogs && tryOpenClaimMenuDialog(player, menu, messageService)) {
            return;
        }
        openClaimMenuChat(player, menu, messageService);
    }

    private void openClaimMenuChat(Player player, ClaimMenu menu, MessageService messageService) {
        player.sendMessage(messageService.render("claim.menu.title", Map.of("claim_name", menu.title())));
        player.sendMessage(messageService.render("claim.menu.owner-type", Map.of("owner_type", menu.ownerType())));
        player.sendMessage(messageService.render("claim.menu.blocks", Map.of("block_count", String.valueOf(menu.blockCount()))));
        player.sendMessage(messageService.render("claim.menu.members", Map.of("member_count", String.valueOf(menu.memberCount()))));
        player.sendMessage(messageService.render("claim.menu.flags", Map.of("flag_count", String.valueOf(menu.flagCount()))));
        player.sendMessage(messageService.render("claim.menu.viewer-owner", Map.of("is_owner", String.valueOf(menu.viewerOwnsClaim()))));
        if (menu.adminClaim()) {
            player.sendMessage(messageService.render("claim.menu.admin-claim", Map.of()));
        }
        player.sendMessage(messageService.render("claim.menu.actions-header", Map.of()));
        for (ClaimMenuAction action : menu.actions()) {
            player.sendMessage(messageService.render("claim.menu.action", Map.of(
                    "label", action.label(),
                    "command", action.command()
            )).clickEvent(ClickEvent.runCommand(action.command())));
        }
    }

    public void openClaimDashboard(Player player, ClaimDashboard dashboard, MessageService messageService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(dashboard, "dashboard");
        Objects.requireNonNull(messageService, "messageService");

        if (preferDialogs && tryOpenClaimDashboardDialog(player, dashboard, messageService)) {
            return;
        }
        openClaimDashboardChat(player, dashboard, messageService);
    }

    private void openClaimDashboardChat(Player player, ClaimDashboard dashboard, MessageService messageService) {
        player.sendMessage(messageService.renderOrDefault("claim.dashboard.title", Map.of(), "<gold>My Claims"));
        if (dashboard.claims().isEmpty()) {
            player.sendMessage(messageService.renderOrDefault(
                    "claim.dashboard.empty",
                    Map.of(),
                    "<yellow>You do not have any claims yet."));
        }
        for (ClaimDashboardRow row : dashboard.claims()) {
            player.sendMessage(messageService.renderOrDefault(
                    "claim.dashboard.claim",
                    Map.of(
                            "claim_id", row.claimId().toString(),
                            "claim_name", row.claimName(),
                            "block_count", String.valueOf(row.blockCount()),
                            "is_current", String.valueOf(row.currentClaim()),
                            "command", row.manageCommand()
                    ),
                    "<gray>- <yellow><claim_name></yellow> (<block_count> blocks) <command>")
                    .clickEvent(ClickEvent.runCommand(row.manageCommand())));
        }
        player.sendMessage(messageService.renderOrDefault("claim.dashboard.actions-header", Map.of(), "<gold>Actions:"));
        for (ClaimMenuAction action : dashboard.actions()) {
            player.sendMessage(messageService.renderOrDefault(
                    "claim.dashboard.action",
                    Map.of(
                            "label", action.label(),
                            "command", action.command()
                    ),
                    "<gray>- <yellow><label></yellow> (<command>)")
                    .clickEvent(ClickEvent.runCommand(action.command())));
        }
    }

    public void openClaimInfo(Player player, ClaimInfoView info, MessageService messageService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(messageService, "messageService");

        if (preferDialogs && tryOpenClaimInfoDialog(player, info, messageService)) {
            return;
        }
        openClaimInfoChat(player, info, messageService);
    }

    private void openClaimInfoChat(Player player, ClaimInfoView info, MessageService messageService) {
        Map<String, String> placeholders = infoPlaceholders(info);
        player.sendMessage(messageService.renderOrDefault(
                "claim.info.name",
                placeholders,
                "<gold>Claim: <yellow><claim_name></yellow>"));
        player.sendMessage(messageService.renderOrDefault(
                "claim.info.owner-type",
                placeholders,
                "<gray>Owner type: <white><owner_type></white>"));
        player.sendMessage(messageService.renderOrDefault(
                "claim.info.blocks",
                placeholders,
                "<gray>Blocks: <white><block_count></white>"));
        player.sendMessage(messageService.renderOrDefault(
                "claim.info.members",
                placeholders,
                "<gray>Members: <white><member_count></white>"));
        player.sendMessage(messageService.renderOrDefault(
                "claim.info.denied",
                placeholders,
                "<gray>Denied players: <white><denied_count></white>"));
        player.sendMessage(messageService.renderOrDefault(
                "claim.info.flags",
                placeholders,
                "<gray>Configured flags: <white><flag_count></white>"));
        player.sendMessage(messageService.renderOrDefault(
                "claim.info.you-own",
                placeholders,
                "<gray>You are owner: <white><is_owner></white>"));
        sendActions(player, messageService, "claim.info", info.actions(), info.backAction());
    }

    public void openClaimMembers(Player player, ClaimMembersView members, MessageService messageService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(members, "members");
        Objects.requireNonNull(messageService, "messageService");

        if (preferDialogs && tryOpenClaimMembersDialog(player, members, messageService)) {
            return;
        }
        openClaimMembersChat(player, members, messageService);
    }

    private void openClaimMembersChat(Player player, ClaimMembersView members, MessageService messageService) {
        player.sendMessage(messageService.renderOrDefault(
                "claim.member.dialog.title",
                Map.of("claim_id", members.claimId().toString(), "claim_name", members.claimName()),
                "<gold>Members for <yellow><claim_name></yellow>"));
        if (members.members().isEmpty()) {
            player.sendMessage(messageService.renderOrDefault(
                    "claim.member.list-empty",
                    Map.of("claim_id", members.claimId().toString(), "claim_name", members.claimName()),
                    "<yellow>This claim has no members."));
        }
        for (ClaimMemberViewRow row : members.members()) {
            player.sendMessage(messageService.renderOrDefault(
                    "claim.member.dialog.row",
                    Map.of(
                            "claim_id", members.claimId().toString(),
                            "claim_name", members.claimName(),
                            "player", row.playerName(),
                            "role", row.role()
                    ),
                    "<gray>- <yellow><player></yellow> (<role>)"));
        }
        sendActions(player, messageService, "claim.member.dialog", members.actions(), members.backAction());
    }

    public void openDeniedPlayers(Player player, ClaimDeniedPlayersView denied, MessageService messageService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(denied, "denied");
        Objects.requireNonNull(messageService, "messageService");

        if (preferDialogs && tryOpenDeniedPlayersDialog(player, denied, messageService)) {
            return;
        }
        openDeniedPlayersChat(player, denied, messageService);
    }

    private void openDeniedPlayersChat(Player player, ClaimDeniedPlayersView denied, MessageService messageService) {
        player.sendMessage(messageService.renderOrDefault(
                "claim.deny.dialog.title",
                Map.of("claim_id", denied.claimId().toString(), "claim_name", denied.claimName()),
                "<gold>Denied players for <yellow><claim_name></yellow>"));
        if (denied.deniedPlayers().isEmpty()) {
            player.sendMessage(messageService.renderOrDefault(
                    "claim.deny.list-empty",
                    Map.of("claim_id", denied.claimId().toString(), "claim_name", denied.claimName()),
                    "<yellow>This claim has no denied players."));
        }
        for (ClaimDeniedPlayerViewRow row : denied.deniedPlayers()) {
            player.sendMessage(messageService.renderOrDefault(
                    "claim.deny.dialog.row",
                    Map.of(
                            "claim_id", denied.claimId().toString(),
                            "claim_name", denied.claimName(),
                            "player", row.playerName()
                    ),
                    "<gray>- <yellow><player></yellow>"));
        }
        sendActions(player, messageService, "claim.deny.dialog", denied.actions(), denied.backAction());
    }

    public void openFlagEditor(Player player, ClaimFlagEditor editor, MessageService messageService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(messageService, "messageService");

        if (preferDialogs && tryOpenFlagEditorDialog(player, editor, messageService)) {
            return;
        }
        openFlagEditorChat(player, editor, messageService);
    }

    private void openFlagEditorChat(Player player, ClaimFlagEditor editor, MessageService messageService) {
        player.sendMessage(messageService.render("claim.flag-editor.title", Map.of("claim_name", editor.claimName())));
        for (ClaimFlagEditorRow row : editor.rows()) {
            player.sendMessage(messageService.render("claim.flag-editor.row", Map.of(
                    "flag", row.key(),
                    "label", row.label(),
                    "category", row.category(),
                    "description", row.description(),
                    "state", row.stateLabel(),
                    "next_state", row.nextStateLabel()
            )).clickEvent(ClickEvent.runCommand(row.toggleCommand())));
        }
    }

    private boolean tryOpenClaimMenuDialog(Player player, ClaimMenu menu, MessageService messageService) {
        try {
            return dialogRenderer.openClaimMenu(player, menu, messageService);
        } catch (LinkageError | RuntimeException error) {
            LOGGER.log(Level.WARNING, "[HavenClaims] Failed to open Claim Menu dialog; falling back to chat.", error);
            return false;
        }
    }

    private boolean tryOpenFlagEditorDialog(Player player, ClaimFlagEditor editor, MessageService messageService) {
        try {
            return dialogRenderer.openFlagEditor(player, editor, messageService);
        } catch (LinkageError | RuntimeException error) {
            LOGGER.log(Level.WARNING, "[HavenClaims] Failed to open Flag Editor dialog; falling back to chat.", error);
            return false;
        }
    }

    private boolean tryOpenClaimDashboardDialog(Player player, ClaimDashboard dashboard, MessageService messageService) {
        try {
            return dialogRenderer.openClaimDashboard(player, dashboard, messageService);
        } catch (LinkageError | RuntimeException error) {
            LOGGER.log(Level.WARNING, "[HavenClaims] Failed to open Claim Dashboard dialog; falling back to chat.", error);
            return false;
        }
    }

    private boolean tryOpenClaimInfoDialog(Player player, ClaimInfoView info, MessageService messageService) {
        try {
            return dialogRenderer.openClaimInfo(player, info, messageService);
        } catch (LinkageError | RuntimeException error) {
            LOGGER.log(Level.WARNING, "[HavenClaims] Failed to open Claim Info dialog; falling back to chat.", error);
            return false;
        }
    }

    private boolean tryOpenClaimMembersDialog(Player player, ClaimMembersView members, MessageService messageService) {
        try {
            return dialogRenderer.openClaimMembers(player, members, messageService);
        } catch (LinkageError | RuntimeException error) {
            LOGGER.log(Level.WARNING, "[HavenClaims] Failed to open Claim Members dialog; falling back to chat.", error);
            return false;
        }
    }

    private boolean tryOpenDeniedPlayersDialog(Player player, ClaimDeniedPlayersView denied, MessageService messageService) {
        try {
            return dialogRenderer.openDeniedPlayers(player, denied, messageService);
        } catch (LinkageError | RuntimeException error) {
            LOGGER.log(Level.WARNING, "[HavenClaims] Failed to open Denied Players dialog; falling back to chat.", error);
            return false;
        }
    }

    private void sendActions(
            Player player,
            MessageService messageService,
            String prefix,
            List<ClaimMenuAction> actions,
            ClaimMenuAction backAction
    ) {
        player.sendMessage(messageService.renderOrDefault(prefix + ".actions-header", Map.of(), "<gold>Actions:"));
        for (ClaimMenuAction action : actions) {
            sendAction(player, messageService, prefix, action);
        }
        sendAction(player, messageService, prefix, backAction);
    }

    private void sendAction(Player player, MessageService messageService, String prefix, ClaimMenuAction action) {
        player.sendMessage(messageService.renderOrDefault(
                prefix + ".action",
                Map.of("label", action.label(), "command", action.command()),
                "<gray>- <yellow><label></yellow> (<command>)")
                .clickEvent(ClickEvent.runCommand(action.command())));
    }

    private static Map<String, String> infoPlaceholders(ClaimInfoView info) {
        return Map.of(
                "claim_id", info.claimId().toString(),
                "claim_name", info.claimName(),
                "owner_type", info.ownerType(),
                "block_count", String.valueOf(info.blockCount()),
                "member_count", String.valueOf(info.memberCount()),
                "denied_count", String.valueOf(info.deniedCount()),
                "flag_count", String.valueOf(info.flagCount()),
                "is_owner", String.valueOf(info.viewerOwnsClaim())
        );
    }

    public void openClaimSetup(Player player) {
        Objects.requireNonNull(player, "player");
        player.sendMessage(Component.text("Claim setup dialog coming soon.", NamedTextColor.YELLOW));
    }

    public void openClaimCreatePreview(Player player, ClaimCreatePreview preview, MessageService messageService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(preview, "preview");
        Objects.requireNonNull(messageService, "messageService");

        if (preferDialogs && tryOpenClaimCreatePreviewDialog(player, preview, messageService)) {
            return;
        }
        Map<String, String> placeholders = createPreviewPlaceholders(preview);
        player.sendMessage(messageService.renderOrDefault(
                "claim.create-preview.title",
                placeholders,
                "<gold>Create claim <yellow><claim_name></yellow>?"));
        player.sendMessage(messageService.renderOrDefault(
                "claim.create-preview.selection",
                placeholders,
                "<gray>Selection: <yellow><selected_blocks></yellow> blocks"));
        player.sendMessage(messageService.renderOrDefault(
                "claim.create-preview.current-total",
                placeholders,
                "<gray>Total after claim: <yellow><proposed_total_blocks></yellow> / <yellow><allowed_blocks></yellow> blocks"));
        player.sendMessage(messageService.renderOrDefault(
                "claim.create-preview.over-limit",
                placeholders,
                "<gray>Over limit: <yellow><overage_blocks></yellow> blocks"));
        player.sendMessage(messageService.renderOrDefault(
                "claim.create-preview.cost",
                placeholders,
                "<gray>Cost: <green><cost></green>"));
        sendActions(player, messageService, "claim.create-preview", List.of(preview.confirmAction()), preview.cancelAction());
    }

    private boolean tryOpenClaimCreatePreviewDialog(Player player, ClaimCreatePreview preview, MessageService messageService) {
        try {
            return dialogRenderer.openClaimCreatePreview(player, preview, messageService);
        } catch (LinkageError | RuntimeException error) {
            return false;
        }
    }

    private static Map<String, String> createPreviewPlaceholders(ClaimCreatePreview preview) {
        return Map.of(
                "claim_name", preview.claimName(),
                "selected_blocks", String.valueOf(preview.selectedBlocks()),
                "proposed_total_blocks", String.valueOf(preview.proposedTotalBlocks()),
                "allowed_blocks", String.valueOf(preview.allowedBlocks()),
                "overage_blocks", String.valueOf(preview.overageBlocks()),
                "cost", preview.cost()
        );
    }

    interface DialogRenderer {
        boolean openClaimMenu(Player player, ClaimMenu menu, MessageService messageService);

        boolean openClaimDashboard(Player player, ClaimDashboard dashboard, MessageService messageService);

        boolean openFlagEditor(Player player, ClaimFlagEditor editor, MessageService messageService);

        boolean openClaimInfo(Player player, ClaimInfoView info, MessageService messageService);

        boolean openClaimMembers(Player player, ClaimMembersView members, MessageService messageService);

        boolean openDeniedPlayers(Player player, ClaimDeniedPlayersView denied, MessageService messageService);

        boolean openClaimCreatePreview(Player player, ClaimCreatePreview preview, MessageService messageService);
    }

    private static final class PaperDialogRenderer implements DialogRenderer {
        @Override
        public boolean openClaimMenu(Player player, ClaimMenu menu, MessageService messageService) {
            List<ActionButton> buttons = new ArrayList<>();
            for (ClaimMenuAction row : menu.actions()) {
                Map<String, String> placeholders = Map.of(
                        "label", row.label(),
                        "command", row.command()
                );
                buttons.add(ActionButton.builder(messageService.renderOrDefault(
                                "claim.menu.dialog.button",
                                placeholders,
                                "<yellow><label></yellow>"))
                        .tooltip(messageService.renderOrDefault(
                                "claim.menu.dialog.tooltip",
                                placeholders,
                                "<gray><command></gray>"))
                        .action(DialogAction.staticAction(ClickEvent.runCommand(row.command())))
                        .build());
            }

            DialogBase base = DialogBase.builder(messageService.renderOrDefault(
                    "claim.menu.dialog.title",
                    Map.of(
                            "claim_name", menu.title(),
                            "owner_type", menu.ownerType(),
                            "block_count", String.valueOf(menu.blockCount()),
                            "member_count", String.valueOf(menu.memberCount()),
                            "flag_count", String.valueOf(menu.flagCount()),
                            "is_owner", String.valueOf(menu.viewerOwnsClaim()),
                            "is_admin_claim", String.valueOf(menu.adminClaim())
                    ),
                    "<gold>Claim Menu: <yellow><claim_name></yellow>"))
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .build();

            Dialog dialog = Dialog.create(factory -> {
                factory.empty()
                        .base(base)
                        .type(DialogType.multiAction(buttons, backButton(messageService, "/claim"), 2));
            });

            player.showDialog(dialog);
            return true;
        }

        @Override
        public boolean openClaimDashboard(Player player, ClaimDashboard dashboard, MessageService messageService) {
            List<ActionButton> buttons = new ArrayList<>();
            for (ClaimDashboardRow row : dashboard.claims()) {
                Map<String, String> placeholders = Map.of(
                        "claim_id", row.claimId().toString(),
                        "claim_name", row.claimName(),
                        "block_count", String.valueOf(row.blockCount()),
                        "is_current", String.valueOf(row.currentClaim()),
                        "command", row.manageCommand()
                );
                buttons.add(ActionButton.builder(messageService.renderOrDefault(
                                "claim.dashboard.dialog.claim-button",
                                placeholders,
                                "<yellow><claim_name></yellow> <gray>(<block_count> blocks)</gray>"))
                        .tooltip(messageService.renderOrDefault(
                                "claim.dashboard.dialog.claim-tooltip",
                                placeholders,
                                "<gray><command></gray>"))
                        .action(DialogAction.staticAction(ClickEvent.runCommand(row.manageCommand())))
                        .build());
            }
            for (ClaimMenuAction action : dashboard.actions()) {
                Map<String, String> placeholders = Map.of(
                        "label", action.label(),
                        "command", action.command()
                );
                buttons.add(ActionButton.builder(messageService.renderOrDefault(
                                "claim.dashboard.dialog.action-button",
                                placeholders,
                                "<yellow><label></yellow>"))
                        .tooltip(messageService.renderOrDefault(
                                "claim.dashboard.dialog.action-tooltip",
                                placeholders,
                                "<gray><command></gray>"))
                        .action(DialogAction.staticAction(ClickEvent.runCommand(action.command())))
                        .build());
            }

            DialogBase base = DialogBase.builder(messageService.renderOrDefault(
                    "claim.dashboard.dialog.title",
                    Map.of(),
                    "<gold>My Claims"))
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .build();

            Dialog dialog = Dialog.create(factory -> {
                factory.empty()
                        .base(base)
                        .type(DialogType.multiAction(buttons, closeButton(messageService), 2));
            });

            player.showDialog(dialog);
            return true;
        }

        @Override
        public boolean openFlagEditor(Player player, ClaimFlagEditor editor, MessageService messageService) {
            List<ActionButton> buttons = new ArrayList<>();
            for (ClaimFlagEditorRow row : editor.rows()) {
                Map<String, String> placeholders = Map.of(
                        "flag", row.key(),
                        "label", row.label(),
                        "category", row.category(),
                        "description", row.description(),
                        "state", row.stateLabel(),
                        "next_state", row.nextStateLabel(),
                        "command", row.toggleCommand()
                );
                buttons.add(ActionButton.builder(messageService.renderOrDefault(
                                "claim.flag-editor.dialog.button",
                                placeholders,
                                "<yellow><label></yellow> <gray>-</gray> <white><state></white>"))
                        .tooltip(messageService.renderOrDefault(
                                "claim.flag-editor.dialog.tooltip",
                                placeholders,
                                "<gray><description></gray> <dark_gray>Next: <next_state></dark_gray>"))
                        .action(DialogAction.staticAction(ClickEvent.runCommand(row.toggleCommand())))
                        .build());
            }

            DialogBase base = DialogBase.builder(messageService.renderOrDefault(
                    "claim.flag-editor.dialog.title",
                    Map.of("claim_name", editor.claimName()),
                    "<gold>Flags for <yellow><claim_name></yellow>"))
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .build();

            Dialog dialog = Dialog.create(factory -> {
                factory.empty()
                        .base(base)
                        .type(DialogType.multiAction(buttons, backButton(messageService, "/claim"), 2));
            });

            player.showDialog(dialog);
            return true;
        }

        @Override
        public boolean openClaimInfo(Player player, ClaimInfoView info, MessageService messageService) {
            List<DialogBody> body = List.of(
                    DialogBody.plainMessage(messageService.renderOrDefault(
                            "claim.info.owner-type",
                            infoPlaceholders(info),
                            "<gray>Owner type: <white><owner_type></white>")),
                    DialogBody.plainMessage(messageService.renderOrDefault(
                            "claim.info.blocks",
                            infoPlaceholders(info),
                            "<gray>Blocks: <white><block_count></white>")),
                    DialogBody.plainMessage(messageService.renderOrDefault(
                            "claim.info.members",
                            infoPlaceholders(info),
                            "<gray>Members: <white><member_count></white>")),
                    DialogBody.plainMessage(messageService.renderOrDefault(
                            "claim.info.denied",
                            infoPlaceholders(info),
                            "<gray>Denied players: <white><denied_count></white>")),
                    DialogBody.plainMessage(messageService.renderOrDefault(
                            "claim.info.flags",
                            infoPlaceholders(info),
                            "<gray>Configured flags: <white><flag_count></white>")),
                    DialogBody.plainMessage(messageService.renderOrDefault(
                            "claim.info.you-own",
                            infoPlaceholders(info),
                            "<gray>You are owner: <white><is_owner></white>"))
            );
            DialogBase base = DialogBase.builder(messageService.renderOrDefault(
                    "claim.info.dialog.title",
                    infoPlaceholders(info),
                    "<gold>Claim: <yellow><claim_name></yellow>"))
                    .body(body)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .build();

            List<ActionButton> buttons = actionButtons(info.actions(), messageService, "claim.info.dialog");
            Dialog dialog = Dialog.create(factory -> factory.empty()
                    .base(base)
                    .type(buttons.isEmpty()
                            ? DialogType.notice(backButton(messageService, info.backAction()))
                            : DialogType.multiAction(buttons, backButton(messageService, info.backAction()), 2)));

            player.showDialog(dialog);
            return true;
        }

        @Override
        public boolean openClaimMembers(Player player, ClaimMembersView members, MessageService messageService) {
            List<DialogBody> body = new ArrayList<>();
            if (members.members().isEmpty()) {
                body.add(DialogBody.plainMessage(messageService.renderOrDefault(
                        "claim.member.list-empty",
                        Map.of("claim_id", members.claimId().toString(), "claim_name", members.claimName()),
                        "<yellow>This claim has no members.")));
            }
            for (ClaimMemberViewRow row : members.members()) {
                body.add(DialogBody.plainMessage(messageService.renderOrDefault(
                        "claim.member.dialog.row",
                        Map.of(
                                "claim_id", members.claimId().toString(),
                                "claim_name", members.claimName(),
                                "player", row.playerName(),
                                "role", row.role()
                        ),
                        "<gray>- <yellow><player></yellow> (<role>)")));
            }
            DialogBase base = DialogBase.builder(messageService.renderOrDefault(
                    "claim.member.dialog.title",
                    Map.of("claim_id", members.claimId().toString(), "claim_name", members.claimName()),
                    "<gold>Members for <yellow><claim_name></yellow>"))
                    .body(body)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .build();

            List<ActionButton> buttons = actionButtons(members.actions(), messageService, "claim.member.dialog");
            Dialog dialog = Dialog.create(factory -> factory.empty()
                    .base(base)
                    .type(buttons.isEmpty()
                            ? DialogType.notice(backButton(messageService, members.backAction()))
                            : DialogType.multiAction(buttons, backButton(messageService, members.backAction()), 2)));

            player.showDialog(dialog);
            return true;
        }

        @Override
        public boolean openDeniedPlayers(Player player, ClaimDeniedPlayersView denied, MessageService messageService) {
            List<DialogBody> body = new ArrayList<>();
            if (denied.deniedPlayers().isEmpty()) {
                body.add(DialogBody.plainMessage(messageService.renderOrDefault(
                        "claim.deny.list-empty",
                        Map.of("claim_id", denied.claimId().toString(), "claim_name", denied.claimName()),
                        "<yellow>This claim has no denied players.")));
            }
            for (ClaimDeniedPlayerViewRow row : denied.deniedPlayers()) {
                body.add(DialogBody.plainMessage(messageService.renderOrDefault(
                        "claim.deny.dialog.row",
                        Map.of(
                                "claim_id", denied.claimId().toString(),
                                "claim_name", denied.claimName(),
                                "player", row.playerName()
                        ),
                        "<gray>- <yellow><player></yellow>")));
            }
            DialogBase base = DialogBase.builder(messageService.renderOrDefault(
                    "claim.deny.dialog.title",
                    Map.of("claim_id", denied.claimId().toString(), "claim_name", denied.claimName()),
                    "<gold>Denied players for <yellow><claim_name></yellow>"))
                    .body(body)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .build();

            List<ActionButton> buttons = actionButtons(denied.actions(), messageService, "claim.deny.dialog");
            Dialog dialog = Dialog.create(factory -> factory.empty()
                    .base(base)
                    .type(buttons.isEmpty()
                            ? DialogType.notice(backButton(messageService, denied.backAction()))
                            : DialogType.multiAction(buttons, backButton(messageService, denied.backAction()), 2)));

            player.showDialog(dialog);
            return true;
        }

        @Override
        public boolean openClaimCreatePreview(Player player, ClaimCreatePreview preview, MessageService messageService) {
            Map<String, String> placeholders = createPreviewPlaceholders(preview);
            List<DialogBody> body = List.of(
                    DialogBody.plainMessage(messageService.renderOrDefault(
                            "claim.create-preview.selection",
                            placeholders,
                            "<gray>Selection: <yellow><selected_chunks></yellow> chunks")),
                    DialogBody.plainMessage(messageService.renderOrDefault(
                            "claim.create-preview.current-total",
                            placeholders,
                            "<gray>Total after claim: <yellow><proposed_total_chunks></yellow> / <yellow><allowed_chunks></yellow> chunks")),
                    DialogBody.plainMessage(messageService.renderOrDefault(
                            "claim.create-preview.over-limit",
                            placeholders,
                            "<gray>Over limit: <yellow><overage_chunks></yellow> chunks")),
                    DialogBody.plainMessage(messageService.renderOrDefault(
                            "claim.create-preview.cost",
                            placeholders,
                            "<gray>Cost: <green><cost></green>"))
            );
            DialogBase base = DialogBase.builder(messageService.renderOrDefault(
                    "claim.create-preview.title",
                    placeholders,
                    "<gold>Create claim <yellow><claim_name></yellow>?"))
                    .body(body)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .build();
            Dialog dialog = Dialog.create(factory -> factory.empty()
                    .base(base)
                    .type(DialogType.multiAction(
                            List.of(actionButton(messageService, "claim.create-preview", preview.confirmAction())),
                            backButton(messageService, preview.cancelAction()),
                            1)));

            player.showDialog(dialog);
            return true;
        }

        private static List<ActionButton> actionButtons(
                List<ClaimMenuAction> actions,
                MessageService messageService,
                String prefix
        ) {
            List<ActionButton> buttons = new ArrayList<>();
            for (ClaimMenuAction action : actions) {
                buttons.add(actionButton(messageService, prefix, action));
            }
            return buttons;
        }

        private static ActionButton actionButton(MessageService messageService, String prefix, ClaimMenuAction action) {
            Map<String, String> placeholders = Map.of(
                    "label", action.label(),
                    "command", action.command()
            );
            return ActionButton.builder(messageService.renderOrDefault(
                            prefix + ".button",
                            placeholders,
                            "<yellow><label></yellow>"))
                    .tooltip(messageService.renderOrDefault(
                            prefix + ".tooltip",
                            placeholders,
                            "<gray><command></gray>"))
                    .action(DialogAction.staticAction(ClickEvent.runCommand(action.command())))
                    .build();
        }

        private static ActionButton backButton(MessageService messageService, String command) {
            return backButton(messageService, new ClaimMenuAction("Back", command));
        }

        private static ActionButton backButton(MessageService messageService, ClaimMenuAction action) {
            return ActionButton.builder(messageService.renderOrDefault(
                            "dialog.back",
                            Map.of("label", action.label(), "command", action.command()),
                            "<yellow><label></yellow>"))
                    .tooltip(messageService.renderOrDefault(
                            "dialog.back-tooltip",
                            Map.of("label", action.label(), "command", action.command()),
                            "<gray><command></gray>"))
                    .action(DialogAction.staticAction(ClickEvent.runCommand(action.command())))
                    .build();
        }

        private static ActionButton closeButton(MessageService messageService) {
            return ActionButton.builder(messageService.renderOrDefault(
                            "dialog.close",
                            Map.of(),
                            "<yellow>Close</yellow>"))
                    .build();
        }
    }
}
