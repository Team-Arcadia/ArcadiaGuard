package com.arcadia.arcadiaguard.persist;

import com.arcadia.arcadiaguard.zone.DimensionFlagStore;
import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DimFlagSerializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DimFlagSerializer() {}

    public static void write(DimensionFlagStore store, Path file) throws IOException {
        writeSnapshot(store.snapshot(), file);
    }

    /**
     * Écrit un snapshot pré-construit. Sûr à appeler depuis un thread async — l'appelant
     * doit avoir copié les maps avant pour éviter ConcurrentModificationException.
     */
    public static void writeSnapshot(Map<String, Map<String, Object>> snapshot, Path file) throws IOException {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Map<String, Object>> dimEntry : snapshot.entrySet()) {
            JsonObject dimObj = new JsonObject();
            for (Map.Entry<String, Object> flag : dimEntry.getValue().entrySet()) {
                Object val = flag.getValue();
                if (val instanceof Boolean b) dimObj.addProperty(flag.getKey(), b);
                else if (val instanceof Integer i) dimObj.addProperty(flag.getKey(), i);
                else if (val instanceof String s) dimObj.addProperty(flag.getKey(), s);
                else if (val instanceof List<?> list) {
                    JsonArray arr = new JsonArray();
                    for (Object item : list) {
                        if (item != null) arr.add(String.valueOf(item));
                    }
                    dimObj.add(flag.getKey(), arr);
                }
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
                if (val.isJsonPrimitive()) {
                    JsonPrimitive prim = val.getAsJsonPrimitive();
                    if (prim.isBoolean()) store.setFlag(dimEntry.getKey(), flag.getKey(), prim.getAsBoolean());
                    else if (prim.isNumber()) store.setFlag(dimEntry.getKey(), flag.getKey(), prim.getAsInt());
                    else if (prim.isString()) store.setFlag(dimEntry.getKey(), flag.getKey(), prim.getAsString());
                } else if (val.isJsonArray()) {
                    ArrayList<String> list = new ArrayList<>();
                    for (JsonElement item : val.getAsJsonArray()) {
                        if (item != null && item.isJsonPrimitive()) {
                            JsonPrimitive prim = item.getAsJsonPrimitive();
                            if (prim.isString()) list.add(prim.getAsString());
                        }
                    }
                    store.setFlag(dimEntry.getKey(), flag.getKey(), list);
                }
            }
        }
    }
}
