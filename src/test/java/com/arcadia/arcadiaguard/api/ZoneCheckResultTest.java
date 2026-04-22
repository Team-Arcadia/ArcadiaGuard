package com.arcadia.arcadiaguard.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.arcadia.arcadiaguard.api.zone.ZoneCheckResult;
import org.junit.jupiter.api.Test;

/** Tests {@link ZoneCheckResult} factories + record accessors. */
class ZoneCheckResultTest {

    @Test
    void allowed_isNotBlocked() {
        var r = ZoneCheckResult.allowed();
        assertFalse(r.blocked());
        assertEquals("", r.zoneName());
        assertEquals("", r.providerName());
    }

    @Test
    void blocked_carriesZoneName() {
        var r = ZoneCheckResult.blocked("testzone");
        assertTrue(r.blocked());
        assertEquals("testzone", r.zoneName());
        assertEquals("", r.providerName());
    }

    @Test
    void blockedWithProvider_carriesAll() {
        var r = ZoneCheckResult.blocked("testzone", "yawp");
        assertTrue(r.blocked());
        assertEquals("testzone", r.zoneName());
        assertEquals("yawp", r.providerName());
    }

    @Test
    void compactCtor_defaultsProviderToEmpty() {
        var r = new ZoneCheckResult(true, "testzone");
        assertEquals("", r.providerName());
    }

    @Test
    void recordEquality_byValue() {
        assertEquals(ZoneCheckResult.allowed(), ZoneCheckResult.allowed());
        assertEquals(ZoneCheckResult.blocked("z"), ZoneCheckResult.blocked("z"));
    }
}
