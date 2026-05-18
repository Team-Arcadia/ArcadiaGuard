package com.arcadia.arcadiaguard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Couvre FR28 : messages in-game bilingues (FR + EN via fichiers lang NeoForge).
 * Vérifie que les deux fichiers lang existent, sont parsables, ont le même nombre
 * de clés, et que chaque clé présente dans l'un est présente dans l'autre.
 */
class I18nKeysTest {

    private static final Path EN = Paths.get(
        "src/main/resources/assets/arcadiaguard/lang/en_us.json");
    private static final Path FR = Paths.get(
        "src/main/resources/assets/arcadiaguard/lang/fr_fr.json");

    /** Extrait les clés JSON (premier niveau uniquement) via regex simple. */
    private Set<String> extractKeys(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        Set<String> keys = new HashSet<>();
        Pattern keyPattern = Pattern.compile("\"([^\"]+)\"\\s*:");
        Matcher matcher = keyPattern.matcher(content);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    @Test
    void en_us_fileExists() {
        assertTrue(Files.exists(EN), "en_us.json doit exister");
    }

    @Test
    void fr_fr_fileExists() {
        assertTrue(Files.exists(FR), "fr_fr.json doit exister");
    }

    @Test
    void en_us_isNotEmpty() throws IOException {
        Set<String> keys = extractKeys(EN);
        assertFalse(keys.isEmpty(), "en_us.json ne doit pas être vide");
    }

    @Test
    void fr_fr_isNotEmpty() throws IOException {
        Set<String> keys = extractKeys(FR);
        assertFalse(keys.isEmpty(), "fr_fr.json ne doit pas être vide");
    }

    @Test
    void bothFiles_haveSameKeyCount() throws IOException {
        Set<String> en = extractKeys(EN);
        Set<String> fr = extractKeys(FR);
        assertEquals(en.size(), fr.size(),
            "EN et FR doivent avoir le même nombre de clés de traduction");
    }

    @Test
    void allEnKeys_presentInFr() throws IOException {
        Set<String> en = extractKeys(EN);
        Set<String> fr = extractKeys(FR);
        Set<String> missing = new HashSet<>(en);
        missing.removeAll(fr);
        if (!missing.isEmpty()) {
            fail("Clés présentes en EN mais absentes en FR : " + missing);
        }
    }

    @Test
    void allFrKeys_presentInEn() throws IOException {
        Set<String> en = extractKeys(EN);
        Set<String> fr = extractKeys(FR);
        Set<String> extra = new HashSet<>(fr);
        extra.removeAll(en);
        if (!extra.isEmpty()) {
            fail("Clés présentes en FR mais absentes en EN : " + extra);
        }
    }

    @Test
    void allKeys_startWithModNamespace() throws IOException {
        Set<String> en = extractKeys(EN);
        for (String key : en) {
            boolean valid = key.startsWith("arcadiaguard.")
                || key.startsWith("item.arcadiaguard.")
                || key.startsWith("itemGroup.arcadiaguard")
                || key.startsWith("block.arcadiaguard.");
            assertTrue(valid,
                "Toutes les clés doivent appartenir au namespace arcadiaguard — clé invalide : " + key);
        }
    }

    @Test
    void en_us_validUtf8() {
        try {
            String content = Files.readString(EN, StandardCharsets.UTF_8);
            assertFalse(content.isBlank());
        } catch (IOException e) {
            fail("Impossible de lire en_us.json en UTF-8 : " + e.getMessage());
        }
    }

    @Test
    void fr_fr_validUtf8() {
        try {
            String content = Files.readString(FR, StandardCharsets.UTF_8);
            assertFalse(content.isBlank());
        } catch (IOException e) {
            fail("Impossible de lire fr_fr.json en UTF-8 : " + e.getMessage());
        }
    }
}
