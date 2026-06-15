package com.invisiblespiders.havenclaims.plugin.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.invisiblespiders.havenclaims.api.flag.FlagKind;
import com.invisiblespiders.havenclaims.api.flag.FlagState;
import com.invisiblespiders.havenclaims.plugin.flag.ClaimFlagRow;
import com.invisiblespiders.havenclaims.plugin.message.MessageService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClaimFlagEditorServiceTest {
    @Test
    void buildsCycleRowsGroupedByFlagCategory() {
        ClaimFlagEditorService service = new ClaimFlagEditorService(messages());

        ClaimFlagEditor editor = service.buildEditor("Home", List.of(
                new ClaimFlagRow("build", "Access", "Build", "Allow block placement.", FlagKind.PLAYER_ACTION, FlagState.ALL, "havenclaims.flag.build"),
                new ClaimFlagRow("fluid_flow", "Environment", "Fluid Flow", "Allow water and lava.", FlagKind.WORLD_EFFECT, FlagState.OFF, "havenclaims.flag.fluid_flow")
        ));

        assertThat(editor.claimName()).isEqualTo("Home");
        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::toggleCommand)
                .containsExactly("/claim flag cycle build", "/claim flag cycle fluid_flow");
        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::stateLabel)
                .containsExactly("ALLOWED", "BLOCKED");
        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::nextStateLabel)
                .containsExactly("DENIED", "ALLOWED");
        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::label)
                .containsExactly("Build", "Fluid Flow");
        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::description)
                .containsExactly("Allow block placement.", "Allow water and lava.");
    }

    @Test
    void usesPurposeBuiltStateLabelsForEntityControlFlags() {
        ClaimFlagEditorService service = new ClaimFlagEditorService(messages());

        ClaimFlagEditor editor = service.buildEditor("Home", List.of(
                new ClaimFlagRow(
                        "remove_hostile_entities",
                        "Entity Control",
                        "Remove Hostiles",
                        "Removes hostile mobs unless named or tamed.",
                        FlagKind.WORLD_EFFECT,
                        FlagState.ALL,
                        "havenclaims.flag.remove_hostile_entities"
                ),
                new ClaimFlagRow(
                        "remove_passive_entities",
                        "Entity Control",
                        "Remove Passives",
                        "Removes passive mobs unless named or tamed.",
                        FlagKind.WORLD_EFFECT,
                        FlagState.OFF,
                        "havenclaims.flag.remove_passive_entities"
                )
        ));

        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::stateLabel)
                .containsExactly("REMOVING", "KEEPING");
        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::nextStateLabel)
                .containsExactly("KEEPING", "REMOVING");
    }

    @Test
    void usesConfiguredStateLabels() {
        MessageService messages = new MessageService(Map.ofEntries(
                Map.entry("claim.flag-editor.state-labels.access.enabled", "Open"),
                Map.entry("claim.flag-editor.state-labels.access.disabled", "Closed"),
                Map.entry("claim.flag-editor.state-labels.environment.enabled", "Flowing"),
                Map.entry("claim.flag-editor.state-labels.environment.disabled", "Stopped")
        ));
        ClaimFlagEditorService service = new ClaimFlagEditorService(messages);

        ClaimFlagEditor editor = service.buildEditor("Home", List.of(
                new ClaimFlagRow("build", "Access", "Build", "Allow block placement.", FlagKind.PLAYER_ACTION, FlagState.ALL, "havenclaims.flag.build"),
                new ClaimFlagRow("fluid_flow", "Environment", "Fluid Flow", "Allow water and lava.", FlagKind.WORLD_EFFECT, FlagState.OFF, "havenclaims.flag.fluid_flow")
        ));

        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::stateLabel)
                .containsExactly("Open", "Stopped");
        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::nextStateLabel)
                .containsExactly("Closed", "Flowing");
    }

    @Test
    void usesDefaultStateLabelsWhenMessagesAreMissing() {
        ClaimFlagEditorService service = new ClaimFlagEditorService(new MessageService(Map.of()));

        ClaimFlagEditor editor = service.buildEditor("Home", List.of(
                new ClaimFlagRow("build", "Access", "Build", "Allow block placement.", FlagKind.PLAYER_ACTION, FlagState.ALL, "havenclaims.flag.build"),
                new ClaimFlagRow("fluid_flow", "Environment", "Fluid Flow", "Allow water and lava.", FlagKind.WORLD_EFFECT, FlagState.OFF, "havenclaims.flag.fluid_flow")
        ));

        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::stateLabel)
                .containsExactly("ALLOWED", "BLOCKED");
        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::nextStateLabel)
                .containsExactly("DENIED", "ALLOWED");
    }

    @Test
    void usesDefaultFlagLabelsAndDescriptionsWhenMessagesAreMissing() {
        ClaimFlagEditorService service = new ClaimFlagEditorService(new MessageService(Map.of()));

        ClaimFlagEditor editor = service.buildEditor("Home", List.of(
                new ClaimFlagRow("build", "Access", "Build", "Allow block placement.", FlagKind.PLAYER_ACTION, FlagState.ALL, "havenclaims.flag.build")
        ));

        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::label)
                .containsExactly("Build");
        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::description)
                .containsExactly("Allow block placement.");
    }

    @Test
    void usesConfiguredFlagLabelsAndDescriptions() {
        MessageService messages = new MessageService(Map.ofEntries(
                Map.entry("claim.flag-editor.state-labels.access.enabled", "ALLOWED"),
                Map.entry("claim.flag-editor.state-labels.access.disabled", "DENIED"),
                Map.entry("claim.flag-editor.flag-labels.build", "Block Placement"),
                Map.entry("claim.flag-editor.flag-descriptions.build", "Controls who may place blocks.")
        ));
        ClaimFlagEditorService service = new ClaimFlagEditorService(messages);

        ClaimFlagEditor editor = service.buildEditor("Home", List.of(
                new ClaimFlagRow("build", "Access", "Build", "Allow block placement.", FlagKind.PLAYER_ACTION, FlagState.ALL, "havenclaims.flag.build")
        ));

        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::label)
                .containsExactly("Block Placement");
        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::description)
                .containsExactly("Controls who may place blocks.");
    }

    private static MessageService messages() {
        return new MessageService(Map.ofEntries(
                Map.entry("claim.flag-editor.state-labels.entity-control.enabled", "REMOVING"),
                Map.entry("claim.flag-editor.state-labels.entity-control.disabled", "KEEPING"),
                Map.entry("claim.flag-editor.state-labels.access.enabled", "ALLOWED"),
                Map.entry("claim.flag-editor.state-labels.access.disabled", "DENIED"),
                Map.entry("claim.flag-editor.state-labels.environment.enabled", "ALLOWED"),
                Map.entry("claim.flag-editor.state-labels.environment.disabled", "BLOCKED"),
                Map.entry("claim.flag-editor.state-labels.protection.enabled", "PROTECTED"),
                Map.entry("claim.flag-editor.state-labels.protection.disabled", "UNPROTECTED"),
                Map.entry("claim.flag-editor.state-labels.generic.enabled", "ENABLED"),
                Map.entry("claim.flag-editor.state-labels.generic.disabled", "DISABLED")
        ));
    }
}
