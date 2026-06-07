package com.nick.landclaims.plugin.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MessageServiceTest {
    @Test
    void renderPlainReplacesPlaceholdersBeforeMiniMessageTagsAreParsed() {
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
}
