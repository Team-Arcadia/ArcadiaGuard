package com.arcadia.arcadiaguard.compat.yawp;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.ArcadiaGuardPaths;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/**
 * Migrates zones from YAWP to ArcadiaGuard format.
 * Uses reflection to access YAWP internals — zero compile dependency.
 * The migration is non-destructive: YAWP zones are NOT deleted afterward.
 */
public final class YawpMigrator {

    private YawpMigrator() {}

    /**
     * Reads all zones from YAWP via reflection and converts them to {@link ProtectedZone}.
     * Returns an empty list if YAWP is not present or reflection fails.
     *
     * @param server the running Minecraft server
     * @return the list of migrated zones, never null
     */
    private static final String MIGRATION_MARKER = ".yawp-migrated";

    public static List<ProtectedZone> migrate(MinecraftServer server) {
        List<ProtectedZone> result = new ArrayList<>();
        Path marker = ArcadiaGuardPaths.configRoot().resolve(MIGRATION_MARKER);
        if (Files.exists(marker)) {
            ArcadiaGuard.LOGGER.info("[ArcadiaGuard] YAWP migration already completed — skipping.");
            return result;
        }
        try {
            // Get YAWP zone manager via its service/provider
            Object yawpProvider = ReflectionHelper.invokeStatic(
                "org.yawp.api.YawpAPI", "getZoneManager",
                new Class<?>[0]).orElse(null);
            if (yawpProvider == null) {
                ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] YAWP API not found — migration aborted.");
                return result;
            }

            Object allZones = ReflectionHelper.invoke(
                yawpProvider, "getAllZones", new Class<?>[0]).orElse(null);
            if (!(allZones instanceof Collection<?> zones)) {
                ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Could not read YAWP zones.");
                return result;
            }

            for (Object zone : zones) {
                try {
                    result.add(convertZone(zone));
                } catch (Exception e) {
                    ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Failed to convert YAWP zone: {}", e.getMessage());
                }
            }
            ArcadiaGuard.LOGGER.info("[ArcadiaGuard] YAWP migration: {} zone(s) imported.", result.size());
            try {
                Files.createDirectories(marker.getParent());
                Files.writeString(marker,
                    "migrated=" + java.time.Instant.now() + System.lineSeparator()
                        + "zones=" + result.size() + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            } catch (IOException io) {
                ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Could not write YAWP migration marker: {}", io.getMessage());
            }
        } catch (Exception e) {
            ArcadiaGuard.LOGGER.error("[ArcadiaGuard] YAWP migration failed", e);
        }
        return result;
    }

    private static ProtectedZone convertZone(Object yawpZone) {
        String name = (String) ReflectionHelper.invoke(yawpZone, "getName", new Class<?>[0]).orElse("unnamed");
        String dim  = (String) ReflectionHelper.invoke(yawpZone, "getDimension", new Class<?>[0]).orElse("minecraft:overworld");

        Object minPosObj = ReflectionHelper.invoke(yawpZone, "getMinPos", new Class<?>[0]).orElse(null);
        Object maxPosObj = ReflectionHelper.invoke(yawpZone, "getMaxPos", new Class<?>[0]).orElse(null);

        int minX = 0, minY = 0, minZ = 0, maxX = 0, maxY = 0, maxZ = 0;
        if (minPosObj != null) {
            minX = ReflectionHelper.intMethod(minPosObj, "getX");
            minY = ReflectionHelper.intMethod(minPosObj, "getY");
            minZ = ReflectionHelper.intMethod(minPosObj, "getZ");
        }
        if (maxPosObj != null) {
            maxX = ReflectionHelper.intMethod(maxPosObj, "getX");
            maxY = ReflectionHelper.intMethod(maxPosObj, "getY");
            maxZ = ReflectionHelper.intMethod(maxPosObj, "getZ");
        }

        return new ProtectedZone(name, dim,
            Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ),
            Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ),
            new HashSet<>());
    }
}
