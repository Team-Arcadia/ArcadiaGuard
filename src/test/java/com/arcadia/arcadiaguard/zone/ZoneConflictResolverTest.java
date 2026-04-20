package com.arcadia.arcadiaguard.zone;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ZoneConflictResolverTest {

    private static ProtectedZone zone(String name, int priority, int minX, int maxX) {
        return new ProtectedZone(name, "minecraft:overworld",
            minX, 0, 0, maxX, 9, 9, new HashSet<>(), null, priority);
    }

    private static ProtectedZone zoneWithPriorityFlag(String name, int flagPriority, int minX, int maxX) {
        return new ProtectedZone(name, "minecraft:overworld",
            minX, 0, 0, maxX, 9, 9, new HashSet<>(), null, 0,
            Map.of("priority", flagPriority));
    }

    // --- Tri par priority ---

    @Test
    void resolve_highestPriorityWins() {
        ProtectedZone low = zone("low", 1, 0, 9);
        ProtectedZone high = zone("high", 10, 0, 9);
        assertEquals("high", ZoneConflictResolver.resolve(List.of(low, high)).name());
    }

    @Test
    void resolve_priorityFlagOverridesFieldPriority() {
        // Zone avec priority=0 mais flag "priority"=5 → doit gagner sur zone priority=3
        ProtectedZone withFlag = zoneWithPriorityFlag("with-flag", 5, 0, 9);
        ProtectedZone withField = zone("with-field", 3, 0, 9);
        assertEquals("with-flag", ZoneConflictResolver.resolve(List.of(withField, withFlag)).name());
    }

    // --- Tri par volume (même priority) ---

    @Test
    void resolve_samePriority_smallerVolumeWins() {
        ProtectedZone large = zone("large", 0, 0, 99);   // volume 100 * 10 * 10
        ProtectedZone small = zone("small", 0, 0, 9);    // volume 10 * 10 * 10
        assertEquals("small", ZoneConflictResolver.resolve(List.of(large, small)).name());
    }

    // --- Tri alphabétique (même priority + même volume) ---

    @Test
    void resolve_samePriorityAndVolume_alphabeticalWins() {
        ProtectedZone zoneB = zone("beta", 0, 0, 9);
        ProtectedZone zoneA = zone("alpha", 0, 0, 9);
        assertEquals("alpha", ZoneConflictResolver.resolve(List.of(zoneB, zoneA)).name());
    }

    // --- Cas limite : un seul candidat ---

    @Test
    void resolve_singleCandidate_returnsThatCandidate() {
        ProtectedZone only = zone("only", 5, 0, 9);
        assertEquals("only", ZoneConflictResolver.resolve(List.of(only)).name());
    }

    // --- Pas d'overflow sur zones aux coordonnées extrêmes ---

    @Test
    void resolve_extremeCoordinates_noOverflow() {
        // volume = (long)(MAX - MIN + 1)^3 — doit rester positif, pas d'overflow int
        ProtectedZone huge = new ProtectedZone("huge", "minecraft:overworld",
            Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
            new HashSet<>(), null, 0);
        ProtectedZone small = zone("small", 0, 0, 9);

        // "small" a le plus petit volume → doit gagner
        assertEquals("small", ZoneConflictResolver.resolve(List.of(huge, small)).name());
    }

    // --- Tri complet combiné ---

    @Test
    void resolve_fullSortOrder_priorityThenVolumeThenAlpha() {
        ProtectedZone p10large = zone("z-p10-large", 10, 0, 999);
        ProtectedZone p10small = zone("a-p10-small", 10, 0, 9);
        ProtectedZone p5 = zone("p5", 5, 0, 9);

        // p10 > p5 ; parmi p10 → small gagne sur large
        assertEquals("a-p10-small", ZoneConflictResolver.resolve(List.of(p10large, p10small, p5)).name());
    }
}
