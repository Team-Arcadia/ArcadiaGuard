package com.arcadia.arcadiaguard.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** Tests {@link LogEntry} format output. */
class LogEntryTest {

    @Test
    void format_containsAllFields() {
        var entry = new LogEntry(
            LocalDateTime.of(2026, 4, 22, 14, 30, 15),
            "Netorse",
            "testzone",
            "block-break",
            new BlockPos(100, 64, -200));
        String s = entry.format();
        assertTrue(s.contains("Netorse"), "player name should appear");
        assertTrue(s.contains("testzone"), "zone name should appear");
        assertTrue(s.contains("block-break"), "action should appear");
        assertTrue(s.contains("100"), "x coord should appear");
        assertTrue(s.contains("64"), "y coord should appear");
        assertTrue(s.contains("-200"), "z coord should appear");
    }

    @Test
    void format_beginsWithTimestamp() {
        var entry = new LogEntry(
            LocalDateTime.of(2026, 4, 22, 14, 30, 15),
            "Foo", "zone", "pvp", BlockPos.ZERO);
        assertTrue(entry.format().startsWith("[2026-04-22T14:30:15]"),
            "format should start with ISO-ish timestamp in brackets");
    }

    @Test
    void format_includesBlockedKeyword() {
        var entry = new LogEntry(
            LocalDateTime.now(),
            "a", "b", "c", BlockPos.ZERO);
        assertTrue(entry.format().contains("BLOCKED"));
    }

    @Test
    void recordAccessors_returnConstructorArgs() {
        var ts = LocalDateTime.now();
        var pos = new BlockPos(1, 2, 3);
        var entry = new LogEntry(ts, "n", "z", "a", pos);
        assertEquals(ts, entry.timestamp());
        assertEquals("n", entry.playerName());
        assertEquals("z", entry.zoneName());
        assertEquals("a", entry.action());
        assertEquals(pos, entry.pos());
    }
}
