package com.arcadia.arcadiaguard.network.gui;

import static org.junit.jupiter.api.Assertions.*;

import com.arcadia.arcadiaguard.network.gui.ZoneDetailPayload.FlagEntry;
import com.arcadia.arcadiaguard.network.gui.ZoneDetailPayload.MemberEntry;
import io.netty.buffer.Unpooled;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests codec round-trip pour les records internes de {@link ZoneDetailPayload}.
 * Le STREAM_CODEC top-level utilise RegistryFriendlyByteBuf et requiert un
 * RegistryAccess (setup MC server) donc non testable en JUnit pur, mais les
 * sous-codecs FlagEntry.CODEC et MemberEntry.CODEC utilisent juste ByteBuf.
 */
class ZoneDetailPayloadTest {

    @Test
    void flagEntryCodec_boolRoundTrip() {
        var original = new FlagEntry("pvp", "Pvp", false, false,
            "arcadiaguard.flag.pvp.description", FlagEntry.TYPE_BOOL, "false",
            FlagEntry.SOURCE_ZONE_OWN);
        var buf = Unpooled.buffer();
        FlagEntry.CODEC.encode(buf, original);
        var decoded = FlagEntry.CODEC.decode(buf);
        assertEquals(original, decoded);
    }

    @Test
    void flagEntryCodec_intRoundTrip() {
        var original = new FlagEntry("heal-amount", "Heal Amount", false, false,
            "desc", FlagEntry.TYPE_INT, "10", FlagEntry.SOURCE_DIM);
        var buf = Unpooled.buffer();
        FlagEntry.CODEC.encode(buf, original);
        var decoded = FlagEntry.CODEC.decode(buf);
        assertEquals(original, decoded);
        assertEquals("10", decoded.stringValue());
    }

    @Test
    void flagEntryCodec_listRoundTrip() {
        var original = new FlagEntry("ars-blist", "Ars Blacklist", false, true,
            "d", FlagEntry.TYPE_LIST, "glyph_a,glyph_b,glyph_c",
            FlagEntry.SOURCE_PARENT);
        var buf = Unpooled.buffer();
        FlagEntry.CODEC.encode(buf, original);
        var decoded = FlagEntry.CODEC.decode(buf);
        assertEquals(original, decoded);
    }

    @Test
    void flagEntryCodec_sourceBytesPreserved() {
        for (byte src : new byte[]{FlagEntry.SOURCE_NONE, FlagEntry.SOURCE_ZONE_OWN,
                                    FlagEntry.SOURCE_PARENT, FlagEntry.SOURCE_DIM}) {
            var f = new FlagEntry("x", "X", false, false, "", FlagEntry.TYPE_BOOL, "false", src);
            var buf = Unpooled.buffer();
            FlagEntry.CODEC.encode(buf, f);
            var decoded = FlagEntry.CODEC.decode(buf);
            assertEquals(src, decoded.source(), "source byte " + src + " doit roundtripper");
        }
    }

    @Test
    void flagEntryCodec_emptyStringsOk() {
        var original = new FlagEntry("x", "", false, false, "", FlagEntry.TYPE_BOOL, "",
            FlagEntry.SOURCE_NONE);
        var buf = Unpooled.buffer();
        FlagEntry.CODEC.encode(buf, original);
        assertEquals(original, FlagEntry.CODEC.decode(buf));
    }

    @Test
    void flagEntryCodec_longStringValue() {
        // Verifie que la cap 32768 de VALUE_C est respectee pour les listes longues.
        String longVal = "a".repeat(1000);
        var original = new FlagEntry("x", "X", false, false, "", FlagEntry.TYPE_LIST, longVal,
            FlagEntry.SOURCE_ZONE_OWN);
        var buf = Unpooled.buffer();
        FlagEntry.CODEC.encode(buf, original);
        var decoded = FlagEntry.CODEC.decode(buf);
        assertEquals(longVal, decoded.stringValue());
    }

    @Test
    void memberEntryCodec_roundTrip() {
        var original = new MemberEntry("b00bfd32-3aab-450e-83a1-9cd36f2035ac", "Netorse");
        var buf = Unpooled.buffer();
        MemberEntry.CODEC.encode(buf, original);
        var decoded = MemberEntry.CODEC.decode(buf);
        assertEquals(original, decoded);
        assertEquals("Netorse", decoded.name());
    }

