package com.arcadia.arcadiaguard.zone;

import com.arcadia.arcadiaguard.util.ReflectionHelper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

public final class YawpZoneProvider implements ZoneProvider {

    private static final String MOD_ID = "yawp";
    private static final String REGION_MANAGER_CLASS = "de.z0rdak.yawp.api.core.RegionManager";
    private static final String MEMBER_GROUP = "members";
    private static final String OWNER_GROUP = "owners";

    @Override
    public String name() {
        return MOD_ID;
    }

    public boolean isAvailable() {
        return ModList.get().isLoaded(MOD_ID);
    }

    @Override
    public void reload(MinecraftServer server) {}

    @Override
    public ZoneCheckResult check(ServerPlayer player, BlockPos pos) {
        if (!isAvailable()) return ZoneCheckResult.allowed();
        Object level = ReflectionHelper.invoke(player, "serverLevel", new Class<?>[0]).orElse(null);
        if (!(level instanceof Level serverLevel)) return ZoneCheckResult.allowed();
        return regionsAt(serverLevel, pos).stream()
            .filter(this::isActive)
            .max(Comparator.comparingInt(this::priority))
            .map(region -> permits(region, player)
                ? ZoneCheckResult.allowed()
                : new ZoneCheckResult(true, regionName(region), name()))
            .orElseGet(ZoneCheckResult::allowed);
    }

    @Override
    public Collection<ProtectedZone> zones(Level level) {
        if (!isAvailable()) return List.of();
        List<ProtectedZone> zones = new ArrayList<>();
        for (Object region : allRegions(level)) {
            toProtectedZone(level, region).ifPresent(zones::add);
        }
        return zones;
    }

    @Override
    public Optional<ProtectedZone> get(Level level, String name) {
        if (!isAvailable()) return Optional.empty();
        Object api = regionApi(level);
        if (api == null) return Optional.empty();
        Object regionOpt = ReflectionHelper.invoke(api, "getLocalRegion", new Class<?>[] { String.class }, name).orElse(null);
        Object region = unwrapOptional(regionOpt);
        return region == null ? Optional.empty() : toProtectedZone(level, region);
    }

    @Override
    public boolean add(Level level, ProtectedZone zone) {
        return false;
    }

    @Override
    public boolean remove(Level level, String name) {
        return false;
    }

    @Override
    public boolean whitelistAdd(Level level, String name, UUID playerId, @Nullable String playerName) {
        return mutateWhitelist(level, name, playerId, playerName, true);
    }

    @Override
    public boolean whitelistRemove(Level level, String name, UUID playerId, @Nullable String playerName) {
        return mutateWhitelist(level, name, playerId, playerName, false);
    }

    private boolean mutateWhitelist(Level level, String name, UUID playerId, @Nullable String playerName, boolean add) {
        if (!isAvailable()) return false;
        Object api = regionApi(level);
        if (api == null) return false;
        Object regionOpt = ReflectionHelper.invoke(api, "getLocalRegion", new Class<?>[] { String.class }, name).orElse(null);
        Object region = unwrapOptional(regionOpt);
        if (region == null) return false;
        String playerLabel = playerName == null || playerName.isBlank() ? playerId.toString() : playerName;
        boolean changed = add
            ? !ReflectionHelper.boolMethod(region, "hasPlayer", new Class<?>[] { UUID.class, String.class }, playerId, MEMBER_GROUP)
                && ReflectionHelper.invoke(region, "addPlayer", new Class<?>[] { UUID.class, String.class, String.class }, playerId, playerLabel, MEMBER_GROUP).isPresent()
            : ReflectionHelper.boolMethod(region, "hasPlayer", new Class<?>[] { UUID.class, String.class }, playerId, MEMBER_GROUP)
                && ReflectionHelper.invoke(region, "removePlayer", new Class<?>[] { UUID.class, String.class }, playerId, MEMBER_GROUP).isPresent();
        if (changed) {
            ReflectionHelper.invoke(api, "save", new Class<?>[0]);
        }
        return changed;
    }

