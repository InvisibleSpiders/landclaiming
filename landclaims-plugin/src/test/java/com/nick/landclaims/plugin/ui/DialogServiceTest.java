package com.nick.landclaims.plugin.ui;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nick.landclaims.plugin.message.MessageService;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class DialogServiceTest {

    @Test
    void opensClaimMenuWithDialogRendererWhenPreferred() {
        Player player = mock(Player.class);
        DialogService.DialogRenderer renderer = mock(DialogService.DialogRenderer.class);
        ClaimMenu menu = menu();
        when(renderer.openClaimMenu(player, menu)).thenReturn(true);
        DialogService service = new DialogService(true, renderer);

        service.openClaimMenu(player, menu, messages());

        verify(renderer).openClaimMenu(player, menu);
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    void opensFlagEditorWithDialogRendererWhenPreferred() {
        Player player = mock(Player.class);
        DialogService.DialogRenderer renderer = mock(DialogService.DialogRenderer.class);
        ClaimFlagEditor editor = editor();
        when(renderer.openFlagEditor(player, editor)).thenReturn(true);
        DialogService service = new DialogService(true, renderer);

        service.openFlagEditor(player, editor, messages());

        verify(renderer).openFlagEditor(player, editor);
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    void fallsBackToChatWhenDialogsAreNotPreferred() {
        Player player = mock(Player.class);
        DialogService.DialogRenderer renderer = mock(DialogService.DialogRenderer.class);
        DialogService service = new DialogService(false, renderer);

        service.openClaimMenu(player, menu(), messages());

        verify(renderer, never()).openClaimMenu(any(Player.class), any(ClaimMenu.class));
        verify(player, atLeastOnce()).sendMessage(any(Component.class));
    }

    @Test
    void fallsBackToChatWhenPreferredDialogRendererCannotOpen() {
        Player player = mock(Player.class);
        DialogService.DialogRenderer renderer = mock(DialogService.DialogRenderer.class);
        ClaimMenu menu = menu();
        when(renderer.openClaimMenu(player, menu)).thenReturn(false);
        DialogService service = new DialogService(true, renderer);

        service.openClaimMenu(player, menu, messages());

        verify(renderer).openClaimMenu(player, menu);
        verify(player, atLeastOnce()).sendMessage(any(Component.class));
    }

    private static ClaimMenu menu() {
        return new ClaimMenu(
                "Home",
                "PLAYER",
                3,
                1,
                2,
                true,
                false,
                List.of(
                        new ClaimMenuAction("Flags", "/claim flags"),
                        new ClaimMenuAction("Info", "/claim info")
                )
        );
    }

    private static ClaimFlagEditor editor() {
        return new ClaimFlagEditor(
                "Home",
                List.of(new ClaimFlagEditorRow(
                        "build",
                        "Build",
                        "Access",
                        "Allow block placement.",
                        "ALLOWED",
                        "DENIED",
                        "/claim flag cycle build"
                ))
        );
    }

    private static MessageService messages() {
        return new MessageService(Map.ofEntries(
                Map.entry("claim.menu.title", "<gold><claim_name>"),
                Map.entry("claim.menu.owner-type", "<gray><owner_type>"),
                Map.entry("claim.menu.chunks", "<gray><chunk_count>"),
                Map.entry("claim.menu.members", "<gray><member_count>"),
                Map.entry("claim.menu.flags", "<gray><flag_count>"),
                Map.entry("claim.menu.viewer-owner", "<gray><is_owner>"),
                Map.entry("claim.menu.admin-claim", "<gray>Admin"),
                Map.entry("claim.menu.actions-header", "<gray>Actions"),
                Map.entry("claim.menu.action", "<yellow><label>: <command>"),
                Map.entry("claim.flag-editor.title", "<gold><claim_name>"),
                Map.entry("claim.flag-editor.row", "<yellow><label> <state>")
        ));
    }
}
