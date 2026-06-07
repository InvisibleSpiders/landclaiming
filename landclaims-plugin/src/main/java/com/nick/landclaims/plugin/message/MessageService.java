package com.nick.landclaims.plugin.message;

import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class MessageService {
    private static final String MISSING_MESSAGE_PREFIX = "<red>Missing message: ";
    private static final String MISSING_MESSAGE_SUFFIX = "</red>";

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

        String template = messages.getOrDefault(key, MISSING_MESSAGE_PREFIX + key + MISSING_MESSAGE_SUFFIX);
        String rendered = template;
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            String token = "<" + placeholder.getKey() + ">";
            rendered = rendered.replace(token, Objects.requireNonNull(placeholder.getValue(), placeholder.getKey()));
        }
        return miniMessage.deserialize(rendered);
    }

    public String renderPlain(String key, Map<String, String> placeholders) {
        return plainTextSerializer.serialize(render(key, placeholders));
    }
}
