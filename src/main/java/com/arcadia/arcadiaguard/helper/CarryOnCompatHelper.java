package com.arcadia.arcadiaguard.helper;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

public final class CarryOnCompatHelper {

    private static final DenyDecision PASS = new DenyDecision(false, BlockPos.ZERO, "(none)");

    private CarryOnCompatHelper() {}

    public static boolean shouldBlockEntityPickup(ServerPlayer player, Entity target) {
        if (player == null || target == null) return false;
        Level targetLevel = target.level();
        if (targetLevel == null || targetLevel.isClientSide()) return false;

        GuardService guard = guardService();
        if (guard == null || guard.shouldBypass(player)) return false;

        DenyDecision decision = deniedAt(guard, player, targetLevel, target.blockPosition());
        if (!decision.denied()) {
            Level playerLevel = player.level();
            if (playerLevel != null && !playerLevel.isClientSide()) {
                decision = deniedAt(guard, player, playerLevel, player.blockPosition());
            }
        }
        return deny(player, guard, decision, "carryon_entity");
    }

    public static boolean shouldBlockAt(ServerPlayer player, Level level, BlockPos pos, String action) {
        if (player == null || level == null || level.isClientSide() || pos == null) return false;
        GuardService guard = guardService();
        if (guard == null || guard.shouldBypass(player)) return false;
        return deny(player, guard, deniedAt(guard, player, level, pos), action);
    }

    private static boolean deny(ServerPlayer player, GuardService guard, DenyDecision decision, String action) {
        if (!decision.denied()) return false;
        player.sendSystemMessage(Component.translatable("arcadiaguard.message.carryon").withStyle(ChatFormatting.RED));
        guard.auditDenied(player, decision.zoneName(), decision.pos(), BuiltinFlags.CARRYON, action);
        return true;
    }

    private static DenyDecision deniedAt(GuardService guard, ServerPlayer player, Level level, BlockPos pos) {
        if (pos == null) return PASS;
        ProtectedZone zone = guard.zoneManager().findZoneContaining(level, pos)
            .map(z -> (ProtectedZone) z)
            .orElse(null);
        if (zone != null && guard.isZoneMember(player, zone)) return PASS;
        if (!guard.isZoneDenying(level, pos, BuiltinFlags.CARRYON)) return PASS;
        return new DenyDecision(true, pos, zone != null ? zone.name() : "(dimension)");
    }

    private static GuardService guardService() {
        try {
            return ArcadiaGuard.guardService();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private record DenyDecision(boolean denied, BlockPos pos, String zoneName) {}
}
