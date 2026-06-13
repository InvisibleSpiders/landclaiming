package com.nick.landclaims.plugin.ui;

import com.nick.landclaims.plugin.message.MessageService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogInstancesProvider;
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

    public DialogService(boolean preferDialogs) {
        this.preferDialogs = preferDialogs;
    }

    public void openClaimMenu(Player player, ClaimMenu menu, MessageService messageService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(messageService, "messageService");

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

    public void openFlagEditor(Player player, ClaimFlagEditor editor, MessageService messageService) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(messageService, "messageService");

        if (preferDialogs && tryOpenDialog(player, editor)) {
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

    private boolean tryOpenDialog(Player player, ClaimFlagEditor editor) {
        try {
            return buildAndShowDialog(player, editor);
        } catch (LinkageError | RuntimeException error) {
            return false;
        }
    }

    private boolean buildAndShowDialog(Player player, ClaimFlagEditor editor) {
        DialogInstancesProvider provider = DialogInstancesProvider.instance();

        List<ActionButton> buttons = new ArrayList<>();
        for (ClaimFlagEditorRow row : editor.rows()) {
            Component label = Component.text(row.label() + " — " + row.stateLabel());
            Component tooltip = Component.text(row.description());
            DialogAction action = DialogAction.staticAction(ClickEvent.runCommand(row.toggleCommand()));
            ActionButton button = provider.actionButtonBuilder(label)
                    .tooltip(tooltip)
                    .action(action)
                    .build();
            buttons.add(button);
        }

        Component title = Component.text(editor.claimName());
        DialogBase base = provider.dialogBaseBuilder(title)
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

    public void openClaimSetup(Player player) {
        Objects.requireNonNull(player, "player");
        player.sendMessage(Component.text("Claim setup dialog coming soon.", NamedTextColor.YELLOW));
    }

}
