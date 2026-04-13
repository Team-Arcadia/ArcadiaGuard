package com.arcadia.arcadiaguard.zone;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.ArcadiaGuardPaths;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class ExceptionZoneManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type FILE_TYPE = new TypeToken<Map<String, ArrayList<ExceptionZone>>>() {}.getType();
    private final Map<String, Map<String, ExceptionZone>> zonesByDimension = new LinkedHashMap<>();
    private Path file;

    public void reload(MinecraftServer server) {
        this.file = ArcadiaGuardPaths.exceptionsFile();
        this.zonesByDimension.clear();
        try {
            Files.createDirectories(this.file.getParent());
            if (!Files.exists(this.file)) {
                save();
                return;
            }
            try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
                Map<String, ArrayList<ExceptionZone>> raw = GSON.fromJson(reader, FILE_TYPE);
                if (raw == null) return;
                raw.forEach((dim, zones) -> {
                    Map<String, ExceptionZone> map = new LinkedHashMap<>();
                    for (ExceptionZone zone : zones) map.put(zone.name().toLowerCase(), zone);
                    this.zonesByDimension.put(dim, map);
                });
            }
        } catch (IOException e) {
            ArcadiaGuard.LOGGER.error("Failed to load ArcadiaGuard exception zones", e);
        }
    }

    public boolean isAllowed(ServerPlayer player, BlockPos pos, String featureKey) {
        Map<String, ExceptionZone> zones = this.zonesByDimension.get(dimensionKey(player));
        if (zones == null) return false;
        for (ExceptionZone zone : zones.values()) {
            if (zone.contains(dimensionKey(player), pos) && zone.allows(featureKey)) return true;
        }
        return false;
    }

    public Collection<ExceptionZone> zones(Level level) {
        return this.zonesByDimension.getOrDefault(dimensionKey(level), Map.of()).values();
    }

    public Optional<ExceptionZone> get(Level level, String name) {
        return Optional.ofNullable(this.zonesByDimension.getOrDefault(dimensionKey(level), Map.of()).get(name.toLowerCase()));
    }

    public boolean add(Level level, ExceptionZone zone) {
        Map<String, ExceptionZone> zones = this.zonesByDimension.computeIfAbsent(dimensionKey(level), ignored -> new LinkedHashMap<>());
        String key = zone.name().toLowerCase();
        if (zones.containsKey(key)) return false;
        zones.put(key, zone);
        save();
        return true;
    }

    public boolean remove(Level level, String name) {
        Map<String, ExceptionZone> zones = this.zonesByDimension.get(dimensionKey(level));
        if (zones == null || zones.remove(name.toLowerCase()) == null) return false;
        save();
        return true;
    }

    public boolean allow(Level level, String name, String featureKey) {
        Optional<ExceptionZone> zone = get(level, name);
        if (zone.isEmpty()) return false;
        boolean changed = zone.get().allowedFeatures().add(featureKey.toLowerCase());
        if (changed) save();
        return changed;
    }

    public boolean deny(Level level, String name, String featureKey) {
        Optional<ExceptionZone> zone = get(level, name);
        if (zone.isEmpty()) return false;
        boolean changed = zone.get().allowedFeatures().remove(featureKey.toLowerCase());
        if (changed) save();
        return changed;
    }

    private void save() {
        if (this.file == null) return;
        try {
            Map<String, ArrayList<ExceptionZone>> raw = new LinkedHashMap<>();
            this.zonesByDimension.forEach((dim, zones) -> raw.put(dim, new ArrayList<>(zones.values())));
            try (Writer writer = Files.newBufferedWriter(this.file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                GSON.toJson(raw, FILE_TYPE, writer);
            }
        } catch (IOException e) {
            ArcadiaGuard.LOGGER.error("Failed to save ArcadiaGuard exception zones", e);
        }
    }

    private String dimensionKey(Object levelOrPlayer) {
        Object level = levelOrPlayer instanceof Level ? levelOrPlayer : ReflectionHelper.invoke(levelOrPlayer, "serverLevel", new Class<?>[0]).orElse(null);
        if (level == null) return "unknown";
        Object dimension = ReflectionHelper.invoke(level, "dimension", new Class<?>[0]).orElse(null);
        Object location = dimension == null ? null : ReflectionHelper.invoke(dimension, "location", new Class<?>[0]).orElse(null);
        return location == null ? "unknown" : location.toString();
    }
}
