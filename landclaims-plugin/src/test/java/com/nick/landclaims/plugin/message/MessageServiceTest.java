package com.nick.landclaims.plugin.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageServiceTest {
    @Test
    void renderPlainReplacesPlaceholders() {
        MessageService service = new MessageService(Map.of(
                "claim.created",
                "<green>Claim <white><claim_name></white> created with <yellow><chunk_count></yellow> chunks."
        ));

        String rendered = service.renderPlain("claim.created", Map.of(
                "claim_name", "Spawn",
                "chunk_count", "9"
        ));

        assertThat(rendered).isEqualTo("Claim Spawn created with 9 chunks.");
    }

    @Test
    void renderPlainRendersPlaceholderMiniMessageTagsLiterally() {
        MessageService service = new MessageService(Map.of(
                "claim.created",
                "<green>Claim <white><claim_name></white> created."
        ));

        String rendered = service.renderPlain("claim.created", Map.of(
                "claim_name", "<red>Spawn</red>"
        ));

        assertThat(rendered).contains("<red>Spawn</red>");
    }

    @Test
    void renderPlainDoesNotCascadePlaceholderValuesContainingPlaceholderTokens() {
        MessageService service = new MessageService(Map.of(
                "claim.created",
                "Claim <claim_name> created by <owner>."
        ));
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("claim_name", "<owner>");
        placeholders.put("owner", "Nick");

        String rendered = service.renderPlain("claim.created", placeholders);

        assertThat(rendered).isEqualTo("Claim <owner> created by Nick.");
    }

    @Test
    void renderPlainMissingKeyIncludesVisibleFallbackWithMissingKey() {
        MessageService service = new MessageService(Map.of());

        String rendered = service.renderPlain("claim.missing", Map.of());

        assertThat(rendered).contains("Missing message: claim.missing");
    }

    @Test
    void renderPlainOrDefaultUsesFallbackWhenKeyIsMissing() {
        MessageService service = new MessageService(Map.of());

        String rendered = service.renderPlainOrDefault("claim.missing", Map.of(), "Fallback");

        assertThat(rendered).isEqualTo("Fallback");
    }

    @Test
    void renderPlainOrDefaultUsesConfiguredMessageWhenKeyExists() {
        MessageService service = new MessageService(Map.of("claim.label", "<yellow><label></yellow>"));

        String rendered = service.renderPlainOrDefault("claim.label", Map.of("label", "Build"), "Fallback");

        assertThat(rendered).isEqualTo("Build");
    }

    @Test
    void renderOrDefaultUsesFallbackTemplateWhenKeyIsMissing() {
        MessageService service = new MessageService(Map.of());

        String rendered = service.renderPlainText(service.renderOrDefault(
                "claim.missing",
                Map.of("label", "Build"),
                "<yellow><label></yellow>"));

        assertThat(rendered).isEqualTo("Build");
    }

    @Test
    void constructorDefensivelyCopiesMessages() {
        Map<String, String> messages = new HashMap<>();
        messages.put("claim.created", "Claim <claim_name> created.");
        MessageService service = new MessageService(messages);

        messages.put("claim.created", "Changed <claim_name>.");

        String rendered = service.renderPlain("claim.created", Map.of(
                "claim_name", "Spawn"
        ));

        assertThat(rendered).isEqualTo("Claim Spawn created.");
    }

    @Test
    void renderPlainRejectsNullPlaceholderValues() {
        MessageService service = new MessageService(Map.of(
                "claim.created",
                "Claim <claim_name> created."
        ));
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("claim_name", null);

        assertThatNullPointerException()
                .isThrownBy(() -> service.renderPlain("claim.created", placeholders));
    }

    @Test
    void reloadSwapsMessageMap() {
        MessageService service = new MessageService(Map.of("key.one", "old value"));

        service.reload(Map.of("key.one", "new value"));

        assertThat(service.renderPlain("key.one", Map.of())).isEqualTo("new value");
    }

    @Test
    void reloadWithNewKeyMakesItAvailable() {
        MessageService service = new MessageService(Map.of("key.one", "original"));

        service.reload(Map.of("key.one", "original", "key.two", "added"));

        assertThat(service.renderPlain("key.two", Map.of())).isEqualTo("added");
    }
}
