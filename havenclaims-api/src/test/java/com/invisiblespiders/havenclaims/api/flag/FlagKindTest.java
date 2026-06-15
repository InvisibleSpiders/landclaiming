package com.invisiblespiders.havenclaims.api.flag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FlagKindTest {
    @Test
    void playerActionCyclesThroughAllThreeStates() {
        assertEquals(List.of(FlagState.OFF, FlagState.VISITORS, FlagState.ALL),
                FlagKind.PLAYER_ACTION.cycle());
        assertEquals(FlagState.VISITORS, FlagKind.PLAYER_ACTION.next(FlagState.OFF));
        assertEquals(FlagState.ALL, FlagKind.PLAYER_ACTION.next(FlagState.VISITORS));
        assertEquals(FlagState.OFF, FlagKind.PLAYER_ACTION.next(FlagState.ALL));
        assertTrue(FlagKind.PLAYER_ACTION.supports(FlagState.VISITORS));
    }

    @Test
    void worldEffectSkipsVisitors() {
        assertEquals(List.of(FlagState.OFF, FlagState.ALL), FlagKind.WORLD_EFFECT.cycle());
        assertEquals(FlagState.ALL, FlagKind.WORLD_EFFECT.next(FlagState.OFF));
        assertEquals(FlagState.OFF, FlagKind.WORLD_EFFECT.next(FlagState.ALL));
        assertFalse(FlagKind.WORLD_EFFECT.supports(FlagState.VISITORS));
    }

    @Test
    void nextFromUnsupportedStateReturnsFirst() {
        // A WORLD_EFFECT flag should never hold VISITORS, but be defensive.
        assertEquals(FlagState.OFF, FlagKind.WORLD_EFFECT.next(FlagState.VISITORS));
    }
}
