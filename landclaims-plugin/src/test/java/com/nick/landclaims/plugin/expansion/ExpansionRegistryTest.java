package com.nick.landclaims.plugin.expansion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nick.landclaims.api.flag.ClaimFlagDefinition;
import com.nick.landclaims.api.flag.FlagKind;
import com.nick.landclaims.api.flag.FlagState;
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

    @Test
    void registerFlagRejectsDuplicateKey() {
        ExpansionRegistry registry = new ExpansionRegistry();

        registry.registerFlag(flag("custom_one"));

        assertThatThrownBy(() -> registry.registerFlag(flag("custom_one")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("custom_one");
    }

    private static ClaimFlagDefinition flag(String key) {
        return new ClaimFlagDefinition(
                key, "expansion", key, "",
                FlagKind.PLAYER_ACTION, true, FlagState.OFF,
                "landclaims.flag." + key
        );
    }
}
