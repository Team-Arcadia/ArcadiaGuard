package com.arcadia.arcadiaguard.handler.handlers;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import com.arcadia.arcadiaguard.flag.BuiltinFlags;
import com.arcadia.arcadiaguard.guard.GuardService;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Blocks emote usage (Emotecraft mod) inside protected zones when EMOTE_USE=deny.
 *
 * <p>Registers a verifier on {@code ServerEmoteEvents.EMOTE_VERIFICATION} so emotes
 * are blocked before they start. Does nothing if Emotecraft is not loaded.
 *
 * <p>Call {@link #register(GuardService)} once during mod setup.
 */
public final class EmotecraftHandler {

    private EmotecraftHandler() {}

    public static void register(GuardService guardService) {
        if (!ModList.get().isLoaded("emotecraft")) return;
        try {
            Class<?> eventsClass = Class.forName("io.github.kosmx.emotes.api.events.server.ServerEmoteEvents");
            Object verificationEvent = eventsClass.getField("EMOTE_VERIFICATION").get(null);
            Class<?> eventClass = verificationEvent.getClass();

            // Register via the Event<EmoteVerifier>.register(verifier) method
            // EmoteVerifier is a functional interface: EventResult verify(IEmote emote, UUID userId)
            Class<?> verifierInterface = Class.forName("io.github.kosmx.emotes.api.events.server.ServerEmoteEvents$EmoteVerifier");
            // Utiliser le classloader du verifier (module Emotecraft) pour que Proxy
            // puisse resoudre les types EventResult/KeyframeAnimation correctement
            // dans l'environnement NeoForge avec modules separes.
            Object verifier = java.lang.reflect.Proxy.newProxyInstance(
                verifierInterface.getClassLoader(),
                new Class<?>[] { verifierInterface },
                (proxy, method, args) -> {
                    if (!"verify".equals(method.getName())) return null;
                    UUID userId = (UUID) args[1];
                    var server = ServerLifecycleHooks.getCurrentServer();
                    if (server == null) return pass();
                    ServerPlayer player = server.getPlayerList().getPlayer(userId);
                    if (player == null) return pass();
                    ArcadiaGuard.LOGGER.debug("[ArcadiaGuard] Emote verify for {} bypass={}",
                        player.getGameProfile().getName(), guardService.shouldBypass(player));
                    if (guardService.shouldBypass(player)) return pass();
                    var pos = player.blockPosition();
                    var zoneOpt = guardService.zoneManager().findZoneContaining(player.serverLevel(), pos)
                        .map(z -> (com.arcadia.arcadiaguard.zone.ProtectedZone) z);
                    if (zoneOpt.isPresent() && guardService.isZoneMember(player, zoneOpt.get())) return pass();
                    if (!guardService.isZoneDenying(player.serverLevel(), pos, BuiltinFlags.EMOTE_USE)) return pass();
                    player.displayClientMessage(
                        Component.translatable("arcadiaguard.message.emote_use").withStyle(ChatFormatting.RED), true);
                    return fail();
                }
            );
            // Call register(verifier)
            eventClass.getMethod("register", Object.class).invoke(verificationEvent, verifier);
            ArcadiaGuard.debugInfo("[ArcadiaGuard] Emotecraft verifier registered on ServerEmoteEvents.EMOTE_VERIFICATION");
        } catch (Exception e) {
            ArcadiaGuard.LOGGER.error("[ArcadiaGuard] Could not register Emotecraft integration: {}", e.toString(), e);
        }
    }

    private static Object pass() throws Exception {
        // EventResult vient de player-animation-lib (dependance Emotecraft), pas d'Emotecraft lui-meme.
        Class<?> resultClass = Class.forName("dev.kosmx.playerAnim.core.impl.event.EventResult");
        return resultClass.getField("PASS").get(null);
    }

    private static Object fail() throws Exception {
        Class<?> resultClass = Class.forName("dev.kosmx.playerAnim.core.impl.event.EventResult");
        return resultClass.getField("FAIL").get(null);
    }
}
