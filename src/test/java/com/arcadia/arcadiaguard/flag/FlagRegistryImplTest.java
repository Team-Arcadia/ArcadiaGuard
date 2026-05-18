package com.arcadia.arcadiaguard.flag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.flag.IntFlag;
import org.junit.jupiter.api.Test;

/**
 * Tests pour {@link FlagRegistryImpl} : register, collision, get, all.
 */
class FlagRegistryImplTest {

    @Test
    void register_newFlag_storesIt() {
        var reg = new FlagRegistryImpl();
        var f = new BooleanFlag("test-a", true);
        reg.register(f);
        assertTrue(reg.get("test-a").isPresent());
        assertSame(f, reg.get("test-a").get());
    }

    @Test
    void register_duplicateId_keepsFirst() {
        var reg = new FlagRegistryImpl();
        var first = new BooleanFlag("dup", true);
        var second = new BooleanFlag("dup", false);
        reg.register(first);
        reg.register(second);
        assertSame(first, reg.get("dup").get(),
            "register doit conserver la premiere registration en cas de collision");
    }

    @Test
    void get_unknownId_returnsEmpty() {
        var reg = new FlagRegistryImpl();
        assertFalse(reg.get("unknown").isPresent());
    }

    @Test
    void all_returnsRegisteredFlags() {
        var reg = new FlagRegistryImpl();
        reg.register(new BooleanFlag("a", true));
        reg.register(new BooleanFlag("b", false));
        reg.register(new IntFlag("c", 5));
        assertEquals(3, reg.all().size());
    }

    @Test
    void all_preservesInsertionOrder() {
        var reg = new FlagRegistryImpl();
        var a = new BooleanFlag("a", true);
        var b = new BooleanFlag("b", false);
        var c = new IntFlag("c", 5);
        reg.register(a);
        reg.register(b);
        reg.register(c);
        var it = reg.all().iterator();
        assertSame(a, it.next());
        assertSame(b, it.next());
        assertSame(c, it.next());
    }

    @Test
    void registerBuiltins_populatesAll() {
        // Smoke test : verifie que la methode enregistre au moins quelques flags attendus.
        var reg = new FlagRegistryImpl();
        reg.registerBuiltins();
        assertTrue(reg.get("block-break").isPresent());
        assertTrue(reg.get("pvp").isPresent());
        assertTrue(reg.get("heal-amount").isPresent());
        assertTrue(reg.all().size() >= 80,
            "registerBuiltins doit enregistrer au moins 80 flags (actuellement ~86)");
    }
}
