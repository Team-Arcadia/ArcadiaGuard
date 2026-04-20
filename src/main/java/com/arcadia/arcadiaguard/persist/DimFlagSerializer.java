package com.arcadia.arcadiaguard.persist;

import com.arcadia.arcadiaguard.zone.DimensionFlagStore;
import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public final class DimFlagSerializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DimFlagSerializer() {}

    public static void write(DimensionFlagStore store, Path file) throws IOException {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Map<String, Object>> dimEntry : store.all().entrySet()) {
            JsonObject dimObj = new JsonObject();
            for (Map.Entry<String, Object> flag : dimEntry.getValue().entrySet()) {
                Object val = flag.getValue();
                if (val instanceof Boolean b) dimObj.addProperty(flag.getKey(), b);
                else if (val instanceof Integer i) dimObj.addProperty(flag.getKey(), i);
            }
            root.add(dimEntry.getKey(), dimObj);
        }
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            GSON.toJson(root, writer);
        }
    }

    public static void read(DimensionFlagStore store, Path file) throws IOException {
        if (!Files.exists(file)) return;
        JsonObject root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            try {
                JsonElement el = JsonParser.parseReader(reader);
                if (el == null || !el.isJsonObject()) {
                    ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Dim-flag file {} is not a JSON object — keeping store empty", file.getFileName());
                    return;
                }
                root = el.getAsJsonObject();
            } catch (JsonParseException | IllegalStateException e) {
                ArcadiaGuard.LOGGER.warn("[ArcadiaGuard] Corrupted dim-flag file {} — keeping store empty: {}", file.getFileName(), e.getMessage());
                return;
            }
        }
        store.clear();
        for (Map.Entry<String, JsonElement> dimEntry : root.entrySet()) {
            if (!dimEntry.getValue().isJsonObject()) continue;
            for (Map.Entry<String, JsonElement> flag : dimEntry.getValue().getAsJsonObject().entrySet()) {
                JsonElement val = flag.getValue();
                if (!val.isJsonPrimitive()) continue;
                JsonPrimitive prim = val.getAsJsonPrimitive();
                if (prim.isBoolean()) store.setFlag(dimEntry.getKey(), flag.getKey(), prim.getAsBoolean());
                else if (prim.isNumber()) store.setFlag(dimEntry.getKey(), flag.getKey(), prim.getAsInt());
            }
        }
    }
}
