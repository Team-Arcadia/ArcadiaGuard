package com.arcadia.arcadiaguard.logging;

import net.minecraft.core.BlockPos;
import java.time.LocalDateTime;

/**
 * Represents a single blocked-action audit log entry.
 *
 * @param timestamp when the action was blocked
 * @param playerName the name of the acting player
 * @param zoneName   the zone that blocked the action
 * @param action     the action identifier (e.g. {@code "block-break"})
 * @param pos        the block position
 */
public record LogEntry(
    LocalDateTime timestamp,
    String playerName,
    String zoneName,
    String action,
    BlockPos pos
) {
    /** Formats the entry as a single log line. */
    public String format() {
        return String.format("[%s] BLOCKED | player=%s | zone=%s | action=%s | pos=%d,%d,%d",
            timestamp, playerName, zoneName, action, pos.getX(), pos.getY(), pos.getZ());
    }
}
