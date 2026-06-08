package com.nick.landclaims.plugin.ui;

import com.nick.landclaims.plugin.flag.ClaimFlagRow;
import java.util.List;
import java.util.Objects;

public final class ClaimFlagEditorService {
    public ClaimFlagEditor buildEditor(String claimName, List<ClaimFlagRow> flags) {
        Objects.requireNonNull(claimName, "claimName");
        Objects.requireNonNull(flags, "flags");

        return new ClaimFlagEditor(
                claimName,
                flags.stream()
                        .map(flag -> new ClaimFlagEditorRow(
                                flag.key(),
                                flag.category(),
                                stateLabel(flag.enabled()),
                                stateLabel(!flag.enabled()),
                                "/claims flag toggle " + flag.key()
                        ))
                        .toList()
        );
    }

    private String stateLabel(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }
}
