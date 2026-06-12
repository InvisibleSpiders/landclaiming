package com.nick.landclaims.plugin.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.nick.landclaims.plugin.flag.ClaimFlagRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClaimFlagEditorServiceTest {
    @Test
    void buildsToggleRowsGroupedByFlagCategory() {
        ClaimFlagEditorService service = new ClaimFlagEditorService();

        ClaimFlagEditor editor = service.buildEditor("Home", List.of(
                new ClaimFlagRow("build", "Access", "Build", "Allow block placement.", true, "landclaims.flag.build"),
                new ClaimFlagRow("fluid_flow", "Environment", "Fluid Flow", "Allow water and lava.", false, "landclaims.flag.fluid_flow")
        ));

        assertThat(editor.claimName()).isEqualTo("Home");
        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::toggleCommand)
                .containsExactly("/claim flag toggle build", "/claim flag toggle fluid_flow");
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
        ClaimFlagEditorService service = new ClaimFlagEditorService();

        ClaimFlagEditor editor = service.buildEditor("Home", List.of(
                new ClaimFlagRow(
                        "remove_hostile_entities",
                        "Entity Control",
                        "Remove Hostiles",
                        "Removes hostile mobs unless named or tamed.",
                        true,
                        "landclaims.flag.remove_hostile_entities"
                ),
                new ClaimFlagRow(
                        "remove_passive_entities",
                        "Entity Control",
                        "Remove Passives",
                        "Removes passive mobs unless named or tamed.",
                        false,
                        "landclaims.flag.remove_passive_entities"
                )
        ));

        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::stateLabel)
                .containsExactly("REMOVING", "KEEPING");
        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::nextStateLabel)
                .containsExactly("KEEPING", "REMOVING");
    }
}
