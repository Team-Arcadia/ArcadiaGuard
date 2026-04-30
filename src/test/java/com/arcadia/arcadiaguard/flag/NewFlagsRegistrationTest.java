package com.arcadia.arcadiaguard.flag;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Vérifie l'enregistrement et la cohérence des nouveaux flags 1.6.0 :
 * mob-attack-player et mob-spawn-allowlist.
 */
class NewFlagsRegistrationTest {

    @Test
    void mobAttackPlayer_isRegistered() {
        var registry = new FlagRegistryImpl();
        registry.registerBuiltins();
        assertTrue(registry.get("mob-attack-player").isPresent(),
            "mob-attack-player doit être enregistré");
    }

    @Test
    void mobSpawnAllowlist_isRegistered() {
        var registry = new FlagRegistryImpl();
        registry.registerBuiltins();
        assertTrue(registry.get("mob-spawn-allowlist").isPresent(),
            "mob-spawn-allowlist doit être enregistré");
    }

    @Test
    void mobAttackPlayer_defaultIsAllow() {
        // BooleanFlag.defaultValue=false (= ON green = protection active)
        // mais on déclare false comme défaut, ce qui en sémantique GUI = OFF (rouge) = inactif.
        // Sémantique inversée : "default value=false" = comportement par défaut = ne bloque pas.
        // → Vérifie cohérence avec les autres flags de combat.
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
        // Sanity check : les nouvelles clés sont bien dans les fichiers lang.
        var en = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/resources/assets/arcadiaguard/lang/en_us.json"));
        var fr = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/resources/assets/arcadiaguard/lang/fr_fr.json"));
        for (String flagId : new String[]{"mob-attack-player", "mob-spawn-allowlist"}) {
            String key = "arcadiaguard.flag." + flagId + ".description";
            assertTrue(en.contains(key), "EN doit contenir " + key);
            assertTrue(fr.contains(key), "FR doit contenir " + key);
        }
    }
}
