package com.nick.landclaims.plugin.expansion;

import com.nick.landclaims.api.flag.ClaimFlagDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ExpansionRegistry {
    private final List<ClaimFlagDefinition> registeredFlags = new ArrayList<>();

    public void registerFlag(ClaimFlagDefinition definition) {
        registeredFlags.add(Objects.requireNonNull(definition, "definition"));
    }

    public List<ClaimFlagDefinition> registeredFlags() {
        return List.copyOf(registeredFlags);
    }
}
