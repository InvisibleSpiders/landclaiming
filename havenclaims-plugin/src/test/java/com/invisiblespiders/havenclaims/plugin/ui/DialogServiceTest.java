package com.invisiblespiders.havenclaims.plugin.ui;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invisiblespiders.havenclaims.plugin.message.MessageService;
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
        MessageService messages = messages();
        when(renderer.openClaimMenu(player, menu, messages)).thenReturn(true);
        DialogService service = new DialogService(true, renderer);

        service.openClaimMenu(player, menu, messages);

        verify(renderer).openClaimMenu(player, menu, messages);
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    void opensFlagEditorWithDialogRendererWhenPreferred() {
        Player player = mock(Player.class);
        DialogService.DialogRenderer renderer = mock(DialogService.DialogRenderer.class);
        ClaimFlagEditor editor = editor();
        MessageService messages = messages();
        when(renderer.openFlagEditor(player, editor, messages)).thenReturn(true);
        DialogService service = new DialogService(true, renderer);

        service.openFlagEditor(player, editor, messages);

        verify(renderer).openFlagEditor(player, editor, messages);
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    void opensDashboardWithDialogRendererWhenPreferred() {
        Player player = mock(Player.class);
        DialogService.DialogRenderer renderer = mock(DialogService.DialogRenderer.class);
        ClaimDashboard dashboard = dashboard();
        MessageService messages = messages();
        when(renderer.openClaimDashboard(player, dashboard, messages)).thenReturn(true);
        DialogService service = new DialogService(true, renderer);

        service.openClaimDashboard(player, dashboard, messages);

        verify(renderer).openClaimDashboard(player, dashboard, messages);
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    void opensClaimInfoWithDialogRendererWhenPreferred() {
        Player player = mock(Player.class);
        DialogService.DialogRenderer renderer = mock(DialogService.DialogRenderer.class);
        ClaimInfoView info = info();
        MessageService messages = messages();
        when(renderer.openClaimInfo(player, info, messages)).thenReturn(true);
        DialogService service = new DialogService(true, renderer);

        service.openClaimInfo(player, info, messages);

        verify(renderer).openClaimInfo(player, info, messages);
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    void opensClaimMembersWithDialogRendererWhenPreferred() {
        Player player = mock(Player.class);
        DialogService.DialogRenderer renderer = mock(DialogService.DialogRenderer.class);
        ClaimMembersView members = members();
        MessageService messages = messages();
        when(renderer.openClaimMembers(player, members, messages)).thenReturn(true);
        DialogService service = new DialogService(true, renderer);

        service.openClaimMembers(player, members, messages);

        verify(renderer).openClaimMembers(player, members, messages);
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    void opensDeniedPlayersWithDialogRendererWhenPreferred() {
        Player player = mock(Player.class);
        DialogService.DialogRenderer renderer = mock(DialogService.DialogRenderer.class);
        ClaimDeniedPlayersView denied = deniedPlayers();
        MessageService messages = messages();
        when(renderer.openDeniedPlayers(player, denied, messages)).thenReturn(true);
        DialogService service = new DialogService(true, renderer);

        service.openDeniedPlayers(player, denied, messages);

        verify(renderer).openDeniedPlayers(player, denied, messages);
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    void opensClaimCreatePreviewWithDialogRendererWhenPreferred() {
        Player player = mock(Player.class);
        DialogService.DialogRenderer renderer = mock(DialogService.DialogRenderer.class);
        ClaimCreatePreview preview = createPreview();
        MessageService messages = messages();
        when(renderer.openClaimCreatePreview(player, preview, messages)).thenReturn(true);
        DialogService service = new DialogService(true, renderer);

        service.openClaimCreatePreview(player, preview, messages);

        verify(renderer).openClaimCreatePreview(player, preview, messages);
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    void fallsBackToChatWhenDialogsAreNotPreferred() {
        Player player = mock(Player.class);
        DialogService.DialogRenderer renderer = mock(DialogService.DialogRenderer.class);
        DialogService service = new DialogService(false, renderer);

        service.openClaimMenu(player, menu(), messages());

        verify(renderer, never()).openClaimMenu(any(Player.class), any(ClaimMenu.class), any(MessageService.class));
        verify(player, atLeastOnce()).sendMessage(any(Component.class));
    }

    @Test
    void fallsBackToChatWhenPreferredDialogRendererCannotOpen() {
        Player player = mock(Player.class);
        DialogService.DialogRenderer renderer = mock(DialogService.DialogRenderer.class);
        ClaimMenu menu = menu();
        MessageService messages = messages();
        when(renderer.openClaimMenu(player, menu, messages)).thenReturn(false);
        DialogService service = new DialogService(true, renderer);

        service.openClaimMenu(player, menu, messages);

        verify(renderer).openClaimMenu(player, menu, messages);
        verify(player, atLeastOnce()).sendMessage(any(Component.class));
    }

    @Test
    void dashboardChatFallbackUsesDefaultsWhenMessagesAreMissing() {
        Player player = mock(Player.class);
        List<Component> sentMessages = new java.util.ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            sentMessages.add(invocation.getArgument(0));
            return null;
        }).when(player).sendMessage(any(Component.class));
        DialogService service = new DialogService(false);

        service.openClaimDashboard(player, dashboard(), new MessageService(Map.of()));

        List<String> plain = sentMessages.stream()
                .map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize)
                .toList();
        org.assertj.core.api.Assertions.assertThat(plain)
                .noneMatch(message -> message.contains("Missing message:"));
    }

    @Test
    void claimDetailChatFallbacksUseDefaultsWhenMessagesAreMissing() {
        Player player = mock(Player.class);
        List<Component> sentMessages = new java.util.ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            sentMessages.add(invocation.getArgument(0));
            return null;
        }).when(player).sendMessage(any(Component.class));
        DialogService service = new DialogService(false);

        service.openClaimInfo(player, info(), new MessageService(Map.of()));
        service.openClaimMembers(player, members(), new MessageService(Map.of()));
        service.openDeniedPlayers(player, deniedPlayers(), new MessageService(Map.of()));

        List<String> plain = sentMessages.stream()
                .map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize)
                .toList();
        org.assertj.core.api.Assertions.assertThat(plain)
                .noneMatch(message -> message.contains("Missing message:"));
    }

    @Test
    void reloadUpdatesDialogPreference() {
        Player player = mock(Player.class);
        DialogService.DialogRenderer renderer = mock(DialogService.DialogRenderer.class);
        ClaimMenu menu = menu();
        MessageService messages = messages();
        when(renderer.openClaimMenu(player, menu, messages)).thenReturn(true);
        DialogService service = new DialogService(false, renderer);

        service.openClaimMenu(player, menu, messages);
        service.reload(true);
        service.openClaimMenu(player, menu, messages);

        verify(renderer).openClaimMenu(player, menu, messages);
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

    private static ClaimDashboard dashboard() {
        return new ClaimDashboard(
                "My Claims",
                List.of(new ClaimDashboardRow(
                        java.util.UUID.randomUUID(),
                        "Home",
                        3,
                        true,
                        "/claim menu " + java.util.UUID.randomUUID()
                )),
                List.of(new ClaimMenuAction("Create Claim", "/claim create"))
        );
    }

    private static ClaimInfoView info() {
        return new ClaimInfoView(
                java.util.UUID.randomUUID(),
                "Home",
                "PLAYER",
                3,
                1,
                1,
                2,
                true,
                List.of(new ClaimMenuAction("Flags", "/claim flags")),
                new ClaimMenuAction("Back", "/claim menu")
        );
    }

    private static ClaimMembersView members() {
        return new ClaimMembersView(
                java.util.UUID.randomUUID(),
                "Home",
                List.of(new ClaimMemberViewRow("Helper", "manager")),
                List.of(new ClaimMenuAction("Info", "/claim info")),
                new ClaimMenuAction("Back", "/claim menu")
        );
    }

    private static ClaimDeniedPlayersView deniedPlayers() {
        return new ClaimDeniedPlayersView(
                java.util.UUID.randomUUID(),
                "Home",
                List.of(new ClaimDeniedPlayerViewRow("Visitor")),
                List.of(new ClaimMenuAction("Info", "/claim info")),
                new ClaimMenuAction("Back", "/claim menu")
        );
    }

    private static ClaimCreatePreview createPreview() {
        return new ClaimCreatePreview(
                "Home",
                2,
                4,
                5,
                0,
                "free",
                new ClaimMenuAction("Create Claim", "/claim createconfirm Home"),
                new ClaimMenuAction("Cancel", "/claim cancel")
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
