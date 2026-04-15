package com.arcadia.arcadiaguard.item;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Thread-safe list of item IDs dynamically blocked in protected zones.
 * Persisted to config/arcadia/ArcadiaGuard/arcadiaguard-blocked-items.json.
 * Managed in-game via /arcadiaguard item block|unblock|list.
 */
public final class DynamicItemBlockList {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String KEY = "blocked_items";

    private final Set<ResourceLocation> blocked = ConcurrentHashMap.newKeySet();
    private final Path file;

    public DynamicItemBlockList(Path file) {
        this.file = file;
    }

    public void load() {
        blocked.clear();
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file)) {
            Map<String, List<String>> data = GSON.fromJson(r, new TypeToken<Map<String, List<String>>>(){}.getType());
            if (data != null && data.containsKey(KEY)) {
                for (String id : data.get(KEY)) {
                    ResourceLocation loc = ResourceLocation.tryParse(id);
                    if (loc != null) blocked.add(loc);
                }
            }
        } catch (IOException e) {
            ArcadiaGuard.LOGGER.error("Failed to load blocked items list", e);
        }
    }

    /** @return true if the item was added (wasn't already present) */
    public boolean add(ResourceLocation item) {
        boolean added = blocked.add(item);
        if (added) save();
        return added;
    }

    /** @return true if the item was removed */
    public boolean remove(ResourceLocation item) {
        boolean removed = blocked.remove(item);
        if (removed) save();
        return removed;
    }

    public boolean contains(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && blocked.contains(key);
    }

    /** Returns a sorted, immutable snapshot of blocked item IDs. */
    public List<ResourceLocation> list() {
        List<ResourceLocation> sorted = new ArrayList<>(blocked);
        Collections.sort(sorted);
        return Collections.unmodifiableList(sorted);
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            // Keep sorted in file for readability
            List<String> ids = new ArrayList<>();
            new TreeSet<>(blocked).forEach(loc -> ids.add(loc.toString()));
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(Map.of(KEY, ids), w);
            }
        } catch (IOException e) {
            ArcadiaGuard.LOGGER.error("Failed to save blocked items list", e);
        }
    }
}
