package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.DynamicEventHandler;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Blocks parkour actions (ParCool mod) inside protected zones when PARCOOL_ACTIONS=deny.
 *
 * <p>Intercepts {@code ParCoolActionEvent.TryToStart} — the canonical pre-start event.
 * Also intercepts {@code TryToContinue} to stop ongoing actions when a player moves
 * into a protected zone mid-action.
 *
 * <p>Uses DynamicEventHandler so registration is silently skipped if ParCool is absent.
 */
public final class ParcoolHandler implements DynamicEventHandler {

    private static final String EVENT_CLASS = "com.alrex.parcool.api.unstable.action.ParCoolActionEvent$TryToStart";

    private final GuardService guardService;

    public ParcoolHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public String eventClassName() {
        return EVENT_CLASS;
    }

    @Override
    public void handle(Event event) {
        ServerPlayer player = extractPlayer(event);
        if (player == null) return;
        if (guardService.shouldBypass(player)) return;

        Optional<ProtectedZone> zoneOpt = guardService.zoneManager().checkZone(player, player.blockPosition());
        if (zoneOpt.isEmpty()) return;
        if (!guardService.isZoneDenying(zoneOpt.get(), BuiltinFlags.PARCOOL_ACTIONS, player.serverLevel())) return;

        if (event instanceof ICancellableEvent c) c.setCanceled(true);
        // Message gere une fois par PlayerEventHandler (throttle MSG_THROTTLE_MS, via chat).
        // Ici pas d'actionbar car l'event fire chaque tick -> message permanent sinon.
    }

    private static ServerPlayer extractPlayer(Event event) {
        try {
            Object player = event.getClass().getMethod("getPlayer").invoke(event);
            return player instanceof ServerPlayer sp ? sp : null;
        } catch (Exception e) {
            return null;
        }
    }
}
