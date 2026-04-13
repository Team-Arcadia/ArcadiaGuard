package com.arcadia.arcadiaguard.guard;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.logging.ArcadiaGuardAuditLogger;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import com.arcadia.arcadiaguard.zone.ZoneCheckResult;
import com.arcadia.arcadiaguard.zone.ZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.ProfilerFiller;

public final class GuardService {

    public record GuardResult(boolean blocked, String zoneName) {}

    private final ZoneManager zoneManager;
    private final ArcadiaGuardAuditLogger auditLogger;

    public GuardService(ZoneManager zoneManager, ArcadiaGuardAuditLogger auditLogger) {
        this.zoneManager = zoneManager;
        this.auditLogger = auditLogger;
    }

    public boolean shouldBypass(ServerPlayer player) {
        try {
            return player.hasPermissions(ArcadiaGuardConfig.BYPASS_OP_LEVEL.get());
        } catch (Exception e) {
            ArcadiaGuard.LOGGER.warn("ArcadiaGuard: failed to check permissions for player {}, defaulting to no bypass", playerName(player), e);
            return false;
        }
    }

    public GuardResult blockIfProtected(ServerPlayer player, BlockPos pos, String actionName, String featureKey, String message) {
        ProfilerFiller profiler = player.getServer().getProfiler();
        profiler.push("arcadiaguard");
        try {
            if (shouldBypass(player)) return new GuardResult(false, "");
            if (this.zoneManager.isExceptionAllowed(player, pos, featureKey)) return new GuardResult(false, "");

            ZoneCheckResult result = this.zoneManager.check(player, pos);
            if (!result.blocked()) return new GuardResult(false, "");

            ReflectionHelper.invoke(
                player,
                "sendSystemMessage",
                new Class<?>[] { Component.class },
                Component.literal("\u00A7c" + message)
            );
            this.auditLogger.logBlockedAction(playerName(player), actionName, result.zoneName(), pos);
            return new GuardResult(true, result.zoneName());
        } finally {
            profiler.pop();
        }
    }

    public ZoneManager zoneManager() {
        return this.zoneManager;
    }

    private String playerName(ServerPlayer player) {
        Object profile = ReflectionHelper.invoke(player, "getGameProfile", new Class<?>[0]).orElse(null);
        if (profile == null) return "unknown";
        return String.valueOf(ReflectionHelper.invoke(profile, "getName", new Class<?>[0]).orElse("unknown"));
    }
}
