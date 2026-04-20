package com.arcadia.arcadiaguard.compat.luckperms;

import net.neoforged.fml.ModList;

/**
 * Optional LuckPerms integration. Detected at runtime via ModList — zero compile dependency.
 * If LuckPerms is absent, the internal role system is used as fallback.
 *
 * <p>isAvailable() and checker() are memoized so repeated calls from hot paths
 * (shouldBypass, isZoneMember) cost only a volatile read after the first call.
 */
public final class LuckPermsCompat {

    /** H-E4: volatile to ensure visibility across threads after init(). */
    private static volatile LuckPermsPermissionChecker checker;

    /** Memoized availability: null = not yet computed, TRUE/FALSE = resolved. */
    private static volatile Boolean availableCache;

    private LuckPermsCompat() {}

    /**
     * Returns true if LuckPerms is loaded on this server.
     * Result is cached after the first call (volatile double-checked, lock-free read on hot path).
     */
    public static boolean isAvailable() {
        Boolean v = availableCache;
        if (v == null) {
            v = ModList.get().isLoaded("luckperms");
            availableCache = v;
        }
        return v;
    }

    /**
     * Initializes the LuckPerms integration. Must be called after server start,
     * only if {@link #isAvailable()} returns true.
     */
    public static void init() {
        if (!isAvailable()) return;
        checker = new LuckPermsPermissionChecker();
    }

    /** Returns the permission checker, or null if LuckPerms is not loaded. */
    public static LuckPermsPermissionChecker checker() {
        return checker;
    }

    /**
     * H-P6: Invalidates cached LuckPerms permission data for a player.
     * Call on player logout so stale data isn't reused on next login.
     */
    public static void invalidatePlayer(java.util.UUID id) {
        LuckPermsPermissionChecker c = checker;
        if (c != null) c.invalidatePlayer(id);
    }
}
