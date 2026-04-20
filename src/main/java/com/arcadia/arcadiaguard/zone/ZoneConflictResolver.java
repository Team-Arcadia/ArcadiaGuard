package com.arcadia.arcadiaguard.zone;

import java.util.Comparator;
import java.util.List;

/**
 * Resolves conflicts when multiple zones overlap at a given position.
 * Resolution order: highest {@code priority} → smallest volume → alphabetical name.
 */
public final class ZoneConflictResolver {

    private static final Comparator<ProtectedZone> COMPARATOR = Comparator
        .comparingInt(ZoneConflictResolver::priority).reversed()
        .thenComparingLong(ZoneConflictResolver::volume)
        .thenComparing(ProtectedZone::name);

    private ZoneConflictResolver() {}

    /**
     * Returns the winning zone from the given list of overlapping zones.
     * The list must not be empty.
     */
    public static ProtectedZone resolve(List<ProtectedZone> candidates) {
        return candidates.stream().min(COMPARATOR).orElseThrow();
    }

    private static int priority(ProtectedZone zone) {
        Object val = zone.flagValues().get("priority");
        if (val instanceof Integer i) return i;
        return zone.priority();
    }

    private static long volume(ProtectedZone zone) {
        long dx = (long) zone.maxX() - (long) zone.minX() + 1L;
        long dy = (long) zone.maxY() - (long) zone.minY() + 1L;
        long dz = (long) zone.maxZ() - (long) zone.minZ() + 1L;
        try {
            return Math.multiplyExact(Math.multiplyExact(dx, dy), dz);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }
}
