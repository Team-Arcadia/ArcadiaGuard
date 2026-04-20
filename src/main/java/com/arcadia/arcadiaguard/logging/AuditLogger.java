package com.arcadia.arcadiaguard.logging;

import net.minecraft.core.BlockPos;

/**
 * Async audit logger for actions blocked by zone protection.
 * Implementations must be non-blocking on the calling (main) thread.
 */
public interface AuditLogger {

    /**
     * Queues a blocked-action entry for async writing. Must return immediately (O(1)).
     *
     * @param playerName the name of the acting player
     * @param action     the action that was blocked (e.g. {@code "block-break"})
     * @param zoneName   the zone that blocked the action
     * @param pos        the position where the action occurred
     */
    void logBlockedAction(String playerName, String action, String zoneName, BlockPos pos);
}
