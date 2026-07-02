package com.arcadia.arcadiaguard.flag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Verifie l'enregistrement et la coherence des nouveaux flags. */
class NewFlagsRegistrationTest {

    @Test
    void mobAttackPlayer_isRegistered() {
        var registry = new FlagRegistryImpl();
        registry.registerBuiltins();
        assertTrue(registry.get("mob-attack-player").isPresent(),
            "mob-attack-player doit etre enregistre");
    }

    @Test
    void mobSpawnAllowlist_isRegistered() {
        var registry = new FlagRegistryImpl();
        registry.registerBuiltins();
        assertTrue(registry.get("mob-spawn-allowlist").isPresent(),
            "mob-spawn-allowlist doit etre enregistre");
    }

    @Test
    void jadeOverlay_isRegistered() {
        var registry = new FlagRegistryImpl();
        registry.registerBuiltins();
        assertTrue(registry.get("jade-overlay").isPresent(),
            "jade-overlay doit etre enregistre");
    }

    @Test
    void mobAttackPlayer_defaultIsAllow() {
        assertEquals(false, BuiltinFlags.MOB_ATTACK_PLAYER.defaultValue());
    }

    @Test
    void mobSpawnAllowlist_isListType() {
        assertTrue(BuiltinFlags.MOB_SPAWN_ALLOWLIST instanceof com.arcadia.arcadiaguard.api.flag.ListFlag);
    }

    @Test
    void mobSpawnAllowlist_distinctFromMobSpawnList() {
        assertNotEquals(BuiltinFlags.MOB_SPAWN_ALLOWLIST.id(),
                        BuiltinFlags.MOB_SPAWN_LIST.id());
    }

    @Test
    void allFlagDescriptionKeys_havei18nEntries() throws Exception {
        var en = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/resources/assets/arcadiaguard/lang/en_us.json"));
        var fr = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/resources/assets/arcadiaguard/lang/fr_fr.json"));
        for (String flagId : new String[]{"mob-attack-player", "mob-spawn-allowlist", "jade-overlay"}) {
            String key = "arcadiaguard.flag." + flagId + ".description";
            assertTrue(en.contains(key), "EN doit contenir " + key);
            assertTrue(fr.contains(key), "FR doit contenir " + key);
        }
    }
}
