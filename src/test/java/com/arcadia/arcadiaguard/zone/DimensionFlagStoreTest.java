package com.arcadia.arcadiaguard.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests {@link DimensionFlagStore} CRUD + immutability. */
class DimensionFlagStoreTest {

    @Test
    void flags_unknownDim_returnsEmpty() {
        var store = new DimensionFlagStore();
        assertTrue(store.flags("unknown").isEmpty());
    }

    @Test
    void setFlag_storesValue() {
        var store = new DimensionFlagStore();
        store.setFlag("minecraft:overworld", "pvp", false);
        assertEquals(false, store.flags("minecraft:overworld").get("pvp"));
    }

    @Test
    void setFlag_overwritesPrevious() {
        var store = new DimensionFlagStore();
        store.setFlag("minecraft:overworld", "pvp", true);
        store.setFlag("minecraft:overworld", "pvp", false);
        assertEquals(false, store.flags("minecraft:overworld").get("pvp"));
    }

    @Test
    void resetFlag_removesValue() {
        var store = new DimensionFlagStore();
        store.setFlag("minecraft:overworld", "pvp", false);
        store.resetFlag("minecraft:overworld", "pvp");
        assertFalse(store.flags("minecraft:overworld").containsKey("pvp"));
    }

    @Test
    void resetFlag_unknownDim_noThrow() {
        var store = new DimensionFlagStore();
        store.resetFlag("unknown", "pvp"); // doit juste ne rien faire
    }

    @Test
    void all_returnsUnmodifiableMap() {
        var store = new DimensionFlagStore();
        store.setFlag("dim1", "pvp", false);
        assertThrows(UnsupportedOperationException.class,
            () -> store.all().put("dim2", new HashMap<>()));
    }

    @Test
    void all_reflectsStoreState() {
        var store = new DimensionFlagStore();
        store.setFlag("dim1", "a", true);
        store.setFlag("dim2", "b", false);
        assertEquals(2, store.all().size());
        assertTrue(store.all().containsKey("dim1"));
        assertTrue(store.all().containsKey("dim2"));
    }

    @Test
    void clear_removesAll() {
        var store = new DimensionFlagStore();
        store.setFlag("dim1", "pvp", false);
        store.setFlag("dim2", "pvp", false);
        store.clear();
        assertTrue(store.all().isEmpty());
    }

    @Test
    void putAll_mergesData() {
        var store = new DimensionFlagStore();
        Map<String, Map<String, Object>> data = new HashMap<>();
        data.put("dim1", Map.of("pvp", false, "mob-spawn", true));
        data.put("dim2", Map.of("pvp", true));
        store.putAll(data);
        assertEquals(2, store.all().size());
        assertEquals(false, store.flags("dim1").get("pvp"));
        assertEquals(true, store.flags("dim1").get("mob-spawn"));
    }

    @Test
    void multipleDims_independent() {
        var store = new DimensionFlagStore();
        store.setFlag("overworld", "pvp", false);
        store.setFlag("nether", "pvp", true);
        assertEquals(false, store.flags("overworld").get("pvp"));
        assertEquals(true, store.flags("nether").get("pvp"));
    }

    @Test
    void setFlag_acceptsDifferentTypes() {
        var store = new DimensionFlagStore();
        store.setFlag("dim", "boolFlag", true);
        store.setFlag("dim", "intFlag", 42);
        store.setFlag("dim", "listFlag", java.util.List.of("a", "b"));
        var flags = store.flags("dim");
        assertEquals(true, flags.get("boolFlag"));
        assertEquals(42, flags.get("intFlag"));
        assertEquals(java.util.List.of("a", "b"), flags.get("listFlag"));
    }

    // ── Regression 1.5.5 fix #7 : snapshot() pour écriture async ──

    @Test
    void snapshot_isDeepCopy_mutationsToLiveStoreDoNotLeak() {
        var store = new DimensionFlagStore();
        store.setFlag("overworld", "pvp", false);
        var snap = store.snapshot();

        // Mutation après snapshot ne doit pas être visible dans le snapshot.
        store.setFlag("overworld", "pvp", true);
        store.setFlag("overworld", "newFlag", 1);
        store.setFlag("nether", "pvp", false);

        assertEquals(false, snap.get("overworld").get("pvp"),
            "snapshot doit être indépendant du store post-mutation");
        assertFalse(snap.get("overworld").containsKey("newFlag"));
        assertFalse(snap.containsKey("nether"));
    }

    @Test
    void snapshot_isMutable_safeForWriterThread() {
        var store = new DimensionFlagStore();
        store.setFlag("overworld", "pvp", false);
        var snap = store.snapshot();
        // Le snapshot peut être muté librement (ne doit pas être un unmodifiable).
        snap.put("nether", new HashMap<>());
        snap.get("overworld").put("test", true);
        // Le store live ne doit pas être affecté.
        assertFalse(store.all().containsKey("nether"));
        assertFalse(store.flags("overworld").containsKey("test"));
    }

    @Test
    void snapshot_emptyStore_returnsEmpty() {
        var store = new DimensionFlagStore();
        assertTrue(store.snapshot().isEmpty());
    }
}
