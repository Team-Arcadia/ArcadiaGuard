package com.arcadia.arcadiaguard.network.gui;

import static org.junit.jupiter.api.Assertions.*;

import com.arcadia.arcadiaguard.network.gui.DimFlagsPayload.FlagInfo;
import io.netty.buffer.Unpooled;
import java.util.List;
import org.junit.jupiter.api.Test;

class DimFlagsPayloadTest {

    // --- FlagInfo.CODEC round-trips ---

    @Test
    void flagInfoCodec_boolRoundTrip() {
        var original = new FlagInfo("pvp", "PvP", false, true,
            "arcadiaguard.flag.pvp.description", FlagInfo.TYPE_BOOL, "false");
        var buf = Unpooled.buffer();
        FlagInfo.CODEC.encode(buf, original);
        assertEquals(original, FlagInfo.CODEC.decode(buf));
    }

    @Test
    void flagInfoCodec_intRoundTrip() {
        var original = new FlagInfo("heal-amount", "Heal Amount", false, true,
            "desc", FlagInfo.TYPE_INT, "42");
        var buf = Unpooled.buffer();
        FlagInfo.CODEC.encode(buf, original);
        var decoded = FlagInfo.CODEC.decode(buf);
        assertEquals(original, decoded);
        assertEquals("42", decoded.stringValue());
    }

    @Test
    void flagInfoCodec_listRoundTrip() {
        var original = new FlagInfo("ars-blist", "Ars Blacklist", false, false,
            "d", FlagInfo.TYPE_LIST, "glyph_a,glyph_b");
        var buf = Unpooled.buffer();
        FlagInfo.CODEC.encode(buf, original);
        assertEquals(original, FlagInfo.CODEC.decode(buf));
    }

    @Test
    void flagInfoCodec_notConfigured_roundTrip() {
        var original = new FlagInfo("pvp", "PvP", false, false, "", FlagInfo.TYPE_BOOL, "false");
        var buf = Unpooled.buffer();
        FlagInfo.CODEC.encode(buf, original);
        var decoded = FlagInfo.CODEC.decode(buf);
        assertFalse(decoded.configured());
    }

    @Test
    void flagInfoTypeConstants_stable() {
        assertEquals(0, FlagInfo.TYPE_BOOL);
        assertEquals(1, FlagInfo.TYPE_INT);
        assertEquals(2, FlagInfo.TYPE_LIST);
    }

    @Test
    void flagInfoCodec_multipleSequential() {
        var buf = Unpooled.buffer();
        var a = new FlagInfo("a", "A", true, true, "", FlagInfo.TYPE_BOOL, "true");
        var b = new FlagInfo("b", "B", false, false, "d", FlagInfo.TYPE_INT, "5");
        FlagInfo.CODEC.encode(buf, a);
        FlagInfo.CODEC.encode(buf, b);
        assertEquals(a, FlagInfo.CODEC.decode(buf));
        assertEquals(b, FlagInfo.CODEC.decode(buf));
        assertEquals(0, buf.readableBytes());
    }

    // --- viewOnly dans DimFlagsPayload ---

    @Test
    void viewOnly_true_preservedInRecord() {
        var payload = new DimFlagsPayload("minecraft:overworld", List.of(), true);
        assertTrue(payload.viewOnly());
    }

    @Test
    void viewOnly_false_preservedInRecord() {
        var payload = new DimFlagsPayload("minecraft:overworld", List.of(), false);
        assertFalse(payload.viewOnly());
    }

    @Test
    void viewOnly_roundTripViaFlagCodecAndBoolean() {
        // Simule le schéma du STREAM_CODEC top-level : flags + writeBoolean(viewOnly)
        var f = new FlagInfo("pvp", "PvP", true, true, "desc", FlagInfo.TYPE_BOOL, "false");
        var buf = Unpooled.buffer();
        buf.writeInt(1); // size
        FlagInfo.CODEC.encode(buf, f);
        buf.writeBoolean(true); // viewOnly

        int size = buf.readInt();
        var decoded = FlagInfo.CODEC.decode(buf);
        boolean viewOnly = buf.readBoolean();

        assertEquals(1, size);
        assertEquals(f, decoded);
        assertTrue(viewOnly);
        assertEquals(0, buf.readableBytes());
    }
}
