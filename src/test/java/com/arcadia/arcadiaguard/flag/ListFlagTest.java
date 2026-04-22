package com.arcadia.arcadiaguard.flag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.arcadia.arcadiaguard.api.flag.ListFlag;
import org.junit.jupiter.api.Test;

/** Tests {@link ListFlag} constructors + default empty list. */
class ListFlagTest {

    @Test
    void simpleCtor_emptyDefault() {
        var f = new ListFlag("blist");
        assertEquals("blist", f.id());
        assertEquals("", f.description());
        assertEquals("", f.requiredMod());
        assertTrue(f.defaultValue().isEmpty());
    }

    @Test
    void withDescription_storesDescription() {
        var f = new ListFlag("x", "desc");
        assertEquals("desc", f.description());
    }

    @Test
    void withRequiredMod_storesMod() {
        var f = new ListFlag("x", "d", "othermod");
        assertEquals("othermod", f.requiredMod());
    }

    @Test
    void defaultValue_isImmutable() {
        var f = new ListFlag("x");
        // La default list doit etre immutable : essayer d'y ajouter doit throw.
        org.junit.jupiter.api.Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> f.defaultValue().add("item"));
    }
}
