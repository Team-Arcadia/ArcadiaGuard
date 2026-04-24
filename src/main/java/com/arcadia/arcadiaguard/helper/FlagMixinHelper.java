package com.arcadia.arcadiaguard.helper;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.guard.GuardService;
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
        if (dimKey == null) HAS_ZONE_CACHE.clear();
        else HAS_ZONE_CACHE.remove(dimKey);
    }

    public static void invalidateAll() {
        HAS_ZONE_CACHE.clear();
    }

    public static boolean isDenied(Level level, BlockPos pos, BooleanFlag flag) {
        if (level == null || level.isClientSide()) return false;
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
}
