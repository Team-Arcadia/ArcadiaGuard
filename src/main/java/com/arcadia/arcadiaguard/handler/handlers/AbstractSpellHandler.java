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

    /**
     * Extracts ALL individual glyph/effect IDs from the spell (multi-part spells).
     * Default: singleton list from {@link #extractSpellId}. Override for mods with
     * composable spells (Ars Nouveau) to return each spell-part ID separately —
     * needed for blacklist/whitelist/movement checks to match individual glyphs.
     */
    protected List<String> extractSpellGlyphs(Event event, ServerPlayer player) {
        return List.of(extractSpellId(event, player));
    }

    @Override
    public final void handle(Event event) {
        if (!enabled.get()) return;
        ServerPlayer player = extractPlayer(event);
        if (player == null) return;
        String spellId = extractSpellId(event, player);
        List<String> glyphs = extractSpellGlyphs(event, player);
        handleSpell(event, player, spellId, glyphs);
    }

    private void handleSpell(Event event, ServerPlayer player, String spellId, List<String> glyphs) {
        if (guardService.shouldBypass(player)) return;
        // Check the player's position first; if not in a zone, also check the targeted block
        // (prevents casting spells from outside a zone that affect blocks inside it)
        Optional<ProtectedZone> zoneOpt = guardService.zoneManager().checkZone(player, player.blockPosition());
        if (zoneOpt.isEmpty()) {
            net.minecraft.world.phys.HitResult hit = player.pick(32, 0, false);
            if (hit instanceof net.minecraft.world.phys.BlockHitResult bhr) {
                zoneOpt = guardService.zoneManager().checkZone(player, bhr.getBlockPos());
            }
        }
        if (zoneOpt.isEmpty()) return;
        ProtectedZone zone = zoneOpt.get();

        // Movement check: any glyph/spell-id matches the movement set
        if (movementFlag != null && !movementSpells.isEmpty()
                && glyphs.stream().anyMatch(movementSpells::contains)) {
            if (!guardService.isFlagAllowedOrUnset(zone, movementFlag, player.serverLevel())) {
                player.displayClientMessage(Component.translatable(movementMessageKey).withStyle(ChatFormatting.RED), true);
                guardService.auditDenied(player, zone.name(), player.blockPosition(), movementFlag, spellId);
                cancel(event);
                return;
            }
        }

        boolean castAllowed = guardService.isFlagAllowedOrUnset(zone, castFlag, player.serverLevel());
        if (!castAllowed) {
            if (whitelistFlag != null) {
                Object rawWl = zone.flagValues().getOrDefault(whitelistFlag.id(), List.of());
                if (rawWl instanceof List<?> wlList) {
                    List<String> whitelist = wlList.stream().filter(String.class::isInstance).map(String.class::cast).toList();
                    // Whitelist : si ANY glyph est whitelisted, on autorise.
                    if (glyphs.stream().anyMatch(whitelist::contains) || whitelist.contains(spellId)) return;
                }
            }
            player.displayClientMessage(Component.literal("\u00a7c" + castMessage.get()), true);
            guardService.auditDenied(player, zone.name(), player.blockPosition(), castFlag, spellId);
            cancel(event);
            return;
        }

        if (blacklistFlag != null) {
            Object rawBl = zone.flagValues().getOrDefault(blacklistFlag.id(), List.of());
            if (rawBl instanceof List<?> blList) {
                List<String> blacklist = blList.stream().filter(String.class::isInstance).map(String.class::cast).toList();
                // Blacklist : si ANY glyph de la composition est blackliste, on bloque.
                String hit = glyphs.stream().filter(blacklist::contains).findFirst()
                    .orElseGet(() -> blacklist.contains(spellId) ? spellId : null);
                if (hit != null) {
                    // DEBUG : log sur premier match pour aider au diagnostic "tous les sorts bloques"
                    if (!DEBUG_LOGGED) {
                        DEBUG_LOGGED = true;
                        com.arcadia.arcadiaguard.ArcadiaGuard.LOGGER.info(
                            "[ArcadiaGuard] Spell blacklist first hit: spellId='{}', glyphs={}, blacklist={}, matched='{}'",
                            spellId, glyphs, blacklist, hit);
                    }
                    player.displayClientMessage(Component.literal("\u00a7c" + castMessage.get()), true);
                    guardService.auditDenied(player, zone.name(), player.blockPosition(), castFlag, hit + "_blacklisted");
                    cancel(event);
                }
            }
        }
    }

    private static volatile boolean DEBUG_LOGGED = false;

    private static void cancel(Event event) {
        if (event instanceof ICancellableEvent c) c.setCanceled(true);
    }
}
