package com.nick.landclaims.api.flag;

import java.util.List;

public enum FlagKind {
    PLAYER_ACTION(List.of(FlagState.OFF, FlagState.VISITORS, FlagState.ALL)),
    WORLD_EFFECT(List.of(FlagState.OFF, FlagState.ALL));

    private final List<FlagState> cycle;

    FlagKind(List<FlagState> cycle) {
        this.cycle = List.copyOf(cycle);
    }

    public List<FlagState> cycle() {
        return cycle;
    }

    public boolean supports(FlagState state) {
        return cycle.contains(state);
    }

    public FlagState next(FlagState current) {
        int index = cycle.indexOf(current);
        if (index < 0) {
            return cycle.get(0);
        }
        return cycle.get((index + 1) % cycle.size());
    }
}
