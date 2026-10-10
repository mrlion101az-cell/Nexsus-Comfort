package com.nexuscraft.nexuscomfort;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * A soft, reflection-based link to NexusWorldWar's WorldWarApi: is this player fighting in a war right now?
 * A failed lookup is retried every {@link #RETRY_MS}, so it still finds the war if it enabled a moment later.
 */
final class WarBridge {

    static final String API_CLASS_NAME = "com.nexuscraft.nexusworldwar.WorldWarApi";
    static final long RETRY_MS = 30_000L;

    private final LongSupplier clock;
    private final String className;
    private Class<?> apiClass;
    private Object apiInstance;
    private long nextTry;

    WarBridge(LongSupplier clock) {
        this(clock, API_CLASS_NAME);
    }

    /** The class name is a parameter so tests can look for a plugin that is not installed. */
    WarBridge(LongSupplier clock, String className) {
        this.clock = clock;
        this.className = className;
    }

    boolean connected() {
        if (apiInstance != null) {
            return true;
        }
        long now = clock.getAsLong();
        if (now < nextTry) {
            return false;
        }
        nextTry = now + RETRY_MS;
        try {
            Class<?> found = Class.forName(className);
            RegisteredServiceProvider<?> reg = Bukkit.getServicesManager().getRegistration(found);
            if (reg != null) {
                apiClass = found;
                apiInstance = reg.getProvider();
            }
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
        return apiInstance != null;
    }

    /** True only if the war plugin is there and says this player is fighting. */
    boolean fighting(UUID player) {
        if (!connected()) {
            return false;
        }
        try {
            Method m = apiClass.getMethod("isFighter", new Class<?>[] {UUID.class});
            return Boolean.TRUE.equals(m.invoke(apiInstance, player));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
