package com.arcadia.arcadiaguard.client;

import com.arcadia.arcadiaguard.network.ZoneDataPayload.ClientZoneInfo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public final class ClientZoneCache {

    // volatile: read from render thread, written from network thread
    private static volatile Map<String, ClientZoneInfo> zoneMap = new LinkedHashMap<>();
    @Nullable private static volatile BlockPos wandPos1 = null;
    @Nullable private static volatile BlockPos wandPos2 = null;

    private ClientZoneCache() {}

    /** Replace entire cache (compat alias for replaceAll). */
    public static void update(List<ClientZoneInfo> received) {
        replaceAll(received);
    }

    /** Replace entire cache with the given list. */
    public static void replaceAll(List<ClientZoneInfo> received) {
        Map<String, ClientZoneInfo> map = new LinkedHashMap<>();
        for (ClientZoneInfo info : received) map.put(info.name(), info);
        zoneMap = map;
    }

    /** Add or replace a single zone entry by name. */
    public static void add(ClientZoneInfo info) {
        Map<String, ClientZoneInfo> map = new LinkedHashMap<>(zoneMap);
        map.put(info.name(), info);
        zoneMap = map;
    }

    /** Remove a zone entry by name. */
    public static void remove(String name) {
        Map<String, ClientZoneInfo> map = new LinkedHashMap<>(zoneMap);
        map.remove(name);
        zoneMap = map;
    }

    /** Clear all zone entries (keeps wand positions). */
    public static void clear() {
        zoneMap = new LinkedHashMap<>();
        wandPos1 = null;
        wandPos2 = null;
    }

    public static void updateWandPositions(@Nullable BlockPos pos1, @Nullable BlockPos pos2) {
        wandPos1 = pos1;
        wandPos2 = pos2;
    }

    public static List<ClientZoneInfo> zones() { return new ArrayList<>(zoneMap.values()); }

    @Nullable public static BlockPos wandPos1() { return wandPos1; }
    @Nullable public static BlockPos wandPos2() { return wandPos2; }
}
