package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.flag.ListFlag;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.handler.HandlerRegistry.DynamicEventHandler;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Template method base for mod spell-cast handlers.
 *
 * <p>Subclasses provide the event class name, spell-ID extraction, and flag
 * configuration. The common flow — bypass check, zone check, movement guard,
 * cast flag, whitelist/blacklist — is centralised here.
 *
 * <p>Pass {@code null} for {@code movementFlag} or empty set for {@code movementSpells}
 * to skip the movement check. Pass {@code null} for {@code whitelistFlag} /
 * {@code blacklistFlag} to skip those checks.
 */
public abstract class AbstractSpellHandler implements DynamicEventHandler {

    protected final GuardService guardService;
    private final Supplier<Boolean> enabled;
    private final BooleanFlag castFlag;
    private final BooleanFlag movementFlag;
    private final ListFlag whitelistFlag;
    private final ListFlag blacklistFlag;
    private final Set<String> movementSpells;
    private final String movementMessageKey;
    private final Supplier<String> castMessage;

    protected AbstractSpellHandler(
            GuardService guardService,
            Supplier<Boolean> enabled,
            BooleanFlag castFlag,
            BooleanFlag movementFlag,
            ListFlag whitelistFlag,
            ListFlag blacklistFlag,
            Set<String> movementSpells,
            String movementMessageKey,
            Supplier<String> castMessage) {
        this.guardService = guardService;
        this.enabled = enabled;
        this.castFlag = castFlag;
        this.movementFlag = movementFlag;
        this.whitelistFlag = whitelistFlag;
        this.blacklistFlag = blacklistFlag;
        this.movementSpells = movementSpells;
        this.movementMessageKey = movementMessageKey;
        this.castMessage = castMessage;
    }

    /** Extracts the player from a mod event (via reflection). Returns null if unavailable. */
    protected abstract ServerPlayer extractPlayer(Event event);

    /** Extracts and normalises the spell/ability ID from a mod event. */
    protected abstract String extractSpellId(Event event, ServerPlayer player);

    @Override
    public final void handle(Event event) {
        if (!enabled.get()) return;
        ServerPlayer player = extractPlayer(event);
        if (player == null) return;
        String spellId = extractSpellId(event, player);
        handleSpell(event, player, spellId);
    }

    private void handleSpell(Event event, ServerPlayer player, String spellId) {
        if (guardService.shouldBypass(player)) return;
        Optional<ProtectedZone> zoneOpt = guardService.zoneManager().checkZone(player, player.blockPosition());
        if (zoneOpt.isEmpty()) return;
        ProtectedZone zone = zoneOpt.get();

        if (movementFlag != null && !movementSpells.isEmpty() && movementSpells.contains(spellId)) {
            if (!guardService.isFlagAllowedOrUnset(zone, movementFlag, player.serverLevel())) {
                player.sendSystemMessage(Component.translatable(movementMessageKey).withStyle(ChatFormatting.RED));
                cancel(event);
                return;
            }
        }

        boolean castAllowed = guardService.isFlagAllowedOrUnset(zone, castFlag, player.serverLevel());
        if (!castAllowed) {
            if (whitelistFlag != null) {
                @SuppressWarnings("unchecked")
                List<String> whitelist = (List<String>) zone.flagValues().getOrDefault(whitelistFlag.id(), List.of());
                if (whitelist.contains(spellId)) return;
            }
            player.sendSystemMessage(Component.literal("\u00a7c" + castMessage.get()));
            cancel(event);
            return;
        }

        if (blacklistFlag != null) {
            @SuppressWarnings("unchecked")
            List<String> blacklist = (List<String>) zone.flagValues().getOrDefault(blacklistFlag.id(), List.of());
            if (blacklist.contains(spellId)) {
                player.sendSystemMessage(Component.literal("\u00a7c" + castMessage.get()));
                cancel(event);
            }
        }
    }

    private static void cancel(Event event) {
        if (event instanceof ICancellableEvent c) c.setCanceled(true);
    }
}
