package com.arcadia.arcadiaguard.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.flag.Flag;
import com.arcadia.arcadiaguard.api.flag.IntFlag;
import com.arcadia.arcadiaguard.api.flag.ListFlag;
import com.arcadia.arcadiaguard.flag.FlagRegistryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Couvre FR03 : API publique pour enregistrement de flags externes par des mods tiers.
 *
 * <p>Teste que {@link ArcadiaGuardAPI#registerFlag(Flag)} délègue correctement
 * à {@link FlagRegistryImpl}, que le flag est récupérable par ID, et que le
 * comportement de collision (premier gagne) est respecté.
 */
class ExternalFlagApiTest {

    private FlagRegistryImpl registry;
    private ArcadiaGuardAPI api;

    @BeforeEach
    void setUp() throws Exception {
        registry = new FlagRegistryImpl();
        api = new ArcadiaGuardAPI();
        // Injection du registry via le champ package-private (ou via setup())
        var field = ArcadiaGuardAPI.class.getDeclaredField("flagRegistry");
        field.setAccessible(true);
        field.set(api, registry);
    }

    @Test
    void registerFlag_booleanFlag_isRetrievableById() {
        BooleanFlag flag = new BooleanFlag("mymod:custom-pvp", true, "Custom PvP toggle.");
        api.registerFlag(flag);
        assertTrue(registry.get("mymod:custom-pvp").isPresent());
    }

    @Test
    void registerFlag_intFlag_isRetrievableById() {
        IntFlag flag = new IntFlag("mymod:max-players", 5);
        api.registerFlag(flag);
        assertTrue(registry.get("mymod:max-players").isPresent());
    }

    @Test
    void registerFlag_listFlag_isRetrievableById() {
        ListFlag flag = new ListFlag("mymod:item-whitelist");
        api.registerFlag(flag);
        assertTrue(registry.get("mymod:item-whitelist").isPresent());
    }

    @Test
    void registerFlag_defaultValue_preserved() {
        BooleanFlag flag = new BooleanFlag("mymod:invincible", false);
        api.registerFlag(flag);
        Flag<?> retrieved = registry.get("mymod:invincible").orElseThrow();
        assertEquals(false, retrieved.defaultValue());
    }

    @Test
    void registerFlag_duplicate_firstWins() {
        BooleanFlag first = new BooleanFlag("mymod:shared", true);
        BooleanFlag second = new BooleanFlag("mymod:shared", false);
        api.registerFlag(first);
        api.registerFlag(second);

        Flag<?> retrieved = registry.get("mymod:shared").orElseThrow();
        assertEquals(true, retrieved.defaultValue(), "Le premier flag enregistré doit gagner");
    }

    @Test
    void flagRegistry_returnsCorrectInstance() {
        assertNotNull(api.flagRegistry());
        assertEquals(registry, api.flagRegistry());
    }

    @Test
    void registerFlag_multipleDistinctFlags_allPresent() {
        api.registerFlag(new BooleanFlag("mod1:flag-a", true));
        api.registerFlag(new BooleanFlag("mod1:flag-b", false));
        api.registerFlag(new IntFlag("mod1:flag-c", 10));

        assertTrue(registry.get("mod1:flag-a").isPresent());
        assertTrue(registry.get("mod1:flag-b").isPresent());
        assertTrue(registry.get("mod1:flag-c").isPresent());
    }

    @Test
    void registerFlag_appearsInAll() {
        BooleanFlag flag = new BooleanFlag("mymod:in-all", true);
        api.registerFlag(flag);

        assertTrue(registry.all().contains(flag));
    }

    @Test
    void registeredFlag_lookupCost_isO1() {
        // Enregistre 500 flags externes et vérifie que le lookup reste immédiat
        for (int i = 0; i < 500; i++) {
            api.registerFlag(new BooleanFlag("mod:flag-" + i, true));
        }
        long start = System.nanoTime();
        for (int i = 0; i < 500; i++) {
            assertTrue(registry.get("mod:flag-" + i).isPresent());
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 100,
            "500 lookups doivent prendre < 100 ms (O(1)) — temps réel : " + elapsedMs + " ms");
    }
}
