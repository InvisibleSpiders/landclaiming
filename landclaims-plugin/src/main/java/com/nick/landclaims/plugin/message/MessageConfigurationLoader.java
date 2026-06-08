package com.nick.landclaims.plugin.message;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

public final class MessageConfigurationLoader {
    private MessageConfigurationLoader() {
    }

    public static Map<String, String> load(ConfigurationSection section) {
        Objects.requireNonNull(section, "section");
        Map<String, String> messages = new HashMap<>();
        flatten(section, "", messages);
        return Map.copyOf(messages);
    }

    private static void flatten(ConfigurationSection section, String prefix, Map<String, String> messages) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (section.isConfigurationSection(key)) {
                flatten(Objects.requireNonNull(section.getConfigurationSection(key)), path, messages);
                continue;
            }
            if (section.isString(key)) {
                messages.put(path, section.getString(key, ""));
            }
        }
    }
}
