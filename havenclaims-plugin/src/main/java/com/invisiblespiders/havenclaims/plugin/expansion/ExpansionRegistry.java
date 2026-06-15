package com.invisiblespiders.havenclaims.plugin.expansion;

import com.invisiblespiders.havenclaims.api.flag.ClaimFlagDefinition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ExpansionRegistry {
    private final List<ClaimFlagDefinition> registeredFlags = new ArrayList<>();
    private final Set<String> registeredFlagKeys = new HashSet<>();

    public void registerFlag(ClaimFlagDefinition definition) {
        ClaimFlagDefinition nonNullDefinition = Objects.requireNonNull(definition, "definition");
        if (!registeredFlagKeys.add(nonNullDefinition.key())) {
            throw new IllegalArgumentException("Duplicate flag key: " + nonNullDefinition.key());
        }
        registeredFlags.add(nonNullDefinition);
    }

    public List<ClaimFlagDefinition> registeredFlags() {
        return List.copyOf(registeredFlags);
    }
}
