package com.arcadia.arcadiaguard.zone;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stores per-dimension flag overrides (global fallback after zone parent chain). */
public final class DimensionFlagStore {

    private final Map<String, Map<String, Object>> store = new LinkedHashMap<>();

    public Map<String, Object> flags(String dimKey) {
        return store.getOrDefault(dimKey, Map.of());
    }

    public void setFlag(String dimKey, String flagId, Object value) {
        store.computeIfAbsent(dimKey, k -> new LinkedHashMap<>()).put(flagId, value);
    }

    public void resetFlag(String dimKey, String flagId) {
        Map<String, Object> flags = store.get(dimKey);
        if (flags != null) flags.remove(flagId);
    }

    public Map<String, Map<String, Object>> all() {
        return Collections.unmodifiableMap(store);
    }

    /**
     * Returns a deep, immutable snapshot of the store, safe to consume from a writer thread
     * while the main thread continues to mutate the live store.
     */
    public Map<String, Map<String, Object>> snapshot() {
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        for (var e : store.entrySet()) copy.put(e.getKey(), new LinkedHashMap<>(e.getValue()));
        return copy;
    }

    public void clear() {
        store.clear();
    }

    public void putAll(Map<String, Map<String, Object>> data) {
        store.putAll(data);
    }
}
