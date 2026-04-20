package com.arcadia.arcadiaguard.persist;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.api.flag.IntFlag;
import com.arcadia.arcadiaguard.api.flag.ListFlag;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import com.arcadia.arcadiaguard.zone.ZoneRole;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles reading and writing a single {@link ProtectedZone} to/from a JSON file.
 * Each zone is stored as {@code config/arcadiaguard/zones/<dim>/<name>.json}.
 */
public final class ZoneSerializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ZoneSerializer() {}

    public static void write(ProtectedZone zone, Path file) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("format_version", ZoneMigrator.CURRENT_VERSION);
        json.addProperty("name", zone.name());
        json.addProperty("dimension", zone.dimension());
        json.addProperty("min_x", zone.minX());
        json.addProperty("min_y", zone.minY());
        json.addProperty("min_z", zone.minZ());
        json.addProperty("max_x", zone.maxX());
        json.addProperty("max_y", zone.maxY());
        json.addProperty("max_z", zone.maxZ());
        json.add("parent", zone.parent() != null ? GSON.toJsonTree(zone.parent()) : JsonNull.INSTANCE);
        json.addProperty("priority", zone.priority());
        json.addProperty("enabled", zone.enabled());
        json.addProperty("inherit_dim_flags", zone.inheritDimFlags());

        JsonObject flagsJson = new JsonObject();
        for (Map.Entry<String, Object> entry : zone.flagValues().entrySet()) {
            Object val = entry.getValue();
            if (val instanceof Boolean b) flagsJson.addProperty(entry.getKey(), b);
            else if (val instanceof Integer i) flagsJson.addProperty(entry.getKey(), i);
            else if (val instanceof List<?> list) {
                JsonArray arr = new JsonArray();
                for (Object item : list) { if (item != null) arr.add(item.toString()); }
                flagsJson.add(entry.getKey(), arr);
            }
        }
        json.add("flags", flagsJson);

        JsonArray whitelist = new JsonArray();
        for (UUID id : zone.whitelistedPlayers()) {
            whitelist.add(id.toString());
        }
        json.add("whitelist", whitelist);

        // Member roles
        JsonObject rolesJson = new JsonObject();
        for (Map.Entry<UUID, ZoneRole> entry : zone.memberRoles().entrySet()) {
            rolesJson.addProperty(entry.getKey().toString(), entry.getValue().name());
        }
        json.add("member_roles", rolesJson);

        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            GSON.toJson(json, writer);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static ProtectedZone read(Path file) throws IOException {
        long fileSize = Files.size(file);
        if (fileSize > 1_000_000L) {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Zone file {} is suspiciously large ({} bytes > 1 MB), skipping.",
                file.getFileName(), fileSize);
            return null;
        }
        JsonObject json;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            try {
                JsonElement root = JsonParser.parseReader(reader);
                if (root == null || !root.isJsonObject()) {
                    throw new IOException("Root element is not a JSON object");
                }
                json = root.getAsJsonObject();
            } catch (com.google.gson.JsonParseException | IllegalStateException e) {
                throw new IOException("Malformed zone JSON: " + e.getMessage(), e);
            }
        }

        boolean migrated = ZoneMigrator.migrate(json);

        String name;
        String dimension;
        int minX, minY, minZ, maxX, maxY, maxZ;
        String parent;
        int priority;
        try {
            name      = requireString(json, "name");
            dimension = requireString(json, "dimension");
            minX = json.get("min_x").getAsInt();
            minY = json.get("min_y").getAsInt();
            minZ = json.get("min_z").getAsInt();
            maxX = json.get("max_x").getAsInt();
            maxY = json.get("max_y").getAsInt();
            maxZ = json.get("max_z").getAsInt();
            parent = json.has("parent") && !json.get("parent").isJsonNull()
                     ? json.get("parent").getAsString() : null;
            priority  = json.has("priority") ? json.get("priority").getAsInt() : 0;
        } catch (Exception e) {
            throw new IOException("Missing or invalid zone field: " + e.getMessage(), e);
        }

        // Defensive normalization: accept swapped corners rather than producing a
        // zone whose contains() always returns false. Global zones keep MIN_VALUE.
        if (minX != Integer.MIN_VALUE) {
            if (minX > maxX) { int t = minX; minX = maxX; maxX = t; }
            if (minY > maxY) { int t = minY; minY = maxY; maxY = t; }
            if (minZ > maxZ) { int t = minZ; minZ = maxZ; maxZ = t; }
        }

        Set<UUID> whitelist = new HashSet<>();
        String zoneName = json.has("name") ? json.get("name").getAsString() : "<unknown>";
        if (json.has("whitelist")) {
            for (var el : json.getAsJsonArray("whitelist")) {
                String raw = el.getAsString();
                try { whitelist.add(UUID.fromString(raw)); }
                catch (IllegalArgumentException e) {
                    ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Invalid UUID in whitelist for zone {}: {}", zoneName, raw);
                }
            }
        }

        Map<String, Object> flagValues = new LinkedHashMap<>();
        if (json.has("flags") && json.get("flags").isJsonObject()) {
            JsonObject flagsJson = json.getAsJsonObject("flags");
            for (Map.Entry<String, JsonElement> entry : flagsJson.entrySet()) {
                ArcadiaGuard.flagRegistry().get(entry.getKey()).ifPresent(flag ->
                    parseFlagValue(flag, entry.getValue(), flagValues));
            }
        }

        Map<UUID, ZoneRole> memberRoles = new LinkedHashMap<>();
        if (json.has("member_roles") && json.get("member_roles").isJsonObject()) {
            for (var entry : json.getAsJsonObject("member_roles").entrySet()) {
                String raw = entry.getValue().getAsString();
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    ZoneRole role = ZoneRole.valueOf(raw);
                    memberRoles.put(uuid, role);
                } catch (Exception e) {
                    ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Skipping invalid member_role entry in zone {}: {}={}",
                        zoneName, entry.getKey(), raw);
                }
            }
        }

        ProtectedZone zone = new ProtectedZone(name, dimension, minX, minY, minZ,
                                               maxX, maxY, maxZ, whitelist, parent, priority,
                                               flagValues, memberRoles);
        zone.setEnabled(json.has("enabled") ? json.get("enabled").getAsBoolean() : true);
        zone.setInheritDimFlags(json.has("inherit_dim_flags") ? json.get("inherit_dim_flags").getAsBoolean() : false);
        if (migrated) {
            Path bak = file.resolveSibling(file.getFileName().toString().replaceFirst("\\.json$", "") + ".bak");
            if (!Files.exists(bak)) {
                try {
                    Files.copy(file, bak, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Could not create migration backup for {}: {}", file.getFileName(), e.getMessage());
                }
            }
            write(zone, file);
        }
        return zone;
    }

    private static String requireString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            throw new IllegalStateException("missing '" + key + "'");
        }
        return json.get(key).getAsString();
    }

    private static void parseFlagValue(Flag<?> flag, JsonElement element, Map<String, Object> out) {
        try {
            if (flag instanceof BooleanFlag) {
                out.put(flag.id(), element.getAsBoolean());
            } else if (flag instanceof IntFlag) {
                out.put(flag.id(), element.getAsInt());
            } else if (flag instanceof ListFlag && element.isJsonArray()) {
                List<String> list = new ArrayList<>();
                for (var el : element.getAsJsonArray()) list.add(el.getAsString());
                out.put(flag.id(), list);
            }
        } catch (Exception e) {
            ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Could not parse flag '{}': {}", flag.id(), e.getMessage());
        }
    }
}
