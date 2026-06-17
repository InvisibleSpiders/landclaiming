package com.invisiblespiders.havenclaims.plugin.upgrade;

import static org.assertj.core.api.Assertions.assertThat;

import dev.invisiblespiders.haven.api.upgrade.UpgradeDefinition;
import dev.invisiblespiders.haven.api.upgrade.UpgradeEffect;
import dev.invisiblespiders.haven.api.upgrade.UpgradeLevel;
import dev.invisiblespiders.haven.api.upgrade.UpgradeScope;
import dev.invisiblespiders.haven.api.upgrade.UpgradeVisibility;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class HavenClaimsUpgradeConfigTest {
    @Test
    void parsesClaimLimitTrack() {
        HavenClaimsUpgradeConfig config = HavenClaimsUpgradeConfig.from(yaml("""
                categories:
                  claims:
                    name: "Claims"
                    icon: "GRASS_BLOCK"
                    sort: 40
                upgrades:
                  claim-limit:
                    category: "claims"
                    permission: "havenclaims.upgrades.claim-limit"
                    scope: "PLAYER"
                    visibility: "VISIBLE"
                    levels:
                      1:
                        name: "Claim Limit I"
                        description:
                          - "<gray>Adds 1,280 claim blocks."
                        requirements:
                          - type: "money"
                            amount: "500"
                        effects:
                          - type: "claim-limit"
                            blocks: "1280"
                """));

        assertThat(config.categories()).hasSize(1);
        assertThat(config.categories().getFirst().id()).isEqualTo("claims");
        assertThat(config.categories().getFirst().displayName()).isEqualTo("Claims");
        assertThat(config.categories().getFirst().icon()).isEqualTo("GRASS_BLOCK");
        assertThat(config.categories().getFirst().sortOrder()).isEqualTo(40);

        assertThat(config.definitions()).hasSize(1);
        UpgradeDefinition definition = config.definitions().getFirst();
        assertThat(definition.id()).isEqualTo("havenclaims:claim-limit");
        assertThat(definition.providerId()).isEqualTo("havenclaims");
        assertThat(definition.category().id()).isEqualTo("claims");
        assertThat(definition.permission()).isEqualTo("havenclaims.upgrades.claim-limit");
        assertThat(definition.scope()).isEqualTo(UpgradeScope.PLAYER);
        assertThat(definition.visibility()).isEqualTo(UpgradeVisibility.VISIBLE);

        UpgradeLevel level = definition.levels().getFirst();
        assertThat(level.level()).isEqualTo(1);
        assertThat(level.displayName()).isEqualTo("Claim Limit I");
        assertThat(level.metadata()).containsEntry("description.0", "<gray>Adds 1,280 claim blocks.");
        assertThat(level.requirements()).hasSize(1);
        assertThat(level.requirements().getFirst().type()).isEqualTo("money");
        assertThat(level.effects()).extracting(UpgradeEffect::type).containsExactly("claim-limit");
    }

    private static YamlConfiguration yaml(String contents) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(contents);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception);
        }
        return configuration;
    }
}
