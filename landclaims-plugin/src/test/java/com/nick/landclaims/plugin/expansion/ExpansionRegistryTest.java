package com.nick.landclaims.plugin.expansion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nick.landclaims.api.flag.ClaimFlagDefinition;
import org.junit.jupiter.api.Test;

class ExpansionRegistryTest {
    @Test
    void registeredFlagsReturnsImmutableSnapshotOfRegisteredDefinitions() {
        ExpansionRegistry registry = new ExpansionRegistry();
        ClaimFlagDefinition first = flag("custom_one");
        ClaimFlagDefinition second = flag("custom_two");

        registry.registerFlag(first);
        var snapshot = registry.registeredFlags();
        registry.registerFlag(second);

        assertThat(snapshot).containsExactly(first);
        assertThat(registry.registeredFlags()).containsExactly(first, second);
        assertThatThrownBy(() -> registry.registeredFlags().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void registerFlagRejectsNullDefinition() {
        ExpansionRegistry registry = new ExpansionRegistry();

        assertThatNullPointerException()
                .isThrownBy(() -> registry.registerFlag(null));
    }

    private static ClaimFlagDefinition flag(String key) {
        return new ClaimFlagDefinition(
                key,
                "expansion",
                false,
                "landclaims.flag." + key
        );
    }
}
