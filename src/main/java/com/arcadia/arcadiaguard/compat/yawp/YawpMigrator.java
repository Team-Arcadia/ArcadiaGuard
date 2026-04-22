package com.arcadia.arcadiaguard.compat.yawp;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.ArcadiaGuardPaths;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Migrates zones from YAWP (Yet Another World Protector) to ArcadiaGuard format.
 * Uses reflection — zero compile-time dependency on YAWP.
 *
 * <p>Real YAWP API (0.6.x, NeoForge 1.21.1):
 * <ul>
 *   <li>{@code de.z0rdak.yawp.data.region.RegionDataManager} — static, holds private field
 *       {@code dimRegionStorage : Map<ResourceLocation, LevelRegionData>}</li>
 *   <li>{@code LevelRegionData.getLocalList() -> Collection<IMarkableRegion>}</li>
 *   <li>{@code IMarkableRegion.getName()}, {@code getDim()}, {@code getArea() -> CuboidArea}</li>
 *   <li>{@code CuboidArea.getAreaP1()} / {@code getAreaP2() -> BlockPos}</li>
 * </ul>
 */
public final class YawpMigrator {

    private YawpMigrator() {}

    public static List<ProtectedZone> migrate(MinecraftServer server) {
        List<ProtectedZone> result = new ArrayList<>();
        try {
            Class<?> rdmCls;
            try {
                rdmCls = Class.forName("de.z0rdak.yawp.data.region.RegionDataManager");
            } catch (ClassNotFoundException e) {
                ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] YAWP not installed (RegionDataManager class not found) — migration aborted.");
                return result;
            }

            // Private static Map<ResourceLocation, LevelRegionData> dimRegionStorage
            var field = rdmCls.getDeclaredField("dimRegionStorage");
            field.setAccessible(true);
            Object storage = field.get(null);
            if (!(storage instanceof Map<?, ?> dimMap)) {
                ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] YAWP dimRegionStorage not a Map — unexpected YAWP version.");
                return result;
            }
            ArcadiaGuard.LOGGER.info("[ArcadiaGuard] YAWP found: {} dimension(s) with region data.", dimMap.size());

            int totalRegions = 0;
            for (var entry : dimMap.entrySet()) {
                ResourceLocation dim = (entry.getKey() instanceof ResourceLocation rl) ? rl : null;
                Object levelRegionData = entry.getValue();
                if (dim == null || levelRegionData == null) continue;

                Object locals = ReflectionHelper.invoke(levelRegionData, "getLocalList", new Class<?>[0]).orElse(null);
                if (!(locals instanceof Collection<?> regions)) continue;
                totalRegions += regions.size();
                ArcadiaGuard.LOGGER.info("[ArcadiaGuard] YAWP dim '{}': {} region(s).", dim, regions.size());

                for (Object region : regions) {
                    try {
                        ProtectedZone zone = convertRegion(region, dim);
                        if (zone != null) result.add(zone);
                    } catch (Throwable t) {
                        ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Failed to convert YAWP region: {}", t.toString());
                    }
                }
            }

            ArcadiaGuard.LOGGER.info("[ArcadiaGuard] YAWP scan: {} zone(s) convertible sur {} region(s) totales.", result.size(), totalRegions);
        } catch (Throwable t) {
            ArcadiaGuard.LOGGER.error("[ArcadiaGuard] YAWP migration failed", t);
        }
        return result;
    }

    /** Converts one YAWP IMarkableRegion into a {@link ProtectedZone}. */
    private static ProtectedZone convertRegion(Object region, ResourceLocation dim) {
        String name = (String) ReflectionHelper.invoke(region, "getName", new Class<?>[0]).orElse("yawp_unnamed");
        // Sanitize le nom YAWP pour notre format : lowercase + underscores, etc.
        String normalized = ArcadiaGuardPaths.normalizeZoneName(name);
        if (normalized.isEmpty()) normalized = "yawp_" + Math.abs(name.hashCode());

        Object area = ReflectionHelper.invoke(region, "getArea", new Class<?>[0]).orElse(null);
        if (area == null) {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] YAWP region '{}' has no area — skipped.", name);
            return null;
        }

        // Seul le type CuboidArea est supporte (YAWP a aussi sphere/polygon/prism/cylinder).
        Object p1Obj = ReflectionHelper.invoke(area, "getAreaP1", new Class<?>[0]).orElse(null);
        Object p2Obj = ReflectionHelper.invoke(area, "getAreaP2", new Class<?>[0]).orElse(null);
        if (!(p1Obj instanceof BlockPos p1) || !(p2Obj instanceof BlockPos p2)) {
            ArcadiaGuard.LOGGER.warn(
                "[ArcadiaGuard] YAWP region '{}' area type {} non-cuboid — skipped (ArcadiaGuard ne supporte que cuboid).",
                name, area.getClass().getSimpleName());
            return null;
        }

        return new ProtectedZone(normalized, dim.toString(),
            Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()),
            Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()),
            new HashSet<>());
    }

}
