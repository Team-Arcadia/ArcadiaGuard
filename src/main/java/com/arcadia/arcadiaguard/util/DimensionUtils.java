package com.arcadia.arcadiaguard.util;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

/** Centralized utility for computing dimension resource-location keys. */
public final class DimensionUtils {

    private DimensionUtils() {}

    /**
     * H-P3: ConcurrentHashMap keyed on ResourceKey (interned enum-like singletons in practice)
     * rather than WeakHashMap<Level,...> which had synchronized write contention and potential
     * GC churn. ResourceKey instances are long-lived; no weak-ref needed.
     */
    private static final ConcurrentHashMap<ResourceKey<Level>, String> KEY_CACHE =
        new ConcurrentHashMap<>();

    /** Returns the dimension key string for a Level. */
    public static String keyOf(Level level) {
        return keyOf(level.dimension());
    }

    /** Returns the dimension key string for a ResourceKey, caching the result. */
    public static String keyOf(ResourceKey<Level> key) {
        return KEY_CACHE.computeIfAbsent(key, k -> k.location().toString());
    }

    public static String keyOf(ServerPlayer player) {
        return keyOf(player.serverLevel());
    }
}
