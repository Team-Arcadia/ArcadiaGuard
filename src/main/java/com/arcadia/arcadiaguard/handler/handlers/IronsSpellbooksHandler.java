package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.DynamicEventHandler;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

public final class IronsSpellbooksHandler implements DynamicEventHandler {

    private static final String EVENT_CLASS = "io.redspace.ironsspellbooks.api.events.SpellPreCastEvent";
    private final GuardService guardService;

    public IronsSpellbooksHandler(GuardService guardService) {
        this.guardService = guardService;
    }

    @Override
    public String eventClassName() {
        return EVENT_CLASS;
    }

    @Override
    public void handle(Event event) {
        if (!ArcadiaGuardConfig.ENABLE_IRONSSPELLBOOKS.get()) return;
        Object entity = ReflectionHelper.invoke(event, "getEntity", new Class<?>[0]).orElse(null);
        Object spellId = ReflectionHelper.invoke(event, "getSpellId", new Class<?>[0]).orElse("unknown_spell");
        if (entity instanceof ServerPlayer player) {
            Object pos = ReflectionHelper.invoke(player, "blockPosition", new Class<?>[0]).orElse(null);
            if (pos instanceof net.minecraft.core.BlockPos blockPos
                && this.guardService.blockIfProtected(player, blockPos, String.valueOf(spellId), "ironsspellbooks", ArcadiaGuardConfig.MESSAGE_IRONSSPELLBOOKS.get()).blocked()
                && event instanceof ICancellableEvent cancellable) {
                cancellable.setCanceled(true);
            }
        }
    }
}
