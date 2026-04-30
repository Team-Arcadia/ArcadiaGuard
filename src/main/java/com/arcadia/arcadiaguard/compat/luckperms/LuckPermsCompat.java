package com.arcadia.arcadiaguard.compat.luckperms;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

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
        subscribeToUserDataRecalculate();
    }

    /**
     * Subscribe to LuckPerms UserDataRecalculateEvent so command trees are re-sent
     * to affected players when their perms change. Without this, granting
     * {@code arcadiaguard.view} at runtime isn't visible to the client until reco.
     *
     * <p>Uses reflection to stay compile-time-independent from LuckPerms.
     */
    @SuppressWarnings("unchecked")
    private static void subscribeToUserDataRecalculate() {
        try {
            Object lp = ReflectionHelper.invokeStatic(
                "net.luckperms.api.LuckPermsProvider", "get", new Class<?>[0]).orElse(null);
            if (lp == null) return;
            Object bus = ReflectionHelper.invoke(lp, "getEventBus", new Class<?>[0]).orElse(null);
            if (bus == null) return;
            Class<?> eventCls = Class.forName("net.luckperms.api.event.user.UserDataRecalculateEvent");
            // subscribe(Class, Consumer) — use raw Consumer so reflection works
            java.util.function.Consumer<Object> handler = event -> onUserDataRecalculate(event);
            bus.getClass()
                .getMethod("subscribe", Class.class, java.util.function.Consumer.class)
                .invoke(bus, eventCls, handler);
            ArcadiaGuard.debugInfo("[ArcadiaGuard] LuckPerms UserDataRecalculateEvent subscribed — command tree will refresh on perm changes");
        } catch (Throwable t) {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Could not subscribe to LuckPerms events (hot refresh disabled): {}", t.toString());
        }
    }

    /** Appelee quand LP recalcule les donnees d'un user. Invalide caches + resync command tree. */
    private static void onUserDataRecalculate(Object event) {
        try {
            Object user = ReflectionHelper.invoke(event, "getUser", new Class<?>[0]).orElse(null);
            if (user == null) return;
            Object uuidObj = ReflectionHelper.invoke(user, "getUniqueId", new Class<?>[0]).orElse(null);
            if (!(uuidObj instanceof UUID uuid)) return;

            // Invalide les caches bypass + LP permData.
            if (ArcadiaGuard.services() != null) {
                ArcadiaGuard.guardService().invalidateBypass(uuid);
            }
            invalidatePlayer(uuid);

            // Refresh command tree du joueur affecte (enqueue sur main thread).
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                if (p != null) server.getCommands().sendCommands(p);
            });
        } catch (Throwable t) {
            ArcadiaGuard.LOGGER.debug("[ArcadiaGuard] LP recalculate handler failed: {}", t.toString());
        }
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
