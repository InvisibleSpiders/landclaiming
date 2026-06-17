package com.invisiblespiders.havenclaims.plugin.upgrade;

import dev.invisiblespiders.haven.api.upgrade.UpgradeContext;
import dev.invisiblespiders.haven.api.upgrade.UpgradeCategory;
import dev.invisiblespiders.haven.api.upgrade.UpgradeDefinition;
import dev.invisiblespiders.haven.api.upgrade.UpgradeEffect;
import dev.invisiblespiders.haven.api.upgrade.UpgradeLevel;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirement;
import dev.invisiblespiders.haven.api.upgrade.UpgradeRequirementResult;
import dev.invisiblespiders.haven.api.upgrade.UpgradeScope;
import dev.invisiblespiders.haven.api.upgrade.UpgradeVisibility;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

public record HavenClaimsUpgradeConfig(
        List<UpgradeCategory> categories,
        List<UpgradeDefinition> definitions
) {
    public static final String PROVIDER_ID = "havenclaims";
    private static final Pattern SIMPLE_ID = Pattern.compile("[a-z0-9][a-z0-9_-]*");

    public HavenClaimsUpgradeConfig {
        categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
        definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions"));
    }

    public static HavenClaimsUpgradeConfig from(ConfigurationSection root) {
        Objects.requireNonNull(root, "root");
        Map<String, UpgradeCategory> categories = parseCategories(root.getConfigurationSection("categories"));
        List<UpgradeDefinition> definitions = parseDefinitions(root.getConfigurationSection("upgrades"), categories);
        return new HavenClaimsUpgradeConfig(
                List.copyOf(categories.values()),
                definitions
        );
    }

    private static Map<String, UpgradeCategory> parseCategories(ConfigurationSection section) {
        Map<String, UpgradeCategory> categories = new LinkedHashMap<>();
        if (section == null) {
            return categories;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection category = requiredSection(section, id);
            categories.put(id, new UpgradeCategory(
                    id,
                    category.getString("name", id),
                    category.getString("icon", "GRASS_BLOCK"),
                    category.getInt("sort", 0)
            ));
        }
        return categories;
    }

    private static List<UpgradeDefinition> parseDefinitions(
            ConfigurationSection section,
            Map<String, UpgradeCategory> categories
    ) {
        if (section == null) {
            return List.of();
        }
        List<UpgradeDefinition> definitions = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection upgrade = requiredSection(section, id);
            String categoryId = upgrade.getString("category", "claims");
            UpgradeCategory category = categories.computeIfAbsent(categoryId,
                    key -> new UpgradeCategory(key, key, "GRASS_BLOCK", 0));
            definitions.add(new UpgradeDefinition(
                    namespaced(id),
                    PROVIDER_ID,
                    category,
                    enumValue(UpgradeScope.class, upgrade.getString("scope", "PLAYER")),
                    enumValue(UpgradeVisibility.class, upgrade.getString("visibility", "VISIBLE")),
                    blankToNull(upgrade.getString("permission")),
                    parseLevels(requiredSection(upgrade, "levels"))
            ));
        }
        return List.copyOf(definitions);
    }

    private static List<UpgradeLevel> parseLevels(ConfigurationSection section) {
        return section.getKeys(false).stream()
                .sorted(Comparator.comparingInt(HavenClaimsUpgradeConfig::parseLevelNumber))
                .map(levelKey -> parseLevel(parseLevelNumber(levelKey), requiredSection(section, levelKey)))
                .toList();
    }

    private static UpgradeLevel parseLevel(int level, ConfigurationSection section) {
        return new UpgradeLevel(
                level,
                section.getString("name", "Level " + level),
                parseRequirements(section.getMapList("requirements")),
                parseEffects(section.getMapList("effects")),
                parseMetadata(section)
        );
    }

    private static List<UpgradeRequirement> parseRequirements(List<Map<?, ?>> entries) {
        return entries.stream()
                .map(HavenClaimsUpgradeConfig::stringMap)
                .map(values -> new ConfiguredRequirement(requiredType(values), withoutType(values)))
                .map(UpgradeRequirement.class::cast)
                .toList();
    }

    private static List<UpgradeEffect> parseEffects(List<Map<?, ?>> entries) {
        return entries.stream()
                .map(HavenClaimsUpgradeConfig::stringMap)
                .map(values -> new ConfiguredEffect(requiredType(values), withoutType(values)))
                .map(UpgradeEffect.class::cast)
                .toList();
    }

    private static Map<String, String> parseMetadata(ConfigurationSection section) {
        Map<String, String> metadata = new LinkedHashMap<>();
        List<String> description = section.getStringList("description");
        for (int i = 0; i < description.size(); i++) {
            metadata.put("description." + i, description.get(i));
        }
        return metadata;
    }

    private static Map<String, String> stringMap(Map<?, ?> source) {
        Map<String, String> values = new LinkedHashMap<>();
        source.forEach((key, value) -> values.put(String.valueOf(key), String.valueOf(value)));
        return values;
    }

    private static String requiredType(Map<String, String> values) {
        String type = values.get("type");
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Upgrade requirement/effect is missing type.");
        }
        return type;
    }

    private static Map<String, String> withoutType(Map<String, String> values) {
        Map<String, String> copy = new LinkedHashMap<>(values);
        copy.remove("type");
        return Map.copyOf(copy);
    }

    private static String namespaced(String id) {
        String simpleId = id;
        int namespaceSeparator = id.indexOf(':');
        if (namespaceSeparator >= 0) {
            String namespace = id.substring(0, namespaceSeparator);
            simpleId = id.substring(namespaceSeparator + 1);
            if (!PROVIDER_ID.equals(namespace)) {
                throw new IllegalArgumentException("Upgrade id must use havenclaims namespace: " + id);
            }
            if (simpleId.contains(":")) {
                throw new IllegalArgumentException("Upgrade id has invalid syntax: " + id);
            }
        }
        if (!SIMPLE_ID.matcher(simpleId).matches()) {
            throw new IllegalArgumentException("Upgrade id has invalid syntax: " + id);
        }
        return PROVIDER_ID + ":" + simpleId;
    }

    private static int parseLevelNumber(String levelKey) {
        try {
            return Integer.parseInt(levelKey);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Upgrade level must be numeric: " + levelKey, exception);
        }
    }

    private static ConfigurationSection requiredSection(ConfigurationSection parent, String key) {
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section == null) {
            throw new IllegalArgumentException("Missing upgrade config section: " + parent.getCurrentPath() + "." + key);
        }
        return section;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return Enum.valueOf(type, value.toUpperCase(java.util.Locale.ROOT));
    }

    record ConfiguredRequirement(String type, Map<String, String> values) implements UpgradeRequirement {
        ConfiguredRequirement {
            Objects.requireNonNull(type, "type");
            values = Map.copyOf(Objects.requireNonNull(values, "values"));
        }

        @Override
        public UpgradeRequirementResult validate(UpgradeContext context) {
            return UpgradeRequirementResult.success();
        }

        @Override
        public void consume(UpgradeContext context) {
        }

        @Override
        public void refund(UpgradeContext context) {
        }
    }

    record ConfiguredEffect(String type, Map<String, String> values) implements UpgradeEffect {
        ConfiguredEffect {
            Objects.requireNonNull(type, "type");
            values = Map.copyOf(Objects.requireNonNull(values, "values"));
        }

        @Override
        public void apply(UpgradeContext context) {
        }

        @Override
        public void rollback(UpgradeContext context) {
        }
    }
}
