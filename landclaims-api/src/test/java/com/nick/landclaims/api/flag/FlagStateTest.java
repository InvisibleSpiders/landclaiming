package com.nick.landclaims.api.flag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FlagStateTest {
    @Test
    void hasThreeStatesInDeclaredOrder() {
        assertEquals(3, FlagState.values().length);
        assertEquals(FlagState.OFF, FlagState.values()[0]);
        assertEquals(FlagState.VISITORS, FlagState.values()[1]);
        assertEquals(FlagState.ALL, FlagState.values()[2]);
    }
}
