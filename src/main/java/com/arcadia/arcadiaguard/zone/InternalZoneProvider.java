package com.arcadia.arcadiaguard.zone;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.ArcadiaGuardPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class InternalZoneProvider implements ZoneProvider {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type FILE_TYPE = new TypeToken<Map<String, ArrayList<ProtectedZone>>>() {}.getType();

    private final Map<String, Map<String, ProtectedZone>> zonesByDimension = new LinkedHashMap<>();
    private Path file;

    @Override
    public String name() {
        return "internal";
    }

    @Override
    public void reload(MinecraftServer server) {
        this.file = ArcadiaGuardPaths.zonesFile();
        this.zonesByDimension.clear();
        try {
            Files.createDirectories(this.file.getParent());
            if (!Files.exists(this.file)) {
                save();
                return;
            }
            try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
                Map<String, ArrayList<ProtectedZone>> raw = GSON.fromJson(reader, FILE_TYPE);
                if (raw == null) {
                    return;
                }
                raw.forEach((dim, zones) -> {
                    Map<String, ProtectedZone> map = new LinkedHashMap<>();
                    for (ProtectedZone zone : zones) {
                        map.put(zone.name().toLowerCase(), zone);
                    }
                    this.zonesByDimension.put(dim, map);
                });
            }
        } catch (IOException e) {
            ArcadiaGuard.LOGGER.error("Failed to load internal ArcadiaGuard zones", e);
        }
    }

    @Override
    public ZoneCheckResult check(ServerPlayer player, BlockPos pos) {
        String dim = dimensionKey(player);
        Map<String, ProtectedZone> zones = this.zonesByDimension.get(dim);
        if (zones == null) return ZoneCheckResult.allowed();
        for (ProtectedZone zone : zones.values()) {
            if (zone.contains(dim, pos) && !zone.whitelistedPlayers().contains(playerId(player))) {
                return new ZoneCheckResult(true, zone.name(), name());
            }
        }
        return ZoneCheckResult.allowed();
    }

    @Override
    public Collection<ProtectedZone> zones(Level level) {
        return this.zonesByDimension.getOrDefault(dimensionKey(level), Map.of()).values();
    }

    @Override
    public Optional<ProtectedZone> get(Level level, String name) {
        return Optional.ofNullable(this.zonesByDimension.getOrDefault(dimensionKey(level), Map.of()).get(name.toLowerCase()));
    }

    @Override
    public boolean add(Level level, ProtectedZone zone) {
        Map<String, ProtectedZone> zones = this.zonesByDimension.computeIfAbsent(dimensionKey(level), ignored -> new LinkedHashMap<>());
        String key = zone.name().toLowerCase();
        if (zones.containsKey(key)) return false;
        zones.put(key, zone);
        save();
        return true;
    }

    @Override
    public boolean remove(Level level, String name) {
        Map<String, ProtectedZone> zones = this.zonesByDimension.get(dimensionKey(level));
        if (zones == null || zones.remove(name.toLowerCase()) == null) return false;
        save();
        return true;
    }

    @Override
    public boolean whitelistAdd(Level level, String name, UUID playerId, @Nullable String playerName) {
        Optional<ProtectedZone> zone = get(level, name);
        if (zone.isEmpty()) return false;
        boolean changed = zone.get().whitelistAdd(playerId);
        if (changed) save();
        return changed;
    }

    @Override
    public boolean whitelistRemove(Level level, String name, UUID playerId, @Nullable String playerName) {
        Optional<ProtectedZone> zone = get(level, name);
        if (zone.isEmpty()) return false;
        boolean changed = zone.get().whitelistRemove(playerId);
        if (changed) save();
        return changed;
    }

    private void save() {
        if (this.file == null) return;
        try {
            Map<String, ArrayList<ProtectedZone>> raw = new LinkedHashMap<>();
            this.zonesByDimension.forEach((dim, zones) -> raw.put(dim, new ArrayList<>(zones.values())));
            try (Writer writer = Files.newBufferedWriter(this.file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                GSON.toJson(raw, FILE_TYPE, writer);
            }
        } catch (IOException e) {
            ArcadiaGuard.LOGGER.error("Failed to save internal ArcadiaGuard zones", e);
        }
    }

    private String dimensionKey(Object levelOrPlayer) {
        Object level = levelOrPlayer instanceof Level ? levelOrPlayer : ReflectionHelper.invoke(levelOrPlayer, "serverLevel", new Class<?>[0]).orElse(null);
        if (level == null) return "unknown";
        Object dimension = ReflectionHelper.invoke(level, "dimension", new Class<?>[0]).orElse(null);
        if (dimension == null) return "unknown";
        Object location = ReflectionHelper.invoke(dimension, "location", new Class<?>[0]).orElse(null);
        return location == null ? "unknown" : location.toString();
    }

    private UUID playerId(ServerPlayer player) {
        return player.getUUID();
    }
}
