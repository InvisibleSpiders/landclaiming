package com.nick.landclaims.plugin.flag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nick.landclaims.api.flag.ClaimFlagDefinition;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FlagRegistryTest {
    @Test
    void defaultRegistryContainsMvpFlagsWithLockedDownDefaults() {
        FlagRegistry registry = FlagRegistry.createDefault();

        assertThat(registry.keys()).containsExactlyInAnyOrder(
                "build",
                "break",
                "interact",
                "container_access",
                "door_access",
                "switch_access",
                "redstone_access",
                "piston_protection",
                "fluid_flow",
                "explosion_damage",
                "fire_spread",
                "mob_griefing",
                "crop_trample",
                "entity_damage",
                "item_pickup",
                "item_drop"
        );
        assertThat(registry.defaultValue("build")).isFalse();
        assertThat(registry.defaultValue("container_access")).isFalse();
        assertThat(registry.defaultValue("piston_protection")).isTrue();
        assertThat(registry.defaultValue("unknown")).isFalse();
    }

    @Test
    void definitionReturnsRegisteredDefinition() {
        FlagRegistry registry = FlagRegistry.createDefault();

        assertThat(registry.definition("piston_protection"))
                .isPresent()
                .get()
                .satisfies(definition -> {
                    assertThat(definition.key()).isEqualTo("piston_protection");
                    assertThat(definition.defaultValue()).isTrue();
                });
    }

    @Test
    void rejectsDuplicateFlagKeys() {
        ClaimFlagDefinition first = flag("custom_flag", false);
        ClaimFlagDefinition duplicate = flag("custom_flag", true);

        assertThatThrownBy(() -> new FlagRegistry(Set.of(first, duplicate)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposedCollectionsCannotMutateRegistryInternals() {
        FlagRegistry registry = FlagRegistry.createDefault();

        assertThatThrownBy(() -> registry.keys().add("custom_flag"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> registry.definitions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(registry.keys()).contains("build");
        assertThat(registry.keys()).doesNotContain("custom_flag");
        assertThat(registry.keys()).isInstanceOf(Set.class);
    }

    private static ClaimFlagDefinition flag(String key, boolean defaultValue) {
        return new ClaimFlagDefinition(
                key,
                "custom",
                defaultValue,
                "landclaims.flag." + key
        );
    }
}
