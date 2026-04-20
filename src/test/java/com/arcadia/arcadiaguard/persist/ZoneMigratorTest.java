package com.arcadia.arcadiaguard.persist;

import com.arcadia.arcadiaguard.zone.ProtectedZone;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ZoneMigratorTest {

    // ── v0 → v2 (full chain) ────────────────────────────────────────────────────

    @Test
    void migrate_v0_appliesBothSteps() {
        JsonObject json = jsonNoVersion();

        boolean migrated = ZoneMigrator.migrate(json);

        assertTrue(migrated);
        assertEquals(ZoneMigrator.CURRENT_VERSION, json.get("format_version").getAsInt());
        // v0→v1: defaults backfilled
        assertTrue(json.get("enabled").getAsBoolean());
        assertEquals(0, json.get("priority").getAsInt());
        assertTrue(json.has("whitelist"));
        assertTrue(json.has("flags"));
        // v1→v2: new fields backfilled (defaults to true so zones héritent les dim flags par défaut)
        assertTrue(json.get("inherit_dim_flags").getAsBoolean());
        assertTrue(json.has("member_roles"));
    }

    // ── v1 → v2 only ────────────────────────────────────────────────────────────

    @Test
    void migrate_v1_appliesOnlyV1toV2Step() {
        JsonObject json = jsonV1();

        boolean migrated = ZoneMigrator.migrate(json);

        assertTrue(migrated);
        assertEquals(2, json.get("format_version").getAsInt());
        // v1→v2 should have added missing fields
        assertTrue(json.has("inherit_dim_flags"));
        assertTrue(json.has("member_roles"));
    }

    @Test
    void migrate_v1_doesNotOverwriteExistingInheritDimFlags() {
        JsonObject json = jsonV1();
        json.addProperty("inherit_dim_flags", false);

        ZoneMigrator.migrate(json);

        // Must not overwrite existing value
        assertFalse(json.get("inherit_dim_flags").getAsBoolean());
    }

    // ── Already current ──────────────────────────────────────────────────────────

    @Test
    void migrate_currentVersion_returnsFalse() {
        JsonObject json = jsonV1();
        json.addProperty("format_version", ZoneMigrator.CURRENT_VERSION);

        boolean migrated = ZoneMigrator.migrate(json);

        assertFalse(migrated);
    }

    // ── Roundtrip via ZoneSerializer (uses TempDir) ──────────────────────────────

    @Test
    void migrate_v1Json_roundtripViaRead_stableContent() throws Exception {
        // Build a minimal v1 JSON (missing inherit_dim_flags + member_roles)
        String v1Json = """
            {"format_version":1,"name":"test","dimension":"minecraft:overworld",
             "min_x":0,"min_y":0,"min_z":0,"max_x":9,"max_y":9,"max_z":9,
             "priority":0,"enabled":true,
             "flags":{},"whitelist":[]}
            """;
        java.nio.file.Path tmpFile = java.nio.file.Files.createTempFile("zone_v1_", ".json");
        try {
            java.nio.file.Files.writeString(tmpFile, v1Json, java.nio.charset.StandardCharsets.UTF_8);
            ProtectedZone zone = ZoneSerializer.read(tmpFile);
            // After migration, file should be re-written at v2
            String content = java.nio.file.Files.readString(tmpFile, java.nio.charset.StandardCharsets.UTF_8);
            JsonObject after = JsonParser.parseString(content).getAsJsonObject();
            assertEquals(ZoneMigrator.CURRENT_VERSION, after.get("format_version").getAsInt());
            // Zone reads correctly
            assertEquals("test", zone.name());
            assertTrue(zone.inheritDimFlags());
        } finally {
            java.nio.file.Files.deleteIfExists(tmpFile);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static JsonObject jsonNoVersion() {
        JsonObject j = new JsonObject();
        j.addProperty("name", "zone");
        j.addProperty("dimension", "minecraft:overworld");
        return j;
    }

    private static JsonObject jsonV1() {
        JsonObject j = new JsonObject();
        j.addProperty("format_version", 1);
        j.addProperty("name", "zone");
        j.addProperty("dimension", "minecraft:overworld");
        return j;
    }
}
