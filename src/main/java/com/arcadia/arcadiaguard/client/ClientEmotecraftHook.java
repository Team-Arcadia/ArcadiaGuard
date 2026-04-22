package com.arcadia.arcadiaguard.client;

import com.arcadia.arcadiaguard.ArcadiaGuard;
import net.neoforged.fml.ModList;

/**
 * Client-side : enregistre un verifier sur {@code ClientEmoteEvents.EMOTE_VERIFICATION}
 * qui bloque toutes les emotes quand {@link ClientEmoteBlockedState#isBlocked()} est true.
 *
 * <p>Necessaire car le verifier server-side natif d'Emotecraft est conditionne a la
 * config {@code validateEmote} qui peut etre desactivee, alors qu'au client le
 * verifier est toujours appele.
 */
public final class ClientEmotecraftHook {

    private ClientEmotecraftHook() {}

    public static void register() {
        if (!ModList.get().isLoaded("emotecraft")) return;
        try {
            Class<?> eventsClass = Class.forName("io.github.kosmx.emotes.api.events.client.ClientEmoteEvents");
            Object verificationEvent = eventsClass.getField("EMOTE_VERIFICATION").get(null);
            Class<?> eventClass = verificationEvent.getClass();

            Class<?> verifierInterface = Class.forName("io.github.kosmx.emotes.api.events.client.ClientEmoteEvents$EmoteVerifier");
            Object verifier = java.lang.reflect.Proxy.newProxyInstance(
                verifierInterface.getClassLoader(),
                new Class<?>[] { verifierInterface },
                (proxy, method, args) -> {
                    if (!"verify".equals(method.getName())) return null;
                    if (ClientEmoteBlockedState.isBlocked()) return fail();
                    return pass();
                }
            );
            eventClass.getMethod("register", Object.class).invoke(verificationEvent, verifier);
            ArcadiaGuard.LOGGER.info("[ArcadiaGuard] Emotecraft CLIENT verifier registered");
        } catch (Exception e) {
            ArcadiaGuard.LOGGER.error("[ArcadiaGuard] Could not register Emotecraft CLIENT verifier: {}", e.toString(), e);
        }
    }

    private static Object pass() throws Exception {
        Class<?> resultClass = Class.forName("dev.kosmx.playerAnim.core.impl.event.EventResult");
        return resultClass.getField("PASS").get(null);
    }

    private static Object fail() throws Exception {
        Class<?> resultClass = Class.forName("dev.kosmx.playerAnim.core.impl.event.EventResult");
        return resultClass.getField("FAIL").get(null);
    }
}
