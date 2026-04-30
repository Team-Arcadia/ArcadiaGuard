package com.arcadia.arcadiaguard.persist;

import static org.junit.jupiter.api.Assertions.*;

import com.arcadia.arcadiaguard.zone.DimensionFlagStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests DimFlagSerializer round-trip + nouvelle méthode writeSnapshot (1.5.5).
 */
class DimFlagSerializerTest {

    @Test
    void writeAndRead_boolFlag(@TempDir Path tempDir) throws IOException {
        var store = new DimensionFlagStore();
        store.setFlag("minecraft:overworld", "pvp", false);

        Path file = tempDir.resolve("dim-flags.json");
        DimFlagSerializer.write(store, file);

        var loaded = new DimensionFlagStore();
        DimFlagSerializer.read(loaded, file);

        assertEquals(false, loaded.flags("minecraft:overworld").get("pvp"));
    }

    @Test
    void writeAndRead_intFlag(@TempDir Path tempDir) throws IOException {
        var store = new DimensionFlagStore();
        store.setFlag("minecraft:overworld", "heal-amount", 10);

        Path file = tempDir.resolve("dim-flags.json");
        DimFlagSerializer.write(store, file);

        var loaded = new DimensionFlagStore();
        DimFlagSerializer.read(loaded, file);

        assertEquals(10, loaded.flags("minecraft:overworld").get("heal-amount"));
    }

    @Test
    void read_missingFile_keepsStoreEmpty(@TempDir Path tempDir) throws IOException {
        var store = new DimensionFlagStore();
        DimFlagSerializer.read(store, tempDir.resolve("does-not-exist.json"));
        assertTrue(store.all().isEmpty());
    }

    @Test
    void read_corruptedFile_keepsStoreEmpty(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("dim-flags.json");
        java.nio.file.Files.writeString(file, "not json {{{ ");
        var store = new DimensionFlagStore();
        DimFlagSerializer.read(store, file);
        assertTrue(store.all().isEmpty());
    }

    // ── Regression 1.5.5 fix #7 : writeSnapshot pour écriture async ──

    @Test
    void writeSnapshot_persistsExternalMap(@TempDir Path tempDir) throws IOException {
        Map<String, Map<String, Object>> snapshot = new LinkedHashMap<>();
        var dimMap = new LinkedHashMap<String, Object>();
        dimMap.put("pvp", false);
        dimMap.put("heal-amount", 5);
        snapshot.put("minecraft:overworld", dimMap);

        Path file = tempDir.resolve("dim-flags.json");
        DimFlagSerializer.writeSnapshot(snapshot, file);

        var loaded = new DimensionFlagStore();
        DimFlagSerializer.read(loaded, file);

        assertEquals(false, loaded.flags("minecraft:overworld").get("pvp"));
        assertEquals(5, loaded.flags("minecraft:overworld").get("heal-amount"));
    }

    @Test
    void writeSnapshot_sameOutputAsWrite(@TempDir Path tempDir) throws IOException {
        var store = new DimensionFlagStore();
        store.setFlag("minecraft:overworld", "pvp", false);
        store.setFlag("minecraft:nether", "mob-spawn", true);

        Path fileA = tempDir.resolve("a.json");
        Path fileB = tempDir.resolve("b.json");
        DimFlagSerializer.write(store, fileA);
        DimFlagSerializer.writeSnapshot(store.snapshot(), fileB);

        assertEquals(java.nio.file.Files.readString(fileA),
                     java.nio.file.Files.readString(fileB),
            "writeSnapshot doit produire le même JSON que write");
    }
}
