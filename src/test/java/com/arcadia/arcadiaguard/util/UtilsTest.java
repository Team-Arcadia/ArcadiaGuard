package com.arcadia.arcadiaguard.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.arcadia.arcadiaguard.ArcadiaGuardPaths;
import org.junit.jupiter.api.Test;

/**
 * Tests pour les utilitaires string : {@link FlagUtils#formatFlagLabel(String)} et
 * {@link ArcadiaGuardPaths#normalizeZoneName(String)} + {@link ArcadiaGuardPaths#isValidZoneName(String)}.
 */
class UtilsTest {

    // --- FlagUtils.formatFlagLabel ---

    @Test
    void formatFlagLabel_simple() {
        assertEquals("Pvp", FlagUtils.formatFlagLabel("pvp"));
    }

    @Test
    void formatFlagLabel_hyphenated() {
        assertEquals("Block Break", FlagUtils.formatFlagLabel("block-break"));
    }

    @Test
    void formatFlagLabel_multiple() {
        assertEquals("Ars Spell Cast", FlagUtils.formatFlagLabel("ars-spell-cast"));
    }

    // --- ArcadiaGuardPaths.normalizeZoneName ---

    @Test
    void normalizeZoneName_lowercases() {
        assertEquals("foo", ArcadiaGuardPaths.normalizeZoneName("FOO"));
    }

    @Test
    void normalizeZoneName_replacesSpaces() {
        assertEquals("ma_super_zone", ArcadiaGuardPaths.normalizeZoneName("Ma Super Zone"));
    }

    @Test
    void normalizeZoneName_stripsPunctuation() {
        assertEquals("ma_super_zone", ArcadiaGuardPaths.normalizeZoneName("Ma Super Zone !!!"));
    }

    @Test
    void normalizeZoneName_stripsAccents() {
        assertEquals("chateau_fort", ArcadiaGuardPaths.normalizeZoneName("Château Fort"));
        assertEquals("ecolo", ArcadiaGuardPaths.normalizeZoneName("écolo"));
    }

    @Test
    void normalizeZoneName_collapsesUnderscores() {
        assertEquals("a_b", ArcadiaGuardPaths.normalizeZoneName("a___b"));
        assertEquals("a_b", ArcadiaGuardPaths.normalizeZoneName("a   b"));
    }

    @Test
    void normalizeZoneName_trimsLeadingAndTrailing() {
        assertEquals("foo", ArcadiaGuardPaths.normalizeZoneName("  foo  "));
        assertEquals("foo", ArcadiaGuardPaths.normalizeZoneName("___foo___"));
    }

    @Test
    void normalizeZoneName_emptyOrNull() {
        assertEquals("", ArcadiaGuardPaths.normalizeZoneName(""));
        assertEquals("", ArcadiaGuardPaths.normalizeZoneName("   "));
        assertEquals("", ArcadiaGuardPaths.normalizeZoneName(null));
    }

    @Test
    void normalizeZoneName_truncatesAt64() {
        String long65 = "a".repeat(65);
        assertEquals(64, ArcadiaGuardPaths.normalizeZoneName(long65).length());
    }

    @Test
    void normalizeZoneName_keepsDigits() {
        assertEquals("zone_42", ArcadiaGuardPaths.normalizeZoneName("Zone 42"));
    }

    @Test
    void normalizeZoneName_keepsHyphen() {
        assertEquals("zone-a", ArcadiaGuardPaths.normalizeZoneName("zone-a"));
    }

    @Test
    void normalizeZoneName_stripsEmojis() {
        assertEquals("happy_zone", ArcadiaGuardPaths.normalizeZoneName("🏰 Happy Zone 😀"));
    }

    // --- ArcadiaGuardPaths.isValidZoneName ---

    @Test
    void isValidZoneName_acceptsNormalized() {
        assertTrue(ArcadiaGuardPaths.isValidZoneName("foo"));
        assertTrue(ArcadiaGuardPaths.isValidZoneName("my_zone"));
        assertTrue(ArcadiaGuardPaths.isValidZoneName("zone-42"));
    }

    @Test
    void isValidZoneName_rejectsNullAndEmpty() {
        assertFalse(ArcadiaGuardPaths.isValidZoneName(null));
        assertFalse(ArcadiaGuardPaths.isValidZoneName(""));
    }

    @Test
    void isValidZoneName_rejectsSpaces() {
        assertFalse(ArcadiaGuardPaths.isValidZoneName("my zone"));
    }

    @Test
    void isValidZoneName_rejectsPathTraversal() {
        assertFalse(ArcadiaGuardPaths.isValidZoneName("../etc/passwd"));
        assertFalse(ArcadiaGuardPaths.isValidZoneName("foo/bar"));
    }

    @Test
    void normalizationInvariant_normalizedInputIsValid() {
        // Invariant : toute string passee a normalizeZoneName, si non vide apres, doit
        // etre valide. Evite que l'UI cree des noms qui ne peuvent pas etre re-utilises.
        String[] inputs = {"Ma Super Zone", "Château Fort", "zone 42", "🏰 Happy"};
        for (String in : inputs) {
            String norm = ArcadiaGuardPaths.normalizeZoneName(in);
            if (!norm.isEmpty()) {
                assertTrue(ArcadiaGuardPaths.isValidZoneName(norm),
                    "normalize('" + in + "')='" + norm + "' devrait etre valide");
            }
        }
    }
}
