package com.nick.landclaims.plugin.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.nick.landclaims.api.limit.LandClaimsLimitService;
import com.nick.landclaims.plugin.message.MessageService;
import com.nick.landclaims.plugin.tool.ClaimToolService;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ClaimsCommandManageTest {
    @Test
    void renameTabCompletionInRootSuggestions() {
        ClaimsCommand command = command();

        assertThat(command.onTabComplete(mock(Player.class), mock(Command.class), "claim", new String[]{"ren"}))
                .contains("rename");
    }

    @Test
    void deleteTabCompletionInRootSuggestions() {
        ClaimsCommand command = command();

        assertThat(command.onTabComplete(mock(Player.class), mock(Command.class), "claim", new String[]{"del"}))
                .contains("delete");
    }

    private static ClaimsCommand command() {
        return new ClaimsCommand(
                mock(ClaimToolService.class),
                null,
                null,
                null,
                null,
                null,
                null,
                new MessageService(Map.of()),
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
                mock(LandClaimsLimitService.class),
                null,
                null,
                null
        );
    }
}
