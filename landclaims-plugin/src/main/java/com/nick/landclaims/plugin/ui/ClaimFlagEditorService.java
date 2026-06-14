package com.nick.landclaims.plugin.ui;

import com.nick.landclaims.api.flag.FlagState;
import com.nick.landclaims.plugin.flag.ClaimFlagRow;
import com.nick.landclaims.plugin.message.MessageService;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ClaimFlagEditorService {
    private final MessageService messageService;

    public ClaimFlagEditorService(MessageService messageService) {
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    public ClaimFlagEditor buildEditor(String claimName, List<ClaimFlagRow> flags) {
        Objects.requireNonNull(claimName, "claimName");
        Objects.requireNonNull(flags, "flags");

        return new ClaimFlagEditor(
                claimName,
                flags.stream()
                        .map(this::toEditorRow)
                        .toList()
        );
    }

    private ClaimFlagEditorRow toEditorRow(ClaimFlagRow flag) {
        Map<String, String> placeholders = Map.of(
                "flag", flag.key(),
                "category", flag.category(),
                "label", flag.label(),
                "description", flag.description()
        );
        String label = messageService.renderPlainOrDefault(
                "claim.flag-editor.flag-labels." + flag.key(),
                placeholders,
                flag.label());
        String description = messageService.renderPlainOrDefault(
                "claim.flag-editor.flag-descriptions." + flag.key(),
                placeholders,
                flag.description());
        return new ClaimFlagEditorRow(
                flag.key(),
                flag.category(),
                label,
                description,
                stateLabel(flag, flag.state()),
                stateLabel(flag, flag.kind().next(flag.state())),
                "/claim flag cycle " + flag.key()
        );
    }

    private String stateLabel(ClaimFlagRow flag, FlagState state) {
        boolean enabled = state != FlagState.OFF;
        if (flag.key().startsWith("remove_") && flag.category().equalsIgnoreCase("Entity Control")) {
            return stateLabel("entity-control", enabled);
        }
        if (flag.category().equalsIgnoreCase("Access")
                || flag.category().equalsIgnoreCase("Items")
                || flag.category().equalsIgnoreCase("Entity")) {
            return stateLabel("access", enabled);
        }
        if (flag.category().equalsIgnoreCase("Environment")) {
            return stateLabel("environment", enabled);
        }
        if (flag.category().equalsIgnoreCase("Protection")) {
            return stateLabel("protection", enabled);
        }
        return stateLabel("generic", enabled);
    }

    private String stateLabel(String group, boolean enabled) {
        String stateKey = enabled ? "enabled" : "disabled";
        return messageService.renderPlain("claim.flag-editor.state-labels." + group + "." + stateKey, Map.of());
    }
}
