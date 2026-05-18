package com.arcadia.arcadiaguard.persist;

import static org.junit.jupiter.api.Assertions.*;

import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests round-trip write→read pour ZoneSerializer, avec focus sur le bug 1.5.5
 * (regression S-H20) où le snapshot scheduleWrite perdait blockedItems.
 */
class ZoneSerializerRoundTripTest {

    @Test
    void blockedItems_persistThroughWriteRead(@TempDir Path tempDir) throws IOException {
        var zone = new ProtectedZone("test", "minecraft:overworld",
            0, 0, 0, 10, 10, 10, new HashSet<>(), null, 0, null, null, null);
        zone.blockItem(ResourceLocation.parse("minecraft:tnt"));
        zone.blockItem(ResourceLocation.parse("ars_nouveau:wand"));

        Path file = tempDir.resolve("test.json");
        ZoneSerializer.write(zone, file);

        var loaded = ZoneSerializer.read(file);
        assertEquals(2, loaded.blockedItems().size());
        assertTrue(loaded.isItemBlocked(ResourceLocation.parse("minecraft:tnt")));
        assertTrue(loaded.isItemBlocked(ResourceLocation.parse("ars_nouveau:wand")));
    }

    @Test
    void scheduleWriteSnapshotPattern_preservesBlockedItemsOnDisk(@TempDir Path tempDir) throws IOException {
        // Reproduit exactement le pattern d'InternalZoneProvider.scheduleWrite (1.5.5).
        // Avant le fix, le snapshot avait blockedItems vide → fichier disque sans items.
        // Note : on évite setFlag pour ne pas requérir ArcadiaGuard.flagRegistry() en test.
        var original = new ProtectedZone("z", "minecraft:overworld",
            0, 0, 0, 10, 10, 10, new HashSet<>(), null, 0, null, null, null);
        original.blockItem(ResourceLocation.parse("minecraft:tnt"));

        var snapshot = new ProtectedZone(
            original.name(), original.dimension(),
            original.minX(), original.minY(), original.minZ(),
            original.maxX(), original.maxY(), original.maxZ(),
            new HashSet<>(original.whitelistedPlayers()),
            original.parent(), original.priority(),
            new java.util.LinkedHashMap<>(original.flagValues()),
            new java.util.LinkedHashMap<>(original.memberRoles()),
            new HashSet<>(original.blockedItems()));
        snapshot.setEnabled(original.enabled());
        snapshot.setInheritDimFlags(original.inheritDimFlags());

        Path file = tempDir.resolve("z.json");
        ZoneSerializer.write(snapshot, file);

        var loaded = ZoneSerializer.read(file);
        assertTrue(loaded.isItemBlocked(ResourceLocation.parse("minecraft:tnt")),
            "regression 1.5.5 : le snapshot scheduleWrite doit persister blockedItems");
    }

    @Test
    void emptyBlockedItems_writesEmptyArray(@TempDir Path tempDir) throws IOException {
        var zone = new ProtectedZone("empty", "minecraft:overworld",
            0, 0, 0, 10, 10, 10, new HashSet<>(), null, 0, null, null, null);
        Path file = tempDir.resolve("empty.json");
        ZoneSerializer.write(zone, file);

        var loaded = ZoneSerializer.read(file);
        assertTrue(loaded.blockedItems().isEmpty());
    }

    @Test
    void blockedItems_modedNamespaces_roundTrip(@TempDir Path tempDir) throws IOException {
        var zone = new ProtectedZone("z", "minecraft:overworld",
            0, 0, 0, 10, 10, 10, new HashSet<>(), null, 0, null, null, null);
        zone.blockItem(ResourceLocation.parse("ars_nouveau:manipulation_essence_item_frame_interaction"));
        zone.blockItem(ResourceLocation.parse("hominid:juggernaut_spawn_egg"));

        Path file = tempDir.resolve("z.json");
        ZoneSerializer.write(zone, file);
        var loaded = ZoneSerializer.read(file);

        assertEquals(2, loaded.blockedItems().size());
        assertTrue(loaded.isItemBlocked(ResourceLocation.parse("ars_nouveau:manipulation_essence_item_frame_interaction")));
        assertTrue(loaded.isItemBlocked(ResourceLocation.parse("hominid:juggernaut_spawn_egg")));
    }
}
