package com.arcadia.arcadiaguard.compat.luckperms;

import com.arcadia.arcadiaguard.util.ReflectionHelper;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.LoggerFactory;

/**
 * Checks ArcadiaGuard-specific LuckPerms permission nodes via reflection.
 * Zero compile dependency on LuckPerms — all calls go through {@link ReflectionHelper}.
 *
 * <p>Node hierarchy:
 * <ul>
 *   <li>{@code arcadiaguard.*} — full bypass
 *   <li>{@code arcadiaguard.zone.bypass} — bypass without admin access
 *   <li>{@code arcadiaguard.zone.<name>.member|moderator|owner} — role in zone
 * </ul>
 */
public final class LuckPermsPermissionChecker {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(LuckPermsPermissionChecker.class);

    /** H13: warn only once on reflection failure to avoid log spam. */
    private final AtomicBoolean warnedReflection = new AtomicBoolean(false);

    /**
     * H-P6: Cache the LuckPerms player adapter (obtained once per JVM session).
     * The adapter is a stable singleton — safe to cache in a volatile.
     */
    private volatile Object cachedPlayerAdapter = null;
    private volatile boolean adapterResolved = false;

    /**
     * H-P6: Cache permData objects per player UUID. Avoids re-fetching the entire
     * LuckPerms data chain on every permission check. Invalidated on logout via
     * {@link #invalidatePlayer(UUID)}.
     */
    private final ConcurrentHashMap<UUID, Object> permDataCache = new ConcurrentHashMap<>();

    /**
     * H-P6: Invalidates cached permission data for a player (call on logout).
     */
    public void invalidatePlayer(UUID id) {
        permDataCache.remove(id);
    }

    /** Returns true if the player has {@code arcadiaguard.*} or {@code arcadiaguard.zone.bypass}. */
    public boolean hasBypass(ServerPlayer player) {
        return checkNode(player, "arcadiaguard.*") || checkNode(player, "arcadiaguard.zone.bypass");
    }

    /**
     * Returns the highest role the player holds in the given zone via LuckPerms nodes.
     * Returns {@code null} if LuckPerms is unavailable or no node matches.
     */
    public String resolveRole(ServerPlayer player, String zoneName) {
        String base = "arcadiaguard.zone." + zoneName.toLowerCase(java.util.Locale.ROOT) + ".";
        if (checkNode(player, base + "owner"))     return "owner";
        if (checkNode(player, base + "moderator")) return "moderator";
        if (checkNode(player, base + "member"))    return "member";
        return null;
    }

    private boolean checkNode(ServerPlayer player, String node) {
        try {
            // H-P6: cache playerAdapter — it's a stable singleton resolved once
            if (!adapterResolved) {
                synchronized (this) {
                    if (!adapterResolved) {
                        Object lp = ReflectionHelper.invokeStatic(
                            "net.luckperms.api.LuckPermsProvider", "get", new Class<?>[0]).orElse(null);
                        cachedPlayerAdapter = lp == null ? null : ReflectionHelper.invoke(
                            lp, "getPlayerAdapter", new Class<?>[]{Class.class}, ServerPlayer.class).orElse(null);
                        adapterResolved = true;
                    }
                }
            }
            Object adapter = cachedPlayerAdapter;
            if (adapter == null) return false;

            // H-P6: cache permData per UUID (invalidated on logout)
            UUID uuid = player.getUUID();
            Object permData = permDataCache.computeIfAbsent(uuid, id -> {
                Object user = ReflectionHelper.invoke(
                    adapter, "getUser", new Class<?>[]{}, player).orElse(null);
                if (user == null) return null;
                Object cachedData = ReflectionHelper.invoke(user, "getCachedData", new Class<?>[0]).orElse(null);
                if (cachedData == null) return null;
                return ReflectionHelper.invoke(cachedData, "getPermissionData", new Class<?>[0]).orElse(null);
            });
            if (permData == null) return false;

            Object tristate = ReflectionHelper.invoke(
                permData, "checkPermission", new Class<?>[]{String.class}, node).orElse(null);
            if (tristate == null) return false;
            return ReflectionHelper.boolMethod(tristate, "asBoolean", new Class<?>[0]);
        } catch (Exception e) {
            // H13: log once to surface potential LP API changes
            if (warnedReflection.compareAndSet(false, true)) {
                LOG.warn("[ArcadiaGuard] LuckPerms reflection failed (likely API change): {}", e.toString());
            }
            return false;
        }
    }
}
