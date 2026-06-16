package com.invisiblespiders.havenclaims.plugin.accrual;

import dev.invisiblespiders.haven.api.HavenAPI;
import dev.invisiblespiders.haven.api.service.HavenAfkService;
import java.util.UUID;

public final class HavenCoreAfkDetector implements AfkDetector {
    private final HavenAfkService havenAfkService;

    public HavenCoreAfkDetector(HavenAfkService havenAfkService) {
        this.havenAfkService = havenAfkService;
    }

    public static AfkDetector create() {
        HavenAfkService afkService = HavenAPI.get(HavenAfkService.class);
        return afkService != null ? new HavenCoreAfkDetector(afkService) : new NoopAfkDetector();
    }

    @Override
    public boolean isAfk(UUID playerId) {
        return havenAfkService.isAfk(playerId);
    }
}