    @Test
    void memberEntryCodec_emptyOk() {
        var original = new MemberEntry("", "");
        var buf = Unpooled.buffer();
        MemberEntry.CODEC.encode(buf, original);
        assertEquals(original, MemberEntry.CODEC.decode(buf));
    }

    @Test
    void flagEntryTypeConstants_stable() {
        // Verifie que les constants n'ont pas change (ordre important pour la serialisation).
        assertEquals(0, FlagEntry.TYPE_BOOL);
        assertEquals(1, FlagEntry.TYPE_INT);
        assertEquals(2, FlagEntry.TYPE_LIST);
    }

    @Test
    void flagEntrySourceConstants_stable() {
        assertEquals(0, FlagEntry.SOURCE_NONE);
        assertEquals(1, FlagEntry.SOURCE_ZONE_OWN);
        assertEquals(2, FlagEntry.SOURCE_PARENT);
        assertEquals(3, FlagEntry.SOURCE_DIM);
    }

    // --- viewOnly dans ZoneDetailPayload ---

    @Test
    void viewOnly_true_preservedInRecord() {
        var detail = new ZoneDetailPayload.Detail("z", "dim", 0, 0, 0, 1, 1, 1,
            null, List.of(), List.of(), true, false, List.of());
        var payload = new ZoneDetailPayload(detail, true);
        assertTrue(payload.viewOnly());
    }

    @Test
    void viewOnly_false_preservedInRecord() {
        var detail = new ZoneDetailPayload.Detail("z", "dim", 0, 0, 0, 1, 1, 1,
            null, List.of(), List.of(), true, false, List.of());
        var payload = new ZoneDetailPayload(detail, false);
        assertFalse(payload.viewOnly());
    }

    @Test
    void viewOnly_roundTripViaDetailCodecAndBoolean() {
        // Le STREAM_CODEC top-level encod : Detail.CODEC + writeBoolean(viewOnly).
        // On simule le même schéma sur un ByteBuf simple pour vérifier le round-trip.
        var detail = new ZoneDetailPayload.Detail("myzone", "minecraft:overworld",
            -10, 60, -10, 10, 80, 10, "parentZone",
            List.of(new FlagEntry("pvp", "PvP", true, false, "desc",
                FlagEntry.TYPE_BOOL, "false", FlagEntry.SOURCE_ZONE_OWN)),
            List.of(), true, true, List.of("minecraft:diamond_sword"));
        var buf = Unpooled.buffer();
        ZoneDetailPayload.Detail.CODEC.encode(buf, detail);
        buf.writeBoolean(true); // viewOnly = true

        var decoded = ZoneDetailPayload.Detail.CODEC.decode(buf);
        boolean viewOnly = buf.readBoolean();

        assertEquals(detail, decoded);
        assertTrue(viewOnly);
        assertEquals(0, buf.readableBytes(), "buffer entièrement consommé");
    }

    @Test
    void flagEntryCodec_multipleSequentialWrites() {
        // Encode 3 entries dans le meme buf + decode sequentiellement.
        var buf = Unpooled.buffer();
        var a = new FlagEntry("a", "A", true, false, "", FlagEntry.TYPE_BOOL, "true",
            FlagEntry.SOURCE_ZONE_OWN);
        var b = new FlagEntry("b", "B", false, false, "", FlagEntry.TYPE_INT, "5",
            FlagEntry.SOURCE_DIM);
        var c = new FlagEntry("c", "C", false, true, "d", FlagEntry.TYPE_LIST, "x,y",
            FlagEntry.SOURCE_PARENT);
        FlagEntry.CODEC.encode(buf, a);
        FlagEntry.CODEC.encode(buf, b);
        FlagEntry.CODEC.encode(buf, c);
        assertEquals(a, FlagEntry.CODEC.decode(buf));
        assertEquals(b, FlagEntry.CODEC.decode(buf));
        assertEquals(c, FlagEntry.CODEC.decode(buf));
        assertTrue(buf.readableBytes() == 0, "buffer entierement consomme");
    }
}
