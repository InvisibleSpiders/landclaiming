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
                                flag.label(),
                                flag.description(),
                                stateLabel(flag, flag.enabled()),
                                stateLabel(flag, !flag.enabled()),
                                "/claim flag toggle " + flag.key()
                        ))
                        .toList()
        );
    }

    private String stateLabel(ClaimFlagRow flag, boolean enabled) {
        if (flag.key().startsWith("remove_") && flag.category().equalsIgnoreCase("Entity Control")) {
            return enabled ? "REMOVING" : "KEEPING";
        }
        if (flag.category().equalsIgnoreCase("Access")
                || flag.category().equalsIgnoreCase("Items")
                || flag.category().equalsIgnoreCase("Entity")) {
            return enabled ? "ALLOWED" : "DENIED";
        }
        if (flag.category().equalsIgnoreCase("Environment")) {
            return enabled ? "ALLOWED" : "BLOCKED";
        }
        if (flag.category().equalsIgnoreCase("Protection")) {
            return enabled ? "PROTECTED" : "UNPROTECTED";
        }
        return enabled ? "ENABLED" : "DISABLED";
    }
}
