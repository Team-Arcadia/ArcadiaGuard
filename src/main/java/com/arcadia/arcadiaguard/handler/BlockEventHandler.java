package com.arcadia.arcadiaguard.handler;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Handles block break and block place, checking {@link BuiltinFlags#BLOCK_BREAK} and
 * {@link BuiltinFlags#BLOCK_PLACE} on the zone. Both are denied by default (flag = false).
 */
public final class BlockEventHandler {

    private final GuardService guard;

    public BlockEventHandler(GuardService guard) {
        this.guard = guard;
    }

    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!ArcadiaGuardConfig.ENABLE_BLOCK_BREAK.get()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        // L4 TODO: add early-out via internal.hasZoneInChunk(event.getPos()) once InternalZoneProvider exposes it
        if (guard.blockIfFlagDenied(player, event.getPos(), BuiltinFlags.BLOCK_BREAK,
                "block_break", "arcadiaguard.message.block_break").blocked()) {
            event.setCanceled(true);
        }
    }

    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!ArcadiaGuardConfig.ENABLE_BLOCK_PLACE.get()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (guard.blockIfFlagDenied(player, event.getPos(), BuiltinFlags.BLOCK_PLACE,
                "block_place", "arcadiaguard.message.block_place").blocked()) {
            event.setCanceled(true);
        }
    }
}
