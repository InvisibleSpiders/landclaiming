package com.nick.landclaims.plugin.message;

import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class MessageService {
    private static final String MISSING_MESSAGE_PREFIX = "Missing message: ";

    private final Map<String, String> messages;
    private final MiniMessage miniMessage;
    private final PlainTextComponentSerializer plainTextSerializer;

    public MessageService(Map<String, String> messages) {
        this.messages = Map.copyOf(Objects.requireNonNull(messages, "messages"));
        this.miniMessage = MiniMessage.miniMessage();
        this.plainTextSerializer = PlainTextComponentSerializer.plainText();
    }

    public Component render(String key, Map<String, String> placeholders) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");

        String template = messages.get(key);
        if (template == null) {
            return Component.text(MISSING_MESSAGE_PREFIX + key, NamedTextColor.RED);
        }
        return miniMessage.deserialize(template, placeholderResolver(placeholders));
    }

    public String renderPlain(String key, Map<String, String> placeholders) {
        return plainTextSerializer.serialize(render(key, placeholders));
    }

    private static TagResolver placeholderResolver(Map<String, String> placeholders) {
        TagResolver.Builder resolver = TagResolver.builder();
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            String placeholderKey = Objects.requireNonNull(placeholder.getKey(), "placeholder key");
            String placeholderValue = Objects.requireNonNull(placeholder.getValue(), placeholderKey);
            resolver.resolver(Placeholder.unparsed(placeholderKey, placeholderValue));
        }
        return resolver.build();
    }
}
