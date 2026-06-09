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
                .containsExactly("/claims flag toggle build", "/claims flag toggle fluid_flow");
        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::stateLabel)
                .containsExactly("ON", "OFF");
        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::nextStateLabel)
                .containsExactly("OFF", "ON");
        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::label)
                .containsExactly("Build", "Fluid Flow");
        assertThat(editor.rows()).extracting(ClaimFlagEditorRow::description)
                .containsExactly("Allow block placement.", "Allow water and lava.");
    }
}
