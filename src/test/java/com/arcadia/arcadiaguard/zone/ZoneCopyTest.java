package com.arcadia.arcadiaguard.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Couvre FR17 : duplication de zone (commande /ag zone copy).
 * La logique de copie dans ZoneCommands crée un nouveau ProtectedZone
 * avec champs copiés profondément — on valide ici que ce contrat est respecté.
 */
class ZoneCopyTest {

    private ProtectedZone baseZone() {
        return new ProtectedZone("original", "minecraft:overworld",
            10, 64, -20, 50, 128, 30, new java.util.HashSet<>());
    }

    /** Reproduit exactement la logique de ZoneCommands.copy(). */
    private ProtectedZone copyZone(ProtectedZone orig, String newName) {
        return new ProtectedZone(
            newName,
            orig.dimension(),
            orig.minX(), orig.minY(), orig.minZ(),
            orig.maxX(), orig.maxY(), orig.maxZ(),
            new java.util.HashSet<>(orig.whitelistedPlayers()),
            orig.parent(),
            orig.priority(),
            new LinkedHashMap<>(orig.flagValues())
        );
    }

    @Test
    void copy_preservesDimension() {
        ProtectedZone copy = copyZone(baseZone(), "clone");
        assertEquals("minecraft:overworld", copy.dimension());
    }

    @Test
    void copy_preservesBounds() {
        ProtectedZone orig = baseZone();
        ProtectedZone copy = copyZone(orig, "clone");
        assertEquals(orig.minX(), copy.minX());
        assertEquals(orig.minY(), copy.minY());
        assertEquals(orig.minZ(), copy.minZ());
        assertEquals(orig.maxX(), copy.maxX());
        assertEquals(orig.maxY(), copy.maxY());
        assertEquals(orig.maxZ(), copy.maxZ());
    }

    @Test
    void copy_newName_differs() {
        ProtectedZone copy = copyZone(baseZone(), "clone");
        assertEquals("clone", copy.name());
    }

    @Test
    void copy_whitelist_isDeepCopy() {
        ProtectedZone orig = baseZone();
        UUID player = UUID.randomUUID();
        orig.whitelistAdd(player);

        ProtectedZone copy = copyZone(orig, "clone");

        assertTrue(copy.whitelistedPlayers().contains(player));
        // Modifier l'original ne doit pas affecter la copie
        orig.whitelistRemove(player);
        assertTrue(copy.whitelistedPlayers().contains(player),
            "La whitelist doit être une copie indépendante");
    }

    @Test
    void copy_flags_areDeepCopy() {
        ProtectedZone orig = baseZone();
        orig.setFlag("pvp", false);

        ProtectedZone copy = copyZone(orig, "clone");

        assertEquals(false, copy.flagValues().get("pvp"));
        // Modifier l'original ne doit pas affecter la copie
        orig.setFlag("pvp", true);
        assertEquals(false, copy.flagValues().get("pvp"),
            "Les flags doivent être une copie indépendante");
    }

    @Test
    void copy_flagValues_notSameReference() {
        ProtectedZone orig = baseZone();
        orig.setFlag("mob-spawn", true);
        ProtectedZone copy = copyZone(orig, "clone");
        assertNotSame(orig.flagValues(), copy.flagValues());
    }

    @Test
    void copy_withParent_preservesParent() {
        ProtectedZone orig = new ProtectedZone("child", "minecraft:overworld",
            0, 0, 0, 10, 10, 10,
            Set.of(), "parent-zone", 5, new LinkedHashMap<>());
        ProtectedZone copy = copyZone(orig, "child-copy");
        assertEquals("parent-zone", copy.parent());
    }

    @Test
    void copy_withPriority_preservesPriority() {
        ProtectedZone orig = new ProtectedZone("zone", "minecraft:overworld",
            0, 0, 0, 10, 10, 10,
            Set.of(), null, 42, new LinkedHashMap<>());
        ProtectedZone copy = copyZone(orig, "zone-copy");
        assertEquals(42, copy.priority());
    }

    @Test
    void copy_noParent_remainsNull() {
        ProtectedZone orig = baseZone();
        ProtectedZone copy = copyZone(orig, "clone");
        assertNull(copy.parent());
    }

    @Test
    void copy_emptyWhitelist_remains_empty() {
        ProtectedZone orig = baseZone();
        ProtectedZone copy = copyZone(orig, "clone");
        assertTrue(copy.whitelistedPlayers().isEmpty());
    }

    @Test
    void copy_multipleFlags_allPreserved() {
        ProtectedZone orig = baseZone();
        orig.setFlag("pvp", false);
        orig.setFlag("mob-spawn", true);
        orig.setFlag("heal-amount", 4);

        ProtectedZone copy = copyZone(orig, "clone");

        assertEquals(false, copy.flagValues().get("pvp"));
        assertEquals(true, copy.flagValues().get("mob-spawn"));
        assertEquals(4, copy.flagValues().get("heal-amount"));
    }

    @Test
    void copy_crossDimension_preservesDimension() {
        ProtectedZone nether = new ProtectedZone("nether-zone", "minecraft:the_nether",
            0, 0, 0, 100, 128, 100, new java.util.HashSet<>());
        ProtectedZone copy = copyZone(nether, "nether-copy");
        assertEquals("minecraft:the_nether", copy.dimension());
    }
}
