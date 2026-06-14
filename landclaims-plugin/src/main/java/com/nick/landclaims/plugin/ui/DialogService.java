package com.nick.landclaims.plugin.ui;

import com.nick.landclaims.plugin.message.MessageService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public final class DialogService {
    private final boolean preferDialogs;
    private final DialogRenderer dialogRenderer;

    public DialogService(boolean preferDialogs) {
        this(preferDialogs, new PaperDialogRenderer());
    }

    DialogService(boolean preferDialogs, DialogRenderer dialogRenderer) {
        this.preferDialogs = preferDialogs;
        this.dialogRenderer = Objects.requireNonNull(dialogRenderer, "dialogRenderer");
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
        player.sendMessage(messageService.render("claim.menu.chunks", Map.of("chunk_count", String.valueOf(menu.chunkCount()))));
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
                            "chunk_count", String.valueOf(row.chunkCount()),
                            "is_current", String.valueOf(row.currentClaim()),
                            "command", row.manageCommand()
                    ),
                    "<gray>- <yellow><claim_name></yellow> (<chunk_count> chunks) <command>")
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
            return false;
        }
    }

    private boolean tryOpenFlagEditorDialog(Player player, ClaimFlagEditor editor, MessageService messageService) {
        try {
            return dialogRenderer.openFlagEditor(player, editor, messageService);
        } catch (LinkageError | RuntimeException error) {
            return false;
        }
    }

    private boolean tryOpenClaimDashboardDialog(Player player, ClaimDashboard dashboard, MessageService messageService) {
        try {
            return dialogRenderer.openClaimDashboard(player, dashboard, messageService);
        } catch (LinkageError | RuntimeException error) {
            return false;
        }
    }

    public void openClaimSetup(Player player) {
        Objects.requireNonNull(player, "player");
        player.sendMessage(Component.text("Claim setup dialog coming soon.", NamedTextColor.YELLOW));
    }

    interface DialogRenderer {
        boolean openClaimMenu(Player player, ClaimMenu menu, MessageService messageService);

        boolean openClaimDashboard(Player player, ClaimDashboard dashboard, MessageService messageService);

        boolean openFlagEditor(Player player, ClaimFlagEditor editor, MessageService messageService);
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
                            "chunk_count", String.valueOf(menu.chunkCount()),
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
                        .type(DialogType.multiAction(buttons).build());
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
                        "chunk_count", String.valueOf(row.chunkCount()),
                        "is_current", String.valueOf(row.currentClaim()),
                        "command", row.manageCommand()
                );
                buttons.add(ActionButton.builder(messageService.renderOrDefault(
                                "claim.dashboard.dialog.claim-button",
                                placeholders,
                                "<yellow><claim_name></yellow> <gray>(<chunk_count> chunks)</gray>"))
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
                        .type(DialogType.multiAction(buttons).build());
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
                    .afterAction(DialogBase.DialogAfterAction.NONE)
                    .build();

            Dialog dialog = Dialog.create(factory -> {
                factory.empty()
                        .base(base)
                        .type(DialogType.multiAction(buttons).build());
            });

            player.showDialog(dialog);
            return true;
        }
    }
}
