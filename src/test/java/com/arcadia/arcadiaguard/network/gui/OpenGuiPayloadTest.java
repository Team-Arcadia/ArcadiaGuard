package com.arcadia.arcadiaguard.network.gui;

import static org.junit.jupiter.api.Assertions.*;

import com.arcadia.arcadiaguard.network.gui.OpenGuiPayload.ZoneEntry;
import io.netty.buffer.Unpooled;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenGuiPayloadTest {

    // --- ZoneEntry.CODEC round-trips ---

    @Test
    void zoneEntryCodec_roundTrip() {
        var original = new ZoneEntry("spawn", "minecraft:overworld",
            -10, 60, -10, 10, 80, 10,
            3, false, 5, true);
        var buf = Unpooled.buffer();
        ZoneEntry.CODEC.encode(buf, original);
        var decoded = ZoneEntry.CODEC.decode(buf);
        assertEquals(original, decoded);
    }

    @Test
    void zoneEntryCodec_withParent() {
        var original = new ZoneEntry("child", "minecraft:the_nether",
            0, 0, 0, 5, 5, 5,
            0, true, 2, false);
        var buf = Unpooled.buffer();
        ZoneEntry.CODEC.encode(buf, original);
        var decoded = ZoneEntry.CODEC.decode(buf);
        assertTrue(decoded.hasParent());
        assertFalse(decoded.enabled());
    }

    @Test
    void zoneEntryCodec_multipleSequential() {
        var buf = Unpooled.buffer();
        var a = new ZoneEntry("a", "dim:a", 0, 0, 0, 1, 1, 1, 0, false, 0, true);
        var b = new ZoneEntry("b", "dim:b", -5, 50, -5, 5, 60, 5, 2, true, 3, false);
        ZoneEntry.CODEC.encode(buf, a);
        ZoneEntry.CODEC.encode(buf, b);
        assertEquals(a, ZoneEntry.CODEC.decode(buf));
        assertEquals(b, ZoneEntry.CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void noPosSentinel_preservedInRecord() {
        assertEquals(Long.MIN_VALUE, OpenGuiPayload.NO_POS);
    }

    // --- viewOnly dans OpenGuiPayload ---

    @Test
    void viewOnly_true_preservedInRecord() {
        var payload = new OpenGuiPayload(List.of(),
            OpenGuiPayload.NO_POS, OpenGuiPayload.NO_POS,
            false, 1, 50, 1, true);
        assertTrue(payload.viewOnly());
    }

    @Test
    void viewOnly_false_preservedInRecord() {
        var payload = new OpenGuiPayload(List.of(),
            OpenGuiPayload.NO_POS, OpenGuiPayload.NO_POS,
            false, 1, 50, 1, false);
        assertFalse(payload.viewOnly());
    }

    @Test
    void viewOnly_roundTripViaZoneEntryCodecAndBoolean() {
        // Simule le schéma du STREAM_CODEC top-level pour la partie viewOnly
        var e = new ZoneEntry("spawn", "minecraft:overworld",
            0, 64, 0, 10, 80, 10, 5, false, 3, true);
        var buf = Unpooled.buffer();
        ZoneEntry.CODEC.encode(buf, e);
        buf.writeBoolean(true); // viewOnly

        var decoded = ZoneEntry.CODEC.decode(buf);
        boolean viewOnly = buf.readBoolean();

        assertEquals(e, decoded);
        assertTrue(viewOnly);
        assertEquals(0, buf.readableBytes());
    }
}
