package com.invisiblespiders.havenclaims.plugin.flag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.invisiblespiders.havenclaims.api.flag.ClaimFlagDefinition;
import com.invisiblespiders.havenclaims.api.flag.FlagKind;
import com.invisiblespiders.havenclaims.api.flag.FlagState;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FlagRegistryTest {
    private final FlagRegistry registry = FlagRegistry.createDefault();

    @Test
    void containerAccessIsPlayerActionOwnerExemptVisitorsDefault() {
        ClaimFlagDefinition def = registry.definition("container_access").orElseThrow();
        assertEquals(FlagKind.PLAYER_ACTION, def.kind());
        assertTrue(def.ownerExempt());
        assertEquals(FlagState.VISITORS, def.defaultState());
    }

    @Test
    void entityDamageDefaultsToAll() {
        assertEquals(FlagState.ALL, registry.definition("entity_damage").orElseThrow().defaultState());
    }

    @Test
    void explosionDamageIsWorldEffectNotOwnerExemptOffDefault() {
        ClaimFlagDefinition def = registry.definition("explosion_damage").orElseThrow();
        assertEquals(FlagKind.WORLD_EFFECT, def.kind());
        assertFalse(def.ownerExempt());
        assertEquals(FlagState.OFF, def.defaultState());
    }

    @Test
    void pistonProtectionDefaultsToAll() {
        assertEquals(FlagState.ALL, registry.definition("piston_protection").orElseThrow().defaultState());
    }

    @Test
    void defaultStateLookupFallsBackToOff() {
        assertEquals(FlagState.OFF, registry.defaultState("nonexistent_flag"));
    }

    @Test
    void defaultRegistryContainsAllExpectedFlags() {
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
                "remove_hostile_entities",
                "remove_passive_entities",
                "item_pickup",
                "item_drop"
        );
    }

    @Test
    void definitionReturnsRegisteredDefinition() {
        assertThat(registry.definition("piston_protection"))
                .isPresent()
                .get()
                .satisfies(definition -> {
                    assertThat(definition.key()).isEqualTo("piston_protection");
                    assertThat(definition.label()).isEqualTo("Piston Protection");
                    assertThat(definition.description()).contains("piston");
                    assertThat(definition.kind()).isEqualTo(FlagKind.WORLD_EFFECT);
                    assertThat(definition.defaultState()).isEqualTo(FlagState.ALL);
                });
    }

    @Test
    void rejectsDuplicateFlagKeys() {
        ClaimFlagDefinition first = flag("custom_flag");
        ClaimFlagDefinition duplicate = flag("custom_flag");

        assertThatThrownBy(() -> new FlagRegistry(List.of(first, duplicate)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposedCollectionsCannotMutateRegistryInternals() {
        assertThatThrownBy(() -> registry.keys().add("custom_flag"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> registry.definitions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(registry.keys()).contains("build");
        assertThat(registry.keys()).doesNotContain("custom_flag");
        assertThat(registry.keys()).isInstanceOf(Set.class);
    }

    private static ClaimFlagDefinition flag(String key) {
        return new ClaimFlagDefinition(
                key,
                "custom",
                key,
                "",
                FlagKind.PLAYER_ACTION,
                true,
                FlagState.OFF,
                "havenclaims.flag." + key
        );
    }
}
