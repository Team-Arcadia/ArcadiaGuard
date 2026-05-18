package com.arcadia.arcadiaguard.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link DynamicItemBlockList} CRUD + persistence (GSON JSON round-trip).
 * Utilise @TempDir pour isolation filesystem.
 */
class DynamicItemBlockListTest {

    private static final ResourceLocation TNT = ResourceLocation.parse("minecraft:tnt");
    private static final ResourceLocation STICK = ResourceLocation.parse("minecraft:stick");
    private static final ResourceLocation CUSTOM = ResourceLocation.parse("arcadia:wand");

    @Test
    void load_missingFile_noThrow(@TempDir Path tmp) {
        var list = new DynamicItemBlockList(tmp.resolve("nope.json"));
        list.load();
        assertTrue(list.list().isEmpty());
    }

    @Test
    void add_newItem_returnsTrueAndPersists(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("bl.json");
        var list = new DynamicItemBlockList(file);
        assertTrue(list.add(TNT));
        assertTrue(Files.exists(file));
        assertTrue(list.list().contains(TNT));
    }

    @Test
    void add_duplicate_returnsFalse(@TempDir Path tmp) {
        var list = new DynamicItemBlockList(tmp.resolve("bl.json"));
        assertTrue(list.add(TNT));
        assertFalse(list.add(TNT));
    }

    @Test
    void remove_existing_returnsTrue(@TempDir Path tmp) {
        var list = new DynamicItemBlockList(tmp.resolve("bl.json"));
        list.add(TNT);
        assertTrue(list.remove(TNT));
        assertFalse(list.list().contains(TNT));
    }

    @Test
    void remove_absent_returnsFalse(@TempDir Path tmp) {
        var list = new DynamicItemBlockList(tmp.resolve("bl.json"));
        assertFalse(list.remove(TNT));
    }

    @Test
    void persistence_roundTrip(@TempDir Path tmp) {
        Path file = tmp.resolve("bl.json");
        var list1 = new DynamicItemBlockList(file);
        list1.add(TNT);
        list1.add(STICK);
        list1.add(CUSTOM);

        var list2 = new DynamicItemBlockList(file);
        list2.load();
        assertEquals(3, list2.list().size());
        assertTrue(list2.list().contains(TNT));
        assertTrue(list2.list().contains(STICK));
        assertTrue(list2.list().contains(CUSTOM));
    }

    @Test
    void load_clearsExisting(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("bl.json");
        // Pre-ecrit le fichier avec TNT+STICK.
        Files.writeString(file, "{\"blocked_items\":[\"minecraft:tnt\",\"minecraft:stick\"]}");
        var list = new DynamicItemBlockList(file);
        list.load();
        assertEquals(2, list.list().size());
        // Reecrit le fichier avec un seul item.
        Files.writeString(file, "{\"blocked_items\":[\"minecraft:apple\"]}");
        list.load();
        // load() a du clear() et relire -> n'a plus que apple, pas TNT ni STICK.
        assertEquals(1, list.list().size());
        assertFalse(list.list().contains(TNT));
        assertFalse(list.list().contains(STICK));
    }

    @Test
    void list_isSorted(@TempDir Path tmp) {
        // Meme namespace pour garantir l'ordre alphabetique par path (ResourceLocation
        // compareTo compare d'abord namespace puis path).
        var list = new DynamicItemBlockList(tmp.resolve("bl.json"));
        list.add(ResourceLocation.parse("minecraft:zzz"));
        list.add(ResourceLocation.parse("minecraft:aaa"));
        list.add(ResourceLocation.parse("minecraft:mmm"));
        var result = list.list();
        assertEquals("minecraft:aaa", result.get(0).toString());
        assertEquals("minecraft:mmm", result.get(1).toString());
        assertEquals("minecraft:zzz", result.get(2).toString());
    }

    @Test
    void list_isUnmodifiable(@TempDir Path tmp) {
        var list = new DynamicItemBlockList(tmp.resolve("bl.json"));
        list.add(TNT);
        assertThrows(UnsupportedOperationException.class, () -> list.list().add(STICK));
    }

    @Test
    void load_ignoresInvalidIds(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("bl.json");
        // ResourceLocation.tryParse rejette les espaces et caracteres non [a-z0-9_/.-].
        Files.writeString(file,
            "{\"blocked_items\":[\"minecraft:tnt\",\"CAPS:BAD\",\"with spaces\",\"minecraft:stick\"]}");
        var list = new DynamicItemBlockList(file);
        list.load();
        // Doit charger tnt + stick mais ignorer silencieusement les ids invalides.
        assertTrue(list.list().contains(TNT));
        assertTrue(list.list().contains(STICK));
        assertEquals(2, list.list().size(),
            "Ids avec espaces et majuscules rejetes par ResourceLocation.tryParse");
    }
}
