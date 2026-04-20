package com.arcadia.arcadiaguard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ArcadiaGuardPathsTest {

    // --- Cas valides ---

    @ParameterizedTest
    @ValueSource(strings = {"spawn", "zone-1", "my_zone", "a", "abc123"})
    void isValidZoneName_acceptsValidNames(String name) {
        assertTrue(ArcadiaGuardPaths.isValidZoneName(name));
    }

    @Test
    void isValidZoneName_accepts64Chars() {
        String name = "a".repeat(64);
        assertTrue(ArcadiaGuardPaths.isValidZoneName(name));
    }

    @Test
    void isValidZoneName_acceptsUpperCaseInput_caseInsensitive() {
        // La méthode appelle toLowerCase() avant match → "Spawn" est valide
        assertTrue(ArcadiaGuardPaths.isValidZoneName("Spawn"));
        assertTrue(ArcadiaGuardPaths.isValidZoneName("ZONE_1"));
    }

    // --- Cas invalides ---

    @Test
    void isValidZoneName_rejectsNull() {
        assertFalse(ArcadiaGuardPaths.isValidZoneName(null));
    }

    @Test
    void isValidZoneName_rejectsEmpty() {
        assertFalse(ArcadiaGuardPaths.isValidZoneName(""));
    }

    @Test
    void isValidZoneName_rejects65Chars() {
        String name = "a".repeat(65);
        assertFalse(ArcadiaGuardPaths.isValidZoneName(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../evil", "zone/sub", "zone\\sub", "zone zone", "zone.json"})
    void isValidZoneName_rejectsPathTraversalAndSpecialChars(String name) {
        assertFalse(ArcadiaGuardPaths.isValidZoneName(name));
    }

    @ParameterizedTest
    @ValueSource(strings = {"à", "é", "ü", "ñ", "日本語", "zône"})
    void isValidZoneName_rejectsUnicode(String name) {
        assertFalse(ArcadiaGuardPaths.isValidZoneName(name));
    }
}
