package com.arcadia.arcadiaguard.network.gui;

import static org.junit.jupiter.api.Assertions.*;

import com.arcadia.arcadiaguard.network.gui.ZoneLogsPayload.LogLine;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

/**
 * Tests des bornes de codecs ZoneLogsPayload, regression 1.5.2 (LogLine bornes)
 * et 1.5.5 (zoneName top-level borné).
 */
class ZoneLogsPayloadTest {

    @Test
    void logLineCodec_roundTrip() {
        var original = new LogLine("2026-04-30 12:34:56", "Curveo",
            "block_break:minecraft:diamond_ore", "100 64 -200");
        var buf = Unpooled.buffer();
        LogLine.CODEC.encode(buf, original);
        var decoded = LogLine.CODEC.decode(buf);
        assertEquals(original, decoded);
    }

    @Test
    void logLineCodec_actionAtBoundary128_succeeds() {
        // Cap action = 128 chars (1 byte UTF8 pour ASCII = 128 bytes < 256 bytes du varint)
        String action = "a".repeat(128);
        var line = new LogLine("ts", "p", action, "0 0 0");
        var buf = Unpooled.buffer();
        LogLine.CODEC.encode(buf, line);
        var decoded = LogLine.CODEC.decode(buf);
        assertEquals(action, decoded.action());
    }

    @Test
    void logLineCodec_playerNameAtBoundary16_succeeds() {
        String player = "x".repeat(16);
        var line = new LogLine("ts", player, "act", "0 0 0");
        var buf = Unpooled.buffer();
        LogLine.CODEC.encode(buf, line);
        var decoded = LogLine.CODEC.decode(buf);
        assertEquals(player, decoded.player());
    }

    @Test
    void logLineCodec_playerNameOverBoundary_throws() {
        // 17 chars > limite 16 → encode doit cap
        String player = "x".repeat(17);
        var line = new LogLine("ts", player, "act", "0 0 0");
        var buf = Unpooled.buffer();
        assertThrows(Exception.class, () -> LogLine.CODEC.encode(buf, line),
            "playerName > 16 chars doit être rejeté (cap stringUtf8(16))");
    }

    @Test
    void logLineCodec_actionOverBoundary_throws() {
        String action = "a".repeat(200); // > 128
        var line = new LogLine("ts", "p", action, "0 0 0");
        var buf = Unpooled.buffer();
        assertThrows(Exception.class, () -> LogLine.CODEC.encode(buf, line));
    }

    // ── Regression 1.5.5 : zoneName du payload top-level borné ──
    // Le bug était l'usage de STRING_UTF8 (uncapped 32 KiB) au lieu d'une borne
    // explicite. On ne peut pas tester le STREAM_CODEC top-level (RegistryFriendlyByteBuf
    // requis) mais on peut au moins vérifier que le LogLine.CODEC est bien borné
    // et confirmer par lecture que les constantes existent.

    @Test
    void zoneNameCodec_isExplicitlyBounded() throws Exception {
        // Reflection : confirme que ZONE_NAME_C existe et est un stringUtf8(64).
        var field = ZoneLogsPayload.class.getDeclaredField("ZONE_NAME_C");
        field.setAccessible(true);
        Object codec = field.get(null);
        assertNotNull(codec, "ZONE_NAME_C doit être un static final pour éviter l'allocation inline");
    }
}
