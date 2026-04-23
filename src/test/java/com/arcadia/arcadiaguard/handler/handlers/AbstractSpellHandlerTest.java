package com.arcadia.arcadiaguard.handler.handlers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.arcadia.arcadiaguard.api.flag.BooleanFlag;
import com.arcadia.arcadiaguard.api.flag.ListFlag;
import com.arcadia.arcadiaguard.guard.GuardService;
import com.arcadia.arcadiaguard.zone.ProtectedZone;
import com.arcadia.arcadiaguard.zone.ZoneManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Couvre FR21 : handlers mods optionnels (Ars Nouveau, Iron's Spellbooks…).
 * Couvre FR22 : pattern blacklist/whitelist par mod.
 * Couvre NFR04 : dégradation gracieuse — null player, mod absent, handler désactivé.
 *
 * <p>Utilise {@code TestSpellHandler} (inner class concrète) pour tester les chemins
 * communs de {@link AbstractSpellHandler} sans dépendance réelle à un mod tiers.
 * Bootstrap Minecraft requis pour {@code @Mock ServerPlayer}.
 */
@ExtendWith(MockitoExtension.class)
class AbstractSpellHandlerTest {

    // ── Flags de test ─────────────────────────────────────────────────────────

    private static final BooleanFlag CAST_FLAG     = new BooleanFlag("spells", true);
    private static final BooleanFlag MOVEMENT_FLAG = new BooleanFlag("spell-move", true);
    private static final ListFlag    WHITELIST_FLAG = new ListFlag("spells-whitelist");
    private static final ListFlag    BLACKLIST_FLAG = new ListFlag("spells-blacklist");
    private static final String      SPELL_ID       = "fireball";

    // ── Mocks ─────────────────────────────────────────────────────────────────

    @Mock private GuardService  guardService;
    @Mock private ZoneManager   zoneManager;
    @Mock private ServerPlayer  player;

    @BeforeAll
    static void bootstrap() throws Exception {
        Class<?> lmlClass = Class.forName("net.neoforged.fml.loading.LoadingModList");
        java.lang.reflect.Method ofMethod = lmlClass.getMethod(
            "of", List.class, List.class, List.class, List.class, java.util.Map.class
        );
        ofMethod.invoke(null, List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @BeforeEach
    void setUp() {
        lenient().when(guardService.zoneManager()).thenReturn(zoneManager);
        lenient().when(player.blockPosition()).thenReturn(BlockPos.ZERO);
    }

    // ── NFR04 : handler désactivé ─────────────────────────────────────────────

    @Test
    void handle_whenDisabled_doesNotInteractWithGuardService() {
        var handler = makeHandler(() -> false, CAST_FLAG, null, null, null, Set.of(), player);
        handler.handle(new TestEvent());
        verifyNoInteractions(guardService);
    }

    @Test
    void handle_whenDisabled_doesNotInteractWithPlayer() {
        var handler = makeHandler(() -> false, CAST_FLAG, null, null, null, Set.of(), player);
        handler.handle(new TestEvent());
        verifyNoInteractions(player);
    }

    // ── NFR04 : extractPlayer retourne null ───────────────────────────────────

    @Test
    void handle_whenExtractPlayerReturnsNull_doesNotThrow() {
        // null player = mod non chargé ou event mal typé — doit être silencieux
        var handler = makeHandler(() -> true, CAST_FLAG, null, null, null, Set.of(), null);
        assertDoesNotThrow(() -> handler.handle(new TestEvent()));
    }

    @Test
    void handle_whenExtractPlayerReturnsNull_doesNotCallGuardService() {
        var handler = makeHandler(() -> true, CAST_FLAG, null, null, null, Set.of(), null);
        handler.handle(new TestEvent());
        verifyNoInteractions(guardService);
    }

    // ── Bypass joueur ─────────────────────────────────────────────────────────

    @Test
    void handle_whenPlayerBypasses_doesNotCheckZone() {
        when(guardService.shouldBypass(player)).thenReturn(true);
        var handler = makeHandler(() -> true, CAST_FLAG, null, null, null, Set.of(), player);
        handler.handle(new TestEvent());
        verify(zoneManager, never()).checkZone(any(), any());
    }

    @Test
    void handle_whenPlayerBypasses_noAuditLogged() {
        when(guardService.shouldBypass(player)).thenReturn(true);
        var handler = makeHandler(() -> true, CAST_FLAG, null, null, null, Set.of(), player);
        handler.handle(new TestEvent());
        verify(guardService, never()).auditDenied(any(), any(), any(), any(), any());
    }

    // ── Joueur hors zone ──────────────────────────────────────────────────────

    @Test
    void handle_whenNoZone_doesNotDenySpell() {
        when(guardService.shouldBypass(player)).thenReturn(false);
        when(zoneManager.checkZone(any(), any())).thenReturn(Optional.empty());
        var handler = makeHandler(() -> true, CAST_FLAG, null, null, null, Set.of(), player);
        handler.handle(new TestEvent());
        verify(guardService, never()).auditDenied(any(), any(), any(), any(), any());
    }

    // ── FR21 : sort interdit par cast flag ────────────────────────────────────

    @Test
    void handle_whenCastFlagDenied_auditIsCalled() {
        ProtectedZone zone = zone("z");
        when(guardService.shouldBypass(player)).thenReturn(false);
        when(zoneManager.checkZone(any(), any())).thenReturn(Optional.of(zone));
        when(guardService.isFlagAllowedOrUnset(eq(zone), eq(CAST_FLAG), any())).thenReturn(false);

        var handler = makeHandler(() -> true, CAST_FLAG, null, null, null, Set.of(), player);
        handler.handle(new TestEvent());

        verify(guardService).auditDenied(eq(player), eq("z"), any(), eq(CAST_FLAG), eq(SPELL_ID));
    }

    @Test
    void handle_whenCastFlagDenied_eventIsCancelled() {
        ProtectedZone zone = zone("z");
        when(guardService.shouldBypass(player)).thenReturn(false);
        when(zoneManager.checkZone(any(), any())).thenReturn(Optional.of(zone));
        when(guardService.isFlagAllowedOrUnset(eq(zone), eq(CAST_FLAG), any())).thenReturn(false);

        TestCancellableEvent event = new TestCancellableEvent();
        makeHandler(() -> true, CAST_FLAG, null, null, null, Set.of(), player).handle(event);

        assertTrue(event.isCanceled(), "L'événement doit être annulé quand le cast flag interdit le sort");
    }

    // ── FR22 : whitelist surpasse l'interdiction du cast flag ─────────────────

    @Test
    void handle_whenCastFlagDenied_spellInWhitelist_doesNotDeny() {
        ProtectedZone zone = zone("z");
        zone.setFlag(WHITELIST_FLAG.id(), new ArrayList<>(List.of(SPELL_ID)));
        when(guardService.shouldBypass(player)).thenReturn(false);
        when(zoneManager.checkZone(any(), any())).thenReturn(Optional.of(zone));
        when(guardService.isFlagAllowedOrUnset(eq(zone), eq(CAST_FLAG), any())).thenReturn(false);

        var handler = makeHandler(() -> true, CAST_FLAG, null, WHITELIST_FLAG, null, Set.of(), player);
        handler.handle(new TestEvent());

        verify(guardService, never()).auditDenied(any(), any(), any(), any(), any());
    }

    @Test
    void handle_whenCastFlagDenied_spellNotInWhitelist_denied() {
        ProtectedZone zone = zone("z");
        zone.setFlag(WHITELIST_FLAG.id(), new ArrayList<>(List.of("other-spell")));
        when(guardService.shouldBypass(player)).thenReturn(false);
        when(zoneManager.checkZone(any(), any())).thenReturn(Optional.of(zone));
        when(guardService.isFlagAllowedOrUnset(eq(zone), eq(CAST_FLAG), any())).thenReturn(false);

        var handler = makeHandler(() -> true, CAST_FLAG, null, WHITELIST_FLAG, null, Set.of(), player);
        handler.handle(new TestEvent());

        verify(guardService).auditDenied(any(), any(), any(), any(), any());
    }

    // ── FR22 : blacklist bloque même quand cast flag est autorisé ─────────────

    @Test
    void handle_whenCastFlagAllowed_spellInBlacklist_deniesCast() {
        ProtectedZone zone = zone("z");
        zone.setFlag(BLACKLIST_FLAG.id(), new ArrayList<>(List.of(SPELL_ID)));
        when(guardService.shouldBypass(player)).thenReturn(false);
        when(zoneManager.checkZone(any(), any())).thenReturn(Optional.of(zone));
        when(guardService.isFlagAllowedOrUnset(eq(zone), eq(CAST_FLAG), any())).thenReturn(true);

        TestCancellableEvent event = new TestCancellableEvent();
        makeHandler(() -> true, CAST_FLAG, null, null, BLACKLIST_FLAG, Set.of(), player).handle(event);

        assertTrue(event.isCanceled(), "Sort en blacklist doit être annulé même si cast flag est true");
    }

    @Test
    void handle_whenCastFlagAllowed_spellNotInBlacklist_doesNotDeny() {
        ProtectedZone zone = zone("z");
        zone.setFlag(BLACKLIST_FLAG.id(), new ArrayList<>(List.of("other-spell")));
        when(guardService.shouldBypass(player)).thenReturn(false);
        when(zoneManager.checkZone(any(), any())).thenReturn(Optional.of(zone));
        when(guardService.isFlagAllowedOrUnset(eq(zone), eq(CAST_FLAG), any())).thenReturn(true);

        var handler = makeHandler(() -> true, CAST_FLAG, null, null, BLACKLIST_FLAG, Set.of(), player);
        handler.handle(new TestEvent());

        verify(guardService, never()).auditDenied(any(), any(), any(), any(), any());
    }

    // ── FR21 : flag de mouvement ──────────────────────────────────────────────

    @Test
    void handle_whenMovementFlagDenied_movementSpellCancelled() {
        ProtectedZone zone = zone("z");
        when(guardService.shouldBypass(player)).thenReturn(false);
        when(zoneManager.checkZone(any(), any())).thenReturn(Optional.of(zone));
        when(guardService.isFlagAllowedOrUnset(eq(zone), eq(MOVEMENT_FLAG), any())).thenReturn(false);

        TestCancellableEvent event = new TestCancellableEvent();
        // SPELL_ID est dans movementSpells → movement check déclenché
        new TestSpellHandler(guardService, () -> true, CAST_FLAG, MOVEMENT_FLAG,
            null, null, Set.of(SPELL_ID), "arcadiaguard.move", () -> "blocked",
            player, SPELL_ID, List.of(SPELL_ID)).handle(event);

        assertTrue(event.isCanceled(), "Sort de mouvement doit être annulé si movement flag interdit");
    }

    @Test
    void handle_whenMovementFlagDenied_nonMovementSpell_notBlockedByMovementCheck() {
        ProtectedZone zone = zone("z");
        when(guardService.shouldBypass(player)).thenReturn(false);
        when(zoneManager.checkZone(any(), any())).thenReturn(Optional.of(zone));
        // Le cast flag est autorisé, pas de blacklist → le sort doit passer
        when(guardService.isFlagAllowedOrUnset(eq(zone), eq(CAST_FLAG), any())).thenReturn(true);

        TestCancellableEvent event = new TestCancellableEvent();
        // SPELL_ID n'est PAS dans movementSpells ("teleport" l'est) → pas de movement check
        new TestSpellHandler(guardService, () -> true, CAST_FLAG, MOVEMENT_FLAG,
            null, null, Set.of("teleport"), "arcadiaguard.move", () -> "blocked",
            player, SPELL_ID, List.of(SPELL_ID)).handle(event);

        assertFalse(event.isCanceled(), "Sort non-movement ne doit pas être bloqué par le movement flag");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private TestSpellHandler makeHandler(Supplier<Boolean> enabled, BooleanFlag castFlag,
            BooleanFlag movementFlag, ListFlag whitelistFlag, ListFlag blacklistFlag,
            Set<String> movementSpells, ServerPlayer returnPlayer) {
        return new TestSpellHandler(guardService, enabled, castFlag, movementFlag,
            whitelistFlag, blacklistFlag, movementSpells, "arcadiaguard.move", () -> "blocked",
            returnPlayer, SPELL_ID, List.of(SPELL_ID));
    }

    private static ProtectedZone zone(String name) {
        return new ProtectedZone(name, "minecraft:overworld",
            -100, 0, -100, 100, 256, 100, new HashSet<>());
    }

    // ── Doubles de test ───────────────────────────────────────────────────────

    private static final class TestSpellHandler extends AbstractSpellHandler {
        private final ServerPlayer returnPlayer;
        private final String       spellId;
        private final List<String> glyphs;

        TestSpellHandler(GuardService gs, Supplier<Boolean> enabled, BooleanFlag castFlag,
                BooleanFlag movementFlag, ListFlag wl, ListFlag bl, Set<String> movSpells,
                String moveMsg, Supplier<String> castMsg,
                ServerPlayer returnPlayer, String spellId, List<String> glyphs) {
            super(gs, enabled, castFlag, movementFlag, wl, bl, movSpells, moveMsg, castMsg);
            this.returnPlayer = returnPlayer;
            this.spellId      = spellId;
            this.glyphs       = glyphs;
        }

        @Override public String eventClassName() { return "test.TestEvent"; }
        @Override protected ServerPlayer extractPlayer(Event event) { return returnPlayer; }
        @Override protected String extractSpellId(Event event, ServerPlayer player) { return spellId; }
        @Override protected List<String> extractSpellGlyphs(Event event, ServerPlayer p) { return glyphs; }
    }

    /** Event simple non-annulable — pour tester les chemins qui ne doivent pas cancel. */
    private static final class TestEvent extends Event {}

    /** Event annulable — pour vérifier que AbstractSpellHandler appelle setCanceled(true). */
    private static final class TestCancellableEvent extends Event implements ICancellableEvent {
        private boolean canceled = false;
        @Override public boolean isCanceled() { return canceled; }
        @Override public void setCanceled(boolean c) { this.canceled = c; }
    }
}