    private Collection<Object> allRegions(Level level) {
        Object api = regionApi(level);
        if (api == null) return List.of();
        Object regions = ReflectionHelper.invoke(api, "getAllLocalRegions", new Class<?>[0]).orElse(null);
        if (regions instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return List.of();
    }

    private List<Object> regionsAt(Level level, BlockPos pos) {
        Object api = regionApi(level);
        if (api == null) return List.of();
        Object posObject = pos;
        Object regions = ReflectionHelper.invoke(api, "getRegionsAt", new Class<?>[] { posObject.getClass() }, posObject).orElse(null);
        if (regions instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (regions instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return List.of();
    }

    private Object regionApi(Level level) {
        Object manager = ReflectionHelper.invokeStatic(REGION_MANAGER_CLASS, "get", new Class<?>[0]).orElse(null);
        if (manager == null) return null;
        Object dimension = ReflectionHelper.invoke(level, "dimension", new Class<?>[0]).orElse(null);
        if (dimension == null) return null;
        Object apiOpt = ReflectionHelper.invoke(manager, "getDimRegionApi", new Class<?>[] { dimension.getClass() }, dimension).orElse(null);
        return unwrapOptional(apiOpt);
    }

    private Optional<ProtectedZone> toProtectedZone(Level level, Object region) {
        Object area = ReflectionHelper.invoke(region, "getArea", new Class<?>[0]).orElse(null);
        if (area == null) return Optional.empty();
        Object p1 = ReflectionHelper.invoke(area, "getAreaP1", new Class<?>[0]).orElse(null);
        Object p2 = ReflectionHelper.invoke(area, "getAreaP2", new Class<?>[0]).orElse(null);
        if (!(p1 instanceof BlockPos a) || !(p2 instanceof BlockPos b)) return Optional.empty();
        int minX = Math.min(ReflectionHelper.intMethod(a, "getX"), ReflectionHelper.intMethod(b, "getX"));
        int minY = Math.min(ReflectionHelper.intMethod(a, "getY"), ReflectionHelper.intMethod(b, "getY"));
        int minZ = Math.min(ReflectionHelper.intMethod(a, "getZ"), ReflectionHelper.intMethod(b, "getZ"));
        int maxX = Math.max(ReflectionHelper.intMethod(a, "getX"), ReflectionHelper.intMethod(b, "getX"));
        int maxY = Math.max(ReflectionHelper.intMethod(a, "getY"), ReflectionHelper.intMethod(b, "getY"));
        int maxZ = Math.max(ReflectionHelper.intMethod(a, "getZ"), ReflectionHelper.intMethod(b, "getZ"));
        return Optional.of(new ProtectedZone(
            regionName(region),
            dimensionKey(level),
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            whitelistedPlayers(region)
        ));
    }

    private Set<UUID> whitelistedPlayers(Object region) {
        Set<UUID> players = new LinkedHashSet<>();
        players.addAll(groupPlayers(region, MEMBER_GROUP));
        players.addAll(groupPlayers(region, OWNER_GROUP));
        return players;
    }

    private Set<UUID> groupPlayers(Object region, String groupName) {
        Object group = ReflectionHelper.invoke(region, "getGroup", new Class<?>[] { String.class }, groupName).orElse(null);
        Object players = group == null ? null : ReflectionHelper.invoke(group, "getPlayers", new Class<?>[0]).orElse(null);
        if (!(players instanceof java.util.Map<?, ?> map)) return Set.of();
        Set<UUID> uuids = new LinkedHashSet<>();
        for (Object key : map.keySet()) {
            if (key instanceof UUID uuid) uuids.add(uuid);
        }
        return uuids;
    }

    private boolean permits(Object region, ServerPlayer player) {
        Object playerObject = player;
        return ReflectionHelper.boolMethod(region, "permits", new Class<?>[] { playerObject.getClass() }, playerObject);
    }

    private boolean isActive(Object region) {
        return ReflectionHelper.boolMethod(region, "isActive", new Class<?>[0]);
    }

    private int priority(Object region) {
        return ReflectionHelper.intMethod(region, "getPriority");
    }

    private String regionName(Object region) {
        return String.valueOf(ReflectionHelper.invoke(region, "getName", new Class<?>[0]).orElse("unknown"));
    }

    private String dimensionKey(Level level) {
        Object dimension = ReflectionHelper.invoke(level, "dimension", new Class<?>[0]).orElse(null);
        Object location = dimension == null ? null : ReflectionHelper.invoke(dimension, "location", new Class<?>[0]).orElse(null);
        return location == null ? "unknown" : location.toString();
    }

    private Object unwrapOptional(Object value) {
        if (!(value instanceof Optional<?> optional)) return null;
        return optional.orElse(null);
    }
}
