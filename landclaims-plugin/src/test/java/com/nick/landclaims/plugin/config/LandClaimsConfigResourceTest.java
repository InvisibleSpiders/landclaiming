package com.nick.landclaims.plugin.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LandClaimsConfigResourceTest {
    @Test
    void borderDurationDefaultsToFiveSeconds() throws Exception {
        try (InputStream input = LandClaimsConfigResourceTest.class
                .getClassLoader()
                .getResourceAsStream("config.yml")) {
            assertThat(input).isNotNull();
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8));

            assertThat(configuration.getInt("visuals.border.duration-ticks")).isEqualTo(100);
        }
    }
}
