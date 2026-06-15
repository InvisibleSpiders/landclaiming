package com.invisiblespiders.havenclaims.plugin.listener;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BoundaryMessageDeliveryTest {
    @Test
    void parsesKnownDeliveryModes() {
        assertThat(BoundaryMessageDelivery.from("chat", BoundaryMessageDelivery.ACTION_BAR))
                .isEqualTo(BoundaryMessageDelivery.CHAT);
        assertThat(BoundaryMessageDelivery.from("both", BoundaryMessageDelivery.ACTION_BAR))
                .isEqualTo(BoundaryMessageDelivery.BOTH);
        assertThat(BoundaryMessageDelivery.from("none", BoundaryMessageDelivery.ACTION_BAR))
                .isEqualTo(BoundaryMessageDelivery.NONE);
    }

    @Test
    void fallsBackWhenValueIsBlankOrUnknown() {
        assertThat(BoundaryMessageDelivery.from("", BoundaryMessageDelivery.CHAT))
                .isEqualTo(BoundaryMessageDelivery.CHAT);
        assertThat(BoundaryMessageDelivery.from("somewhere", BoundaryMessageDelivery.BOTH))
                .isEqualTo(BoundaryMessageDelivery.BOTH);
    }

    @Test
    void exposesSendTargets() {
        assertThat(BoundaryMessageDelivery.CHAT.sendsChat()).isTrue();
        assertThat(BoundaryMessageDelivery.CHAT.sendsActionBar()).isFalse();
        assertThat(BoundaryMessageDelivery.ACTION_BAR.sendsChat()).isFalse();
        assertThat(BoundaryMessageDelivery.ACTION_BAR.sendsActionBar()).isTrue();
        assertThat(BoundaryMessageDelivery.BOTH.sendsChat()).isTrue();
        assertThat(BoundaryMessageDelivery.BOTH.sendsActionBar()).isTrue();
        assertThat(BoundaryMessageDelivery.NONE.sendsChat()).isFalse();
        assertThat(BoundaryMessageDelivery.NONE.sendsActionBar()).isFalse();
    }
}
