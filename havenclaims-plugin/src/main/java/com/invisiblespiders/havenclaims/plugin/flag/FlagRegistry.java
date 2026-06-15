package com.invisiblespiders.havenclaims.plugin.flag;

import com.invisiblespiders.havenclaims.api.flag.ClaimFlagDefinition;
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
                flag("build", "Access", "Build", "Allow non-members to place blocks.", false),
                flag("break", "Access", "Break", "Allow non-members to break blocks.", false),
                flag("interact", "Access", "Interact", "Allow generic block interaction.", false),
                flag("container_access", "Access", "Containers", "Allow chest, barrel, furnace, and hopper access.", false),
                flag("door_access", "Access", "Doors & Gates", "Allow doors, trapdoors, and fence gates.", false),
                flag("switch_access", "Access", "Switches", "Allow buttons, levers, and pressure plates.", false),
                flag("redstone_access", "Access", "Redstone Use", "Allow repeater and comparator interaction.", false),
                flag("piston_protection", "Protection", "Piston Protection", "Block piston movement touching this claim.", true),
                flag("fluid_flow", "Environment", "Fluid Flow", "Allow water and lava to flow into this claim.", false),
                flag("explosion_damage", "Environment", "Explosion Damage", "Allow explosions to damage claimed blocks.", false),
                flag("fire_spread", "Environment", "Fire Spread", "Allow fire to spread into this claim.", false),
                flag("mob_griefing", "Environment", "Mob Griefing", "Allow entity block changes in this claim.", false),
                flag("crop_trample", "Entity", "Crop Trample", "Allow farmland trampling in this claim.", false),
                flag("entity_damage", "Entity", "Entity Damage", "Allow non-members to damage entities here.", false),
                flag("remove_hostile_entities", "Entity Control", "Remove Hostiles", "Removes hostile mobs unless named or tamed.", false),
                flag("remove_passive_entities", "Entity Control", "Remove Passives", "Removes passive mobs unless named or tamed.", false),
                flag("item_pickup", "Items", "Item Pickup", "Allow non-members to pick up items here.", false),
                flag("item_drop", "Items", "Item Drop", "Allow non-members to drop items here.", false)
        ));
    }

    public Optional<ClaimFlagDefinition> definition(String key) {
        return Optional.ofNullable(definitions.get(key));
    }

    public boolean defaultValue(String key) {
        return definition(key)
                .map(ClaimFlagDefinition::defaultValue)
                .orElse(false);
    }

    public Set<String> keys() {
        return definitions.keySet();
    }

    public Collection<ClaimFlagDefinition> definitions() {
        return definitions.values();
    }

    private static ClaimFlagDefinition flag(String key, String category, String label, String description, boolean defaultValue) {
        return new ClaimFlagDefinition(
                key,
                category,
                label,
                description,
                defaultValue,
                "havenclaims.flag." + key
        );
    }
}
