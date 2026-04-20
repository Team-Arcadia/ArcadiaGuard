package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.api.event.FlagChangedEvent;
import com.arcadia.arcadiaguard.config.ArcadiaGuardConfig;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.util.ReflectionHelper;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class IronsSpellbooksHandler extends AbstractSpellHandler {

    private static final String EVENT_CLASS = "io.redspace.ironsspellbooks.api.events.SpellPreCastEvent";

    /**
     * M4/H-P7: Cache (zoneName + "|" + spellId) → isBlocked verdict.
     * Invalidated on FlagChangedEvent via {@link #onFlagChanged(FlagChangedEvent)}.
     */
    static final java.util.Map<String, Boolean> BLOCKED_SPELL_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * H-P7: Register the FlagChangedEvent listener so the spell cache is invalidated
     * whenever a flag is modified. Called from HandlerRegistry during init.
     */
    public static void registerEventListeners() {
        NeoForge.EVENT_BUS.register(IronsSpellbooksHandler.class);
    }

    /** H-P7: Clears the blocked-spell cache when any zone flag changes. */
    @SubscribeEvent
    public static void onFlagChanged(FlagChangedEvent event) {
        BLOCKED_SPELL_CACHE.clear();
    }

    private static final Set<String> MOVEMENT_SPELLS = Set.of(
        "ironsspellbooks:teleport_spell", "ironsspellbooks:blink_spell",
        "ironsspellbooks:blood_step_spell", "ironsspellbooks:wind_gust_spell"
    );

    public IronsSpellbooksHandler(GuardService guardService) {
        super(guardService,
            ArcadiaGuardConfig.ENABLE_IRONSSPELLBOOKS::get,
            BuiltinFlags.IRONS_SPELL_CAST,
            BuiltinFlags.SPELL_MOVEMENT,
            BuiltinFlags.IRONS_SPELL_WHITELIST,
            BuiltinFlags.IRONS_SPELL_BLACKLIST,
            MOVEMENT_SPELLS,
            "arcadiaguard.message.irons_spell",
            ArcadiaGuardConfig.MESSAGE_IRONSSPELLBOOKS::get);
    }

    @Override public String eventClassName() { return EVENT_CLASS; }

    @Override
    protected ServerPlayer extractPlayer(Event event) {
        Object entity = ReflectionHelper.invoke(event, "getEntity", new Class<?>[0]).orElse(null);
        return entity instanceof ServerPlayer p ? p : null;
    }

    @Override
    protected String extractSpellId(Event event, ServerPlayer player) {
        Object obj = ReflectionHelper.invoke(event, "getSpellId", new Class<?>[0]).orElse("unknown");
        return String.valueOf(obj).toLowerCase(java.util.Locale.ROOT);
    }
}
