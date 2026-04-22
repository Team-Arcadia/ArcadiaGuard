package com.arcadia.arcadiaguard.zone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests {@link ZoneRole} hierarchy via atLeast(). */
class ZoneRoleTest {

    @Test
    void atLeast_selfReturnsTrue() {
        assertTrue(ZoneRole.MEMBER.atLeast(ZoneRole.MEMBER));
        assertTrue(ZoneRole.MODERATOR.atLeast(ZoneRole.MODERATOR));
        assertTrue(ZoneRole.OWNER.atLeast(ZoneRole.OWNER));
    }

    @Test
    void atLeast_higherRoleSatisfiesLower() {
        assertTrue(ZoneRole.OWNER.atLeast(ZoneRole.MODERATOR));
        assertTrue(ZoneRole.OWNER.atLeast(ZoneRole.MEMBER));
        assertTrue(ZoneRole.MODERATOR.atLeast(ZoneRole.MEMBER));
    }

    @Test
    void atLeast_lowerRoleDoesNotSatisfyHigher() {
        assertFalse(ZoneRole.MEMBER.atLeast(ZoneRole.MODERATOR));
        assertFalse(ZoneRole.MEMBER.atLeast(ZoneRole.OWNER));
        assertFalse(ZoneRole.MODERATOR.atLeast(ZoneRole.OWNER));
    }

    @Test
    void ordinal_matchesHierarchy() {
        // Garantie implicite utilisee par atLeast() : ordinal croissant = role plus fort.
        assertEquals(0, ZoneRole.MEMBER.ordinal());
        assertEquals(1, ZoneRole.MODERATOR.ordinal());
        assertEquals(2, ZoneRole.OWNER.ordinal());
    }
}
