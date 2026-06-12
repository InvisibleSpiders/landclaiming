package com.nick.landclaims.plugin.flag;

import com.nick.landclaims.api.flag.ClaimFlagDefinition;
import com.nick.landclaims.api.flag.FlagKind;
import com.nick.landclaims.api.flag.FlagState;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class FlagRegistry {
    private final Map<String, ClaimFlagDefinition> definitions;

    public FlagRegistry(Collection<ClaimFlagDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        Map<String, ClaimFlagDefinition> byKey = new LinkedHashMap<>();
        for (ClaimFlagDefinition definition : definitions) {
            ClaimFlagDefinition nonNullDefinition = Objects.requireNonNull(definition, "definition");
            ClaimFlagDefinition previous = byKey.putIfAbsent(nonNullDefinition.key(), nonNullDefinition);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate flag key: " + nonNullDefinition.key());
            }
        }
        this.definitions = Map.copyOf(byKey);
    }

    public static FlagRegistry createDefault() {
        return new FlagRegistry(Set.of(
                action("build", "Access", "Build", "Allow non-members to place blocks.", FlagState.VISITORS),
                action("break", "Access", "Break", "Allow non-members to break blocks.", FlagState.VISITORS),
                action("interact", "Access", "Interact", "Allow generic block interaction.", FlagState.VISITORS),
                action("container_access", "Access", "Containers", "Allow chest, barrel, furnace, and hopper access.", FlagState.VISITORS),
                action("door_access", "Access", "Doors & Gates", "Allow doors, trapdoors, and fence gates.", FlagState.VISITORS),
                action("switch_access", "Access", "Switches", "Allow buttons, levers, and pressure plates.", FlagState.VISITORS),
                action("redstone_access", "Access", "Redstone Use", "Allow repeater and comparator interaction.", FlagState.VISITORS),
                action("entity_damage", "Entity", "Entity Damage", "Allow non-members to damage entities here.", FlagState.ALL),
                action("crop_trample", "Entity", "Crop Trample", "Allow farmland trampling in this claim.", FlagState.ALL),
                action("item_pickup", "Items", "Item Pickup", "Allow non-members to pick up items here.", FlagState.ALL),
                action("item_drop", "Items", "Item Drop", "Allow non-members to drop items here.", FlagState.ALL),
                world("piston_protection", "Protection", "Piston Protection", "Block piston movement touching this claim.", FlagState.ALL),
                world("fluid_flow", "Environment", "Fluid Flow", "Allow water and lava to flow into this claim.", FlagState.OFF),
                world("explosion_damage", "Environment", "Explosion Damage", "Allow explosions to damage claimed blocks.", FlagState.OFF),
                world("fire_spread", "Environment", "Fire Spread", "Allow fire to spread into this claim.", FlagState.OFF),
                world("mob_griefing", "Environment", "Mob Griefing", "Allow entity block changes in this claim.", FlagState.OFF),
                world("remove_hostile_entities", "Entity Control", "Remove Hostiles", "Removes hostile mobs unless named or tamed.", FlagState.OFF),
                world("remove_passive_entities", "Entity Control", "Remove Passives", "Removes passive mobs unless named or tamed.", FlagState.OFF)
        ));
    }

    public Optional<ClaimFlagDefinition> definition(String key) {
        return Optional.ofNullable(definitions.get(key));
    }

    public FlagState defaultState(String key) {
        return definition(key)
                .map(ClaimFlagDefinition::defaultState)
                .orElse(FlagState.OFF);
    }

    public Set<String> keys() {
        return definitions.keySet();
    }

    public Collection<ClaimFlagDefinition> definitions() {
        return definitions.values();
    }

    private static ClaimFlagDefinition action(
            String key, String category, String label, String description, FlagState defaultState) {
        return define(key, category, label, description, FlagKind.PLAYER_ACTION, true, defaultState);
    }

    private static ClaimFlagDefinition world(
            String key, String category, String label, String description, FlagState defaultState) {
        return define(key, category, label, description, FlagKind.WORLD_EFFECT, false, defaultState);
    }

    private static ClaimFlagDefinition define(
            String key, String category, String label, String description,
            FlagKind kind, boolean ownerExempt, FlagState defaultState) {
        return new ClaimFlagDefinition(
                key, category, label, description, kind, ownerExempt, defaultState,
                "landclaims.flag." + key);
    }
}
