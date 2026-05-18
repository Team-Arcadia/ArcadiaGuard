package com.arcadia.arcadiaguard.helper;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * Helper statique utilisé par les mixins random-tick pour tester si un flag
 * boolean est refusé à une position. Null-safe avant le démarrage du serveur.
 *
 * <p>Placé hors du package {@code mixin} pour pouvoir être référencé depuis du
 * code non-mixin (ex: InternalZoneProvider pour invalider le cache).
 */
public final class FlagMixinHelper {

    private FlagMixinHelper() {}

    private static final ConcurrentHashMap<String, Boolean> HAS_ZONE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<FlagKey, Boolean> MAY_DENY_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<PosKey, Long> CONTAINER_DROP_ALLOWANCES = new ConcurrentHashMap<>();
    private static final long CONTAINER_DROP_ALLOWANCE_TTL_MS = 1_000L;
    private static final int CONTAINER_DROP_ALLOWANCE_MAX = 512;

    private record FlagKey(String dimension, String flagId) {}
    private record PosKey(String dimension, int x, int y, int z) {}

    /** Fast-path : retourne true si au moins une zone existe dans la dimension. */
    public static boolean hasAnyZoneInDim(Level level) {
        if (level == null || level.isClientSide()) return false;
        GuardService guard = ArcadiaGuard.guardService();
        if (guard == null) return false;
        String dim = level.dimension().location().toString();
        return HAS_ZONE_CACHE.computeIfAbsent(dim, k -> !guard.zoneManager().zones(level).isEmpty());
    }

    public static boolean hasAnyZoneInDim(LevelAccessor accessor) {
        if (accessor instanceof Level level) return hasAnyZoneInDim(level);
        return false;
    }

    /**
     * Fast-path guard : retourne true s'il existe au moins une zone OU au moins un dim flag
     * configure sur cette dimension. Utilise pour les handlers tick (animaux, crop-growth,
     * mixins block-tick) afin de preserver la semantique des dim flags sans perf cost
     * quand rien n'est configure.
     */
    public static boolean hasAnyRuleInDim(Level level) {
        if (level == null || level.isClientSide()) return false;
        if (hasAnyZoneInDim(level)) return true;
        GuardService guard = ArcadiaGuard.guardService();
        if (guard == null) return false;
        String dim = level.dimension().location().toString();
        var dimFlags = ArcadiaGuard.dimFlagStore().flags(dim);
        return dimFlags != null && !dimFlags.isEmpty();
    }

    public static boolean hasAnyRuleInDim(LevelAccessor accessor) {
        if (accessor instanceof Level level) return hasAnyRuleInDim(level);
        return false;
    }

    public static void invalidateHasZoneCache(String dimKey) {
        if (dimKey == null) invalidateAll();
        else {
            HAS_ZONE_CACHE.remove(dimKey);
            invalidateFlagCache(dimKey);
        }
    }

    public static void invalidateAll() {
        HAS_ZONE_CACHE.clear();
        MAY_DENY_CACHE.clear();
    }

    public static void invalidateFlagCache(String dimKey) {
        if (dimKey == null) {
            MAY_DENY_CACHE.clear();
            return;
        }
        MAY_DENY_CACHE.keySet().removeIf(key -> key.dimension().equals(dimKey));
    }

    public static boolean mayDeny(Level level, BooleanFlag flag) {
        if (level == null || level.isClientSide() || flag == null) return false;
        GuardService guard = ArcadiaGuard.guardService();
        if (guard == null || guard.isFrequencyDisabled(flag)) return false;
        String dim = level.dimension().location().toString();
        return MAY_DENY_CACHE.computeIfAbsent(new FlagKey(dim, flag.id()),
            ignored -> computeMayDeny(level, flag, guard, dim));
    }

    public static boolean mayDeny(LevelAccessor accessor, BooleanFlag flag) {
        if (accessor instanceof Level level) return mayDeny(level, flag);
        return false;
    }

    private static boolean computeMayDeny(Level level, BooleanFlag flag, GuardService guard, String dim) {
        Object dimValue = ArcadiaGuard.dimFlagStore().flags(dim).get(flag.id());
        if (Boolean.FALSE.equals(dimValue)) return true;

        try {
            for (var zoneView : guard.zoneManager().zones(level)) {
                if (!(zoneView instanceof ProtectedZone zone)) continue;
                if (!zone.enabled()) continue;
                Object zoneValue = zone.flagValues().get(flag.id());
                if (Boolean.FALSE.equals(zoneValue)) return true;
            }
        } catch (Throwable ignored) {
            return true;
        }
        return false;
    }

    public static boolean isDenied(Level level, BlockPos pos, BooleanFlag flag) {
        if (level == null || level.isClientSide()) return false;
        if (!mayDeny(level, flag)) return false;
        GuardService guard = ArcadiaGuard.guardService();
        if (guard == null) return false;
        try {
            return guard.isZoneDenying(level, pos, flag);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isDenied(LevelAccessor accessor, BlockPos pos, BooleanFlag flag) {
        if (accessor instanceof Level level) return isDenied(level, pos, flag);
        return false;
    }

    public static void allowContainerDropOnce(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) return;
        long now = System.currentTimeMillis();
        if (CONTAINER_DROP_ALLOWANCES.size() > CONTAINER_DROP_ALLOWANCE_MAX) {
            CONTAINER_DROP_ALLOWANCES.entrySet().removeIf(e -> e.getValue() < now);
        }
        CONTAINER_DROP_ALLOWANCES.put(key(level, pos), now + CONTAINER_DROP_ALLOWANCE_TTL_MS);
    }

    public static boolean consumeContainerDropAllowance(Level level, BlockPos pos) {
        if (level == null || level.isClientSide()) return false;
        Long expiresAt = CONTAINER_DROP_ALLOWANCES.remove(key(level, pos));
        return expiresAt != null && expiresAt >= System.currentTimeMillis();
    }

    private static PosKey key(Level level, BlockPos pos) {
        return new PosKey(level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
    }
}
