package com.nick.landclaims.plugin.flag;

import com.nick.landclaims.api.flag.ClaimFlagDefinition;
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
                flag("build", "access", false),
                flag("break", "access", false),
                flag("interact", "access", false),
                flag("container_access", "access", false),
                flag("door_access", "access", false),
                flag("switch_access", "access", false),
                flag("redstone_access", "access", false),
                flag("piston_protection", "protection", true),
                flag("fluid_flow", "environment", false),
                flag("explosion_damage", "environment", false),
                flag("fire_spread", "environment", false),
                flag("mob_griefing", "environment", false),
                flag("crop_trample", "entity", false),
                flag("entity_damage", "entity", false),
                flag("item_pickup", "item", false),
                flag("item_drop", "item", false)
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

    private static ClaimFlagDefinition flag(String key, String category, boolean defaultValue) {
        return new ClaimFlagDefinition(
                key,
                category,
                defaultValue,
                "landclaims.flag." + key
        );
    }
}
