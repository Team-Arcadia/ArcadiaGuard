package com.arcadia.arcadiaguard.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.zone.IZone;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Couvre FR29 : événements NeoForge émis sur création/modification/suppression de zone.
 * Teste la construction et les accesseurs des trois classes d'événements.
 */
class ZoneEventsTest {

    private IZone zone;

    @BeforeEach
    void setUp() {
        zone = new ProtectedZone("test-zone", "minecraft:overworld", 0, 0, 0, 10, 10, 10, new java.util.HashSet<>());
    }

    // ── ZoneCreatedEvent ──────────────────────────────────────────────────────

    @Test
    void zoneCreatedEvent_zone_returnsProvidedZone() {
        ZoneCreatedEvent event = new ZoneCreatedEvent(zone);
        assertSame(zone, event.zone());
    }

    @Test
    void zoneCreatedEvent_zone_name_matchesOriginal() {
        ZoneCreatedEvent event = new ZoneCreatedEvent(zone);
        assertEquals("test-zone", event.zone().name());
    }

    @Test
    void zoneCreatedEvent_differentInstances_independant() {
        IZone other = new ProtectedZone("other", "minecraft:the_nether", 0, 0, 0, 5, 5, 5, new java.util.HashSet<>());
        ZoneCreatedEvent e1 = new ZoneCreatedEvent(zone);
        ZoneCreatedEvent e2 = new ZoneCreatedEvent(other);
        assertEquals("test-zone", e1.zone().name());
        assertEquals("other", e2.zone().name());
    }

    // ── ZoneRemovedEvent ──────────────────────────────────────────────────────

    @Test
    void zoneRemovedEvent_zone_returnsProvidedZone() {
        ZoneRemovedEvent event = new ZoneRemovedEvent(zone);
        assertSame(zone, event.zone());
    }

    @Test
    void zoneRemovedEvent_zone_dimension_correct() {
        ZoneRemovedEvent event = new ZoneRemovedEvent(zone);
        assertEquals("minecraft:overworld", event.zone().dimension());
    }

    @Test
    void zoneRemovedEvent_differentZone_independant() {
        IZone nether = new ProtectedZone("deep", "minecraft:the_nether", -10, 0, -10, 10, 120, 10, new java.util.HashSet<>());
        ZoneRemovedEvent e = new ZoneRemovedEvent(nether);
        assertEquals("deep", e.zone().name());
        assertEquals("minecraft:the_nether", e.zone().dimension());
    }

    // ── FlagChangedEvent ──────────────────────────────────────────────────────

    @Test
    void flagChangedEvent_zone_returnsProvidedZone() {
        BooleanFlag flag = new BooleanFlag("pvp", true);
        FlagChangedEvent event = new FlagChangedEvent(zone, flag, null, false);
        assertSame(zone, event.zone());
    }

    @Test
    void flagChangedEvent_flag_returnsProvidedFlag() {
        BooleanFlag flag = new BooleanFlag("pvp", true);
        FlagChangedEvent event = new FlagChangedEvent(zone, flag, null, false);
        assertSame(flag, event.flag());
        assertEquals("pvp", event.flag().id());
    }

    @Test
    void flagChangedEvent_oldValue_null_whenFlagWasNotSet() {
        BooleanFlag flag = new BooleanFlag("pvp", true);
        FlagChangedEvent event = new FlagChangedEvent(zone, flag, null, false);
        assertNull(event.oldValue());
    }

    @Test
    void flagChangedEvent_newValue_null_whenFlagWasReset() {
        BooleanFlag flag = new BooleanFlag("pvp", true);
        FlagChangedEvent event = new FlagChangedEvent(zone, flag, false, null);
        assertNull(event.newValue());
        assertEquals(false, event.oldValue());
    }

    @Test
    void flagChangedEvent_bothValues_present() {
        BooleanFlag flag = new BooleanFlag("mob-spawn", true);
        FlagChangedEvent event = new FlagChangedEvent(zone, flag, true, false);
        assertEquals(true, event.oldValue());
        assertEquals(false, event.newValue());
    }

    @Test
    void flagChangedEvent_intFlagValue_storedCorrectly() {
        var intFlag = new com.arcadia.arcadiaguard.api.flag.IntFlag("heal-amount", 0);
        FlagChangedEvent event = new FlagChangedEvent(zone, intFlag, 0, 4);
        assertEquals(0, event.oldValue());
        assertEquals(4, event.newValue());
    }

    @Test
    void flagChangedEvent_flagId_matchesExpected() {
        BooleanFlag flag = new BooleanFlag("block-break", false);
        FlagChangedEvent event = new FlagChangedEvent(zone, flag, null, true);
        assertEquals("block-break", event.flag().id());
    }
}
