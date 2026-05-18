package com.arcadia.arcadiaguard.handler;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.helper.FlagMixinHelper;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

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
        var result = guard.blockIfFlagDenied(player, event.getPos(), BuiltinFlags.BLOCK_BREAK,
                "block_break", "arcadiaguard.message.block_break");
        if (result.blocked()) {
            event.setCanceled(true);
        } else if (!event.isCanceled()) {
            allowContainerDropsIfBreakDenied(player, event.getPos());
        }
    }

    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!ArcadiaGuardConfig.ENABLE_BLOCK_BREAK.get()) return;
        if (event.isCanceled()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (guard.shouldBypass(player) || isMemberAt(player, event.getPos())) {
            allowContainerDropsIfBreakDenied(player, event.getPos());
        }
    }

    public void onBlockDrops(BlockDropsEvent event) {
        if (!ArcadiaGuardConfig.ENABLE_BLOCK_BREAK.get()) return;

        Entity breaker = event.getBreaker();
        if (breaker instanceof ServerPlayer player) {
            if (guard.shouldBypass(player)) return;
            if (isMemberAt(player, event.getPos())) return;
            if (!guard.isZoneDenying(event.getLevel(), event.getPos(), BuiltinFlags.BLOCK_BREAK)) return;

            event.getDrops().clear();
            event.setDroppedExperience(0);
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("arcadiaguard.message.block_break")
                .withStyle(ChatFormatting.RED), true);
            String zoneName = zoneNameAt(player.serverLevel(), event.getPos());
            guard.auditDenied(player, zoneName, event.getPos(), BuiltinFlags.BLOCK_BREAK, "block_break_drops");
            return;
        }

        if (guard.isZoneDenying(event.getLevel(), event.getPos(), BuiltinFlags.BLOCK_BREAK)) {
            event.getDrops().clear();
            event.setDroppedExperience(0);
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

    private boolean isMemberAt(ServerPlayer player, BlockPos pos) {
        Optional<ProtectedZone> zoneOpt = guard.zoneManager().findZoneContaining(player.serverLevel(), pos)
            .map(zone -> (ProtectedZone) zone);
        return zoneOpt.isPresent() && guard.isZoneMember(player, zoneOpt.get());
    }

    private String zoneNameAt(Level level, BlockPos pos) {
        return guard.zoneManager().findZoneContaining(level, pos)
            .map(zone -> ((ProtectedZone) zone).name())
            .orElse("(dimension)");
    }

    private void allowContainerDropsIfBreakDenied(ServerPlayer player, BlockPos pos) {
        if (guard.isZoneDenying(player.serverLevel(), pos, BuiltinFlags.BLOCK_BREAK)) {
            FlagMixinHelper.allowContainerDropOnce(player.serverLevel(), pos);
        }
    }
}
