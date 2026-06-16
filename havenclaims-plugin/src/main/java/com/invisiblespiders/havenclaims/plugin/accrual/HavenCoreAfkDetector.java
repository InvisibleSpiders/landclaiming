package com.invisiblespiders.havenclaims.plugin.accrual;

import dev.invisiblespiders.haven.api.HavenAPI;
import java.lang.reflect.Method;
import java.util.UUID;

public final class HavenCoreAfkDetector implements AfkDetector {
    private final Object havenAfkService;
    private final Method isAfkMethod;

    private HavenCoreAfkDetector(Object havenAfkService, Method isAfkMethod) {
        this.havenAfkService = havenAfkService;
        this.isAfkMethod = isAfkMethod;
    }

    @SuppressWarnings("unchecked")
    public static AfkDetector create() {
        try {
            Class<Object> afkClass = (Class<Object>) Class.forName(
                    "dev.invisiblespiders.haven.api.service.HavenAfkService");
            Object afkService = HavenAPI.get(afkClass);
            if (afkService == null) return new NoopAfkDetector();
            Method isAfkMethod = afkClass.getMethod("isAfk", UUID.class);
            return new HavenCoreAfkDetector(afkService, isAfkMethod);
        } catch (ReflectiveOperationException e) {
            return new NoopAfkDetector();
        }
    }

    @Override
    public boolean isAfk(UUID playerId) {
        try {
            return (Boolean) isAfkMethod.invoke(havenAfkService, playerId);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
