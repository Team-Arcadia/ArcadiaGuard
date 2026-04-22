package com.arcadia.arcadiaguard.flag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.arcadia.arcadiaguard.api.flag.IntFlag;
import org.junit.jupiter.api.Test;

/**
 * Tests pour {@link IntFlag} : construction, min/max bounds, defaults.
 */
class IntFlagTest {

    @Test
    void simpleCtor_unboundedMinMax() {
        var f = new IntFlag("x", 5);
        assertEquals(Integer.MIN_VALUE, f.min());
        assertEquals(Integer.MAX_VALUE, f.max());
        assertEquals(5, f.defaultValue());
        assertEquals("x", f.id());
    }

    @Test
    void ctor_withDescription_unboundedMinMax() {
        var f = new IntFlag("x", 5, "desc");
        assertEquals(Integer.MIN_VALUE, f.min());
        assertEquals(Integer.MAX_VALUE, f.max());
        assertEquals("desc", f.description());
    }

    @Test
    void ctor_withMinMax_storesBounds() {
        var f = new IntFlag("heal-amount", 0, 0, 20, "desc");
        assertEquals(0, f.min());
        assertEquals(20, f.max());
        assertEquals(0, f.defaultValue());
    }

    @Test
    void ctor_withRequiredMod_forwardsAll() {
        var f = new IntFlag("x", 3, 1, 10, "d", "othermod");
        assertEquals(1, f.min());
        assertEquals(10, f.max());
        assertEquals("othermod", f.requiredMod());
    }

    @Test
    void clamp_respectsBounds() {
        // Le clamp est fait cote appelant (GuiActionHandler.parseFlagValue).
        // Ici on verifie juste que Math.max/min avec les bornes fonctionne comme attendu.
        var f = new IntFlag("heal", 0, 0, 20, "");
        assertEquals(20, Math.max(f.min(), Math.min(f.max(), 999)));
        assertEquals(0,  Math.max(f.min(), Math.min(f.max(), -50)));
        assertEquals(15, Math.max(f.min(), Math.min(f.max(), 15)));
    }
}
