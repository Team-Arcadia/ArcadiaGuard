package com.arcadia.arcadiaguard.mixin;

import com.arcadia.arcadiaguard.client.ClientParcoolState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * S-H21 (v1.5) : mixin CLIENT-SIDE sur le tick ParCool.
 * Cancel tous les ticks d'action quand {@link ClientParcoolState#isBlocked()}
 * est true (push par le serveur via ParcoolBlockedPayload).
 */
@Pseudo
@Mixin(targets = "com.alrex.parcool.common.action.ActionProcessor", remap = false)
public abstract class ActionProcessorMixin {

    static {
        com.arcadia.arcadiaguard.ArcadiaGuard.LOGGER.info(
            "[ArcadiaGuard] ActionProcessorMixin class init — mixin will be applied to ParCool ActionProcessor.");
    }

    private static boolean WARNED = false;
    private static boolean LOADED_LOGGED = false;

    @Inject(
        method = "onTick(Lnet/neoforged/neoforge/event/tick/PlayerTickEvent$Post;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void arcadiaguard$maybeCancelParcoolTick(Object event, CallbackInfo ci) {
        if (!LOADED_LOGGED) {
            LOADED_LOGGED = true;
            com.arcadia.arcadiaguard.ArcadiaGuard.LOGGER.info(
                "[ArcadiaGuard] ParCool ActionProcessor mixin loaded (first tick).");
        }
        try {
            if (ClientParcoolState.isBlocked()) {
                ci.cancel();
            }
        } catch (Throwable t) {
            if (!WARNED) {
                WARNED = true;
                com.arcadia.arcadiaguard.ArcadiaGuard.LOGGER.warn(
                    "[ArcadiaGuard] PARCOOL_ACTIONS client mixin inoperant: {}", t.toString());
            }
        }
    }

    /**
     * ParCool a plusieurs points d'entree pour triggerer les actions (KeyInput, ClientTick).
     * On intercepte aussi processAction pour catch les cas ou l'action bypasse onTick.
     */
    @Inject(
        method = "processAction",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private void arcadiaguard$maybeCancelProcessAction(Object player, Object parkour,
            Object entries, boolean isClient, Object action, CallbackInfo ci) {
        try {
            if (ClientParcoolState.isBlocked()) ci.cancel();
        } catch (Throwable t) { /* fallback silent */ }
    }
}
