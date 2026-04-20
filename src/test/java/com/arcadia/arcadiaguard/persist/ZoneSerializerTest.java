package com.arcadia.arcadiaguard.persist;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.flag.IntFlag;
import com.arcadia.arcadiaguard.flag.FlagRegistryImpl;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ZoneSerializerTest {

    @TempDir
    Path tempDir;

    // --- Round-trip sans flags (pas de mock ArcadiaGuard nécessaire) ---

    @Test
    void roundTrip_basicZone_fieldsPreserved() throws IOException {
        ProtectedZone original = new ProtectedZone("spawn", "minecraft:overworld",
            -50, 60, -50, 50, 255, 50, new HashSet<>(), null, 3);
        original.setEnabled(true);
        original.setInheritDimFlags(false);

        Path file = tempDir.resolve("spawn.json");
        ZoneSerializer.write(original, file);
        ProtectedZone loaded = ZoneSerializer.read(file);

        assertEquals("spawn", loaded.name());
        assertEquals("minecraft:overworld", loaded.dimension());
        assertEquals(-50, loaded.minX());
        assertEquals(60, loaded.minY());
        assertEquals(-50, loaded.minZ());
        assertEquals(50, loaded.maxX());
        assertEquals(255, loaded.maxY());
        assertEquals(50, loaded.maxZ());
        assertEquals(3, loaded.priority());
        assertTrue(loaded.enabled());
        assertFalse(loaded.inheritDimFlags());
    }

    @Test
    void roundTrip_withParent_preserved() throws IOException {
        ProtectedZone zone = new ProtectedZone("child", "minecraft:the_end",
            0, 0, 0, 9, 9, 9, new HashSet<>(), "parent-zone", 0);

        Path file = tempDir.resolve("child.json");
        ZoneSerializer.write(zone, file);
        ProtectedZone loaded = ZoneSerializer.read(file);

        assertEquals("parent-zone", loaded.parent());
    }

    @Test
    void roundTrip_whitelist_preserved() throws IOException {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        ProtectedZone zone = new ProtectedZone("protected", "minecraft:overworld",
            0, 0, 0, 9, 9, 9, new HashSet<>(java.util.List.of(player1, player2)));

        Path file = tempDir.resolve("protected.json");
        ZoneSerializer.write(zone, file);
        ProtectedZone loaded = ZoneSerializer.read(file);

        assertTrue(loaded.whitelistedPlayers().contains(player1));
        assertTrue(loaded.whitelistedPlayers().contains(player2));
    }

    // --- Round-trip AVEC flags (mockStatic ArcadiaGuard) ---

    @Test
    void roundTrip_withBooleanFlag_preserved() throws IOException {
        BooleanFlag blockBreak = new BooleanFlag("block-break", true);
        BooleanFlag pvp = new BooleanFlag("pvp", true);

        FlagRegistryImpl registry = new FlagRegistryImpl();
        registry.register(blockBreak);
        registry.register(pvp);

        Map<String, Object> flags = new LinkedHashMap<>();
        flags.put("block-break", false);
        flags.put("pvp", false);

        ProtectedZone zone = new ProtectedZone("flagged", "minecraft:overworld",
            0, 0, 0, 9, 9, 9, new HashSet<>(), null, 0, flags);

        Path file = tempDir.resolve("flagged.json");
        ZoneSerializer.write(zone, file);

        try (MockedStatic<ArcadiaGuard> mock = mockStatic(ArcadiaGuard.class)) {
            mock.when(ArcadiaGuard::flagRegistry).thenReturn(registry);

            ProtectedZone loaded = ZoneSerializer.read(file);

            assertEquals(Boolean.FALSE, loaded.flagValues().get("block-break"));
            assertEquals(Boolean.FALSE, loaded.flagValues().get("pvp"));
        }
    }

    @Test
    void roundTrip_withIntFlag_preserved() throws IOException {
        IntFlag priority = new IntFlag("priority", 0);

        FlagRegistryImpl registry = new FlagRegistryImpl();
        registry.register(priority);

        Map<String, Object> flags = Map.of("priority", 5);
        ProtectedZone zone = new ProtectedZone("prio", "minecraft:overworld",
            0, 0, 0, 9, 9, 9, new HashSet<>(), null, 0, flags);

        Path file = tempDir.resolve("prio.json");
        ZoneSerializer.write(zone, file);

        try (MockedStatic<ArcadiaGuard> mock = mockStatic(ArcadiaGuard.class)) {
            mock.when(ArcadiaGuard::flagRegistry).thenReturn(registry);

            ProtectedZone loaded = ZoneSerializer.read(file);
            assertEquals(5, loaded.flagValues().get("priority"));
        }
    }

    // --- JSON corrompu → IOException ---

    @Test
    void read_corruptJson_throwsIOException() throws IOException {
        Path file = tempDir.resolve("corrupt.json");
        Files.writeString(file, "{not valid json at all!!!", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> ZoneSerializer.read(file));
    }

    @Test
    void read_emptyFile_throwsIOException() throws IOException {
        Path file = tempDir.resolve("empty.json");
        Files.writeString(file, "", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> ZoneSerializer.read(file));
    }

    @Test
    void read_jsonArray_throwsIOException() throws IOException {
        Path file = tempDir.resolve("array.json");
        Files.writeString(file, "[]", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> ZoneSerializer.read(file));
    }

    @Test
    void read_missingNameField_throwsIOException() throws IOException {
        Path file = tempDir.resolve("noname.json");
        // JSON valide mais sans champ "name"
        Files.writeString(file, """
            {"format_version":1,"dimension":"minecraft:overworld",
             "min_x":0,"min_y":0,"min_z":0,"max_x":9,"max_y":9,"max_z":9,
             "priority":0,"enabled":true,"inherit_dim_flags":true,
             "flags":{},"whitelist":[],"member_roles":{}}
            """, StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> ZoneSerializer.read(file));
    }

    // --- Swap min/max défensif ---

    @Test
    void read_swappedMinMax_normalizedSilently() throws IOException {
        Path file = tempDir.resolve("swapped.json");
        // min_x > max_x intentionnellement (fichier corrompu ou édité manuellement)
        Files.writeString(file, """
            {"format_version":1,"name":"swapped","dimension":"minecraft:overworld",
             "min_x":50,"min_y":50,"min_z":50,"max_x":-50,"max_y":-50,"max_z":-50,
             "priority":0,"enabled":true,"inherit_dim_flags":true,
             "flags":{},"whitelist":[],"member_roles":{}}
            """, StandardCharsets.UTF_8);

        ProtectedZone loaded = ZoneSerializer.read(file);

        // Après normalisation : min < max
        assertTrue(loaded.minX() <= loaded.maxX());
        assertTrue(loaded.minY() <= loaded.maxY());
        assertTrue(loaded.minZ() <= loaded.maxZ());
        assertEquals(-50, loaded.minX());
        assertEquals(50, loaded.maxX());
    }

    @Test
    void read_globalZone_intMinValueNotSwapped() throws IOException {
        // Les zones globales ont Integer.MIN_VALUE pour les bornes — ne doivent pas être swappées
        Path file = tempDir.resolve("global.json");
        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;
        Files.writeString(file, String.format("""
            {"format_version":1,"name":"global","dimension":"minecraft:overworld",
             "min_x":%d,"min_y":%d,"min_z":%d,"max_x":%d,"max_y":%d,"max_z":%d,
             "priority":0,"enabled":true,"inherit_dim_flags":true,
             "flags":{},"whitelist":[],"member_roles":{}}
            """, min, min, min, max, max, max), StandardCharsets.UTF_8);

        ProtectedZone loaded = ZoneSerializer.read(file);
        assertEquals(Integer.MIN_VALUE, loaded.minX());
        assertEquals(Integer.MAX_VALUE, loaded.maxX());
    }
}
