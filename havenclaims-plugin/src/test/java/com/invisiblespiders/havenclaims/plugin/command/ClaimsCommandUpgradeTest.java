package com.invisiblespiders.havenclaims.plugin.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.invisiblespiders.havenclaims.api.limit.HavenClaimsLimitService;
import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import com.invisiblespiders.havenclaims.plugin.tool.ClaimToolService;
import dev.invisiblespiders.haven.api.upgrade.HavenUpgradeService;
import dev.invisiblespiders.haven.api.upgrade.UpgradeViewRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ClaimsCommandUpgradeTest {
    @Test
    void upgradesCommandOpensClaimsCategoryWhenAllowed() {
        HavenUpgradeService upgradeService = mock(HavenUpgradeService.class);
        ClaimsCommand command = command(upgradeService);
        Player player = mock(Player.class);
        when(player.hasPermission("havenclaims.upgrades")).thenReturn(true);

        command.onCommand(player, mock(Command.class), "claim", new String[]{"upgrades"});

        ArgumentCaptor<UpgradeViewRequest> request = ArgumentCaptor.forClass(UpgradeViewRequest.class);
        verify(upgradeService).openDialog(any(Player.class), request.capture());
        assertThat(request.getValue().providerIds()).containsExactly("havenclaims");
        assertThat(request.getValue().categoryIds()).containsExactly("claims");
    }

    @Test
    void upgradesCommandRequiresPermissionBeforeOpeningDialog() {
        HavenUpgradeService upgradeService = mock(HavenUpgradeService.class);
        ClaimsCommand command = command(upgradeService);
        Player player = mock(Player.class);
        List<Component> messages = captureMessages(player);

        command.onCommand(player, mock(Command.class), "claim", new String[]{"upgrades"});

        assertThat(plain(messages)).containsExactly("You do not have permission to open HavenClaims upgrades.");
    }

    private static ClaimsCommand command(HavenUpgradeService upgradeService) {
        return new ClaimsCommand(
                mock(ClaimToolService.class),
                null,
                null,
                null,
                null,
                null,
                null,
                new MessageService(Map.ofEntries(
                        Map.entry("upgrades.no-permission", "<red>You do not have permission to open HavenClaims upgrades."),
                        Map.entry("upgrades.unavailable", "<red>HavenClaims upgrades are not available yet.")
                )),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                mock(HavenClaimsLimitService.class),
                null,
                null,
                null,
                () -> upgradeService
        );
    }

    private static List<Component> captureMessages(Player player) {
        List<Component> messages = new ArrayList<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            messages.add(invocation.getArgument(0));
            return null;
        }).when(player).sendMessage(any(Component.class));
        return messages;
    }

    private static List<String> plain(List<Component> messages) {
        return messages.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();
    }
}
