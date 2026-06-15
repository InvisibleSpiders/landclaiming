package com.invisiblespiders.havenclaims.plugin.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MessageConfigurationLoaderTest {
    @Test
    void flattensNestedMessageConfigurationSections() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("claim.created", "<green>Created <claim_name>.");
        configuration.set("command.claims.help.title", "<gold>LandClaims commands");
        configuration.set("tool.lore", java.util.List.of("Line one", "Line two"));

        Map<String, String> messages = MessageConfigurationLoader.load(configuration);

        assertThat(messages)
                .containsEntry("claim.created", "<green>Created <claim_name>.")
                .containsEntry("command.claims.help.title", "<gold>LandClaims commands");
        assertThat(messages).doesNotContainKey("tool.lore");
    }
}
